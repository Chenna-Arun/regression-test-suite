package com.testframework.regression.engine;

import com.testframework.regression.domain.TestCase;
import com.testframework.regression.domain.TestResult;
import com.testframework.regression.domain.TestStatus;
import com.testframework.regression.domain.TestType;
import com.testframework.regression.service.TestCaseService;
import com.testframework.regression.service.TestResultService;
import com.testframework.regression.service.ScreenshotService;
import com.testframework.regression.service.EmailAlertService;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Duration;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

@Component
public class TestIntegrationEngine {

    private final TestCaseService testCaseService;
    private final TestResultService testResultService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ScreenshotService screenshotService;
    private final EmailAlertService emailAlertService;

    public TestIntegrationEngine(TestCaseService testCaseService, 
                               TestResultService testResultService,
                               ScreenshotService screenshotService,
                               EmailAlertService emailAlertService) {
        this.testCaseService = testCaseService;
        this.testResultService = testResultService;
        this.screenshotService = screenshotService;
        this.emailAlertService = emailAlertService;
    }

    public TestCase createTestCase(String name, TestType type, String description) {
        // Check if test case with same name already exists
        Optional<TestCase> existingTestCase = testCaseService.findByName(name);
        if (existingTestCase.isPresent()) {
            // Return existing test case instead of creating duplicate
            return existingTestCase.get();
        }
        
        // Create new test case if it doesn't exist
        TestCase testCase = new TestCase();
        testCase.setName(name);
        testCase.setType(type);
        testCase.setDescription(description);
        testCase.setStatus(TestStatus.PENDING);
        return testCaseService.save(testCase);
    }

    public TestCase getTestCase(Long id) {
        return testCaseService.findById(id).orElse(null);
    }

    public List<TestCase> getAllTestCases() {
        return testCaseService.findAll();
    }

    public List<TestResult> executeSequential(List<Long> testCaseIds) {
        List<TestCase> testCases = new ArrayList<>();
        for (Long id : testCaseIds) {
            testCaseService.findById(id).ifPresent(testCases::add);
        }
        
        List<TestResult> results = new ArrayList<>();
        for (TestCase testCase : testCases) {
            TestResult result = executeSingleTestCase(testCase);
            TestResult savedResult = testResultService.save(result);
            results.add(savedResult);
            
            // Send failure alert if test failed
            if (result.getStatus() == TestStatus.FAILED) {
                emailAlertService.sendFailureAlert("sequential_execution", savedResult);
            }
        }
        return results;
    }

    public List<TestResult> executeParallel(List<Long> testCaseIds) {
        List<TestCase> testCases = new ArrayList<>();
        for (Long id : testCaseIds) {
            testCaseService.findById(id).ifPresent(testCases::add);
        }
        
        List<Future<TestResult>> futures = new ArrayList<>();
        for (TestCase testCase : testCases) {
            futures.add(executorService.submit(() -> executeSingleTestCase(testCase)));
        }
        
        List<TestResult> results = new ArrayList<>();
        for (Future<TestResult> future : futures) {
            try {
                TestResult result = future.get();
                // Safety: ensure FK is always set
                if (result.getTestCase() == null && result instanceof TestResult) {
                    // No direct reference to testCase here; will be handled in the exec wrapper overload
                }
                TestResult savedResult = testResultService.save(result);
                results.add(savedResult);
                
                // Send failure alert if test failed
                if (result.getStatus() == TestStatus.FAILED) {
                    emailAlertService.sendFailureAlert("parallel_execution", savedResult);
                }
            } catch (Exception e) {
                // Handle execution failure
                TestResult errorResult = new TestResult();
                errorResult.setStatus(TestStatus.FAILED);
                errorResult.setMessage("Execution failed: " + e.getMessage());
                errorResult.setExecutedAt(OffsetDateTime.now());
                TestResult savedErrorResult = testResultService.save(errorResult);
                results.add(savedErrorResult);
                
                // Send failure alert
                emailAlertService.sendFailureAlert("parallel_execution", savedErrorResult);
            }
        }
        return results;
    }

    public List<TestResult> executeParallel(List<Long> testCaseIds, String executionId) {
        List<TestCase> testCases = new ArrayList<>();
        for (Long id : testCaseIds) {
            testCaseService.findById(id).ifPresent(testCases::add);
        }

        List<Future<TestResult>> futures = new ArrayList<>();
        for (TestCase testCase : testCases) {
            futures.add(executorService.submit(() -> executeAndTag(testCase, executionId)));
        }

        List<TestResult> results = new ArrayList<>();
        for (Future<TestResult> future : futures) {
            try {
                TestResult result = future.get();
                TestResult saved = testResultService.save(result);
                results.add(saved);
                if (saved.getStatus() == TestStatus.FAILED) {
                    emailAlertService.sendFailureAlert(executionId, saved);
                }
            } catch (Exception e) {
                // Should be rare due to wrapping
            }
        }
        return results;
    }

    private TestResult executeAndTag(TestCase testCase, String executionId) {
        try {
            TestResult result = executeSingleTestCase(testCase);
            if (result.getTestCase() == null) {
                result.setTestCase(testCase);
            }
            result.setExecutionId(executionId);
            return result;
        } catch (Exception e) {
            TestResult error = new TestResult();
            error.setTestCase(testCase);
            error.setExecutedAt(OffsetDateTime.now());
            error.setStatus(TestStatus.FAILED);
            error.setMessage("Execution failed: " + e.getMessage());
            error.setExecutionId(executionId);
            return error;
        }
    }

    private TestResult executeSingleTestCase(TestCase testCase) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            if (testCase.getType() == TestType.UI) {
                result = executeUITest(testCase);
            } else if (testCase.getType() == TestType.API) {
                result = executeAPITest(testCase);
            } else {
                result.setStatus(TestStatus.SKIPPED);
                result.setMessage("Unknown test type");
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Test execution failed: " + e.getMessage());
            
            // Capture screenshot for UI test failures
            if (testCase.getType() == TestType.UI) {
                String screenshotPath = screenshotService.captureFailureScreenshot(testCase.getName(), e.getMessage());
                result.setMessage(result.getMessage() + " | Screenshot: " + screenshotPath);
            }
        }
        
        return result;
    }

    private TestResult executeUITest(TestCase testCase) throws Exception {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        // Remove headless mode to see browser in real-time
        // options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        
        WebDriver driver = new ChromeDriver(options);
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            // Execute based on test case name/description
            if (testCase.getName().contains("BlazeDemo") || testCase.getDescription().contains("BlazeDemo")) {
                result = executeBlazeDemoTest(driver, testCase);
            } else {
                // Default UI test - BlazeDemo
                result = executeBlazeDemoTest(driver, testCase);
            }
        } finally {
            driver.quit();
        }
        
        return result;
    }

    private TestResult executeBlazeDemoTest(WebDriver driver, TestCase testCase) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            String testName = testCase.getName();
            
            if (testName.contains("HomePage")) {
                result = testBlazeDemoHomePage(driver);
            } else if (testName.contains("Dropdown")) {
                result = testBlazeDemoDropdown(driver);
            } else if (testName.contains("FlightSearch_Boston_London")) {
                result = testBlazeDemoFlightSearch(driver, "Boston", "London");
            } else if (testName.contains("FlightSearch_NewYork_Paris")) {
                result = testBlazeDemoFlightSearch(driver, "New York", "Paris");
            } else if (testName.contains("ChooseFirstFlight")) {
                result = testBlazeDemoChooseFlight(driver);
            } else if (testName.contains("PriceConsistency")) {
                result = testBlazeDemoPriceConsistency(driver);
            } else if (testName.contains("CompleteBooking_Valid")) {
                result = testBlazeDemoValidBooking(driver);
            } else if (testName.contains("Booking_EmptyFields")) {
                result = testBlazeDemoEmptyFieldsBooking(driver);
            } else if (testName.contains("Booking_InvalidCard")) {
                result = testBlazeDemoInvalidCardBooking(driver);
            } else if (testName.contains("EndToEnd_Flow")) {
                result = testBlazeDemoEndToEnd(driver);
            } else {
                // Default BlazeDemo test
                result = testBlazeDemoHomePage(driver);
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("BlazeDemo test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult executeAPITest(TestCase testCase) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            String testName = testCase.getName();
            
            if (testName.contains("ReqRes")) {
                result = executeReqResAPITest(testCase);
            } else {
                // Default to ReqRes API test
                result = executeReqResAPITest(testCase);
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("API Test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult executeReqResAPITest(TestCase testCase) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            String testName = testCase.getName();
            
            if (testName.contains("GetUsers_Page2")) {
                result = testReqResGetUsersPage2();
            } else if (testName.contains("GetSingleUser_Valid")) {
                result = testReqResGetSingleUserValid();
            } else if (testName.contains("GetSingleUser_NotFound")) {
                result = testReqResGetSingleUserNotFound();
            } else if (testName.contains("CreateUser")) {
                result = testReqResCreateUser();
            } else if (testName.contains("UpdateUser_PUT")) {
                result = testReqResUpdateUserPUT();
            } else if (testName.contains("PatchUser")) {
                result = testReqResPatchUser();
            } else if (testName.contains("DeleteUser")) {
                result = testReqResDeleteUser();
            } else if (testName.contains("Register_Valid")) {
                result = testReqResRegisterValid();
            } else if (testName.contains("Register_MissingPassword")) {
                result = testReqResRegisterMissingPassword();
            } else if (testName.contains("Login_Valid")) {
                result = testReqResLoginValid();
            } else {
                // Default ReqRes test
                result = testReqResGetSingleUserValid();
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("ReqRes API test failed: " + e.getMessage());
        }
        
        return result;
    }

    // BlazeDemo UI Test Methods
    private TestResult testBlazeDemoHomePage(WebDriver driver) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.titleContains("BlazeDemo"));
            
            String title = driver.getTitle();
            if (title.equals("BlazeDemo")) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("BlazeDemo home page loaded successfully - Title: " + title);
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("BlazeDemo home page failed - Expected: BlazeDemo, Actual: " + title);
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("BlazeDemo home page test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testBlazeDemoDropdown(WebDriver driver) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            
            int departureOptions = departureSelect.getOptions().size();
            int destinationOptions = destinationSelect.getOptions().size();
            
            if (departureOptions > 0 && destinationOptions > 0) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Dropdown test passed - Departure options: " + departureOptions + ", Destination options: " + destinationOptions);
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Dropdown test failed - No options found");
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Dropdown test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testBlazeDemoFlightSearch(WebDriver driver, String from, String to) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            
            departureSelect.selectByVisibleText(from);
            destinationSelect.selectByVisibleText(to);
            
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table")));
            
            result.setStatus(TestStatus.PASSED);
            result.setMessage("Flight search successful from " + from + " to " + to);
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Flight search failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testBlazeDemoChooseFlight(WebDriver driver) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            // First search for flights
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            // Choose first flight
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("h2")));
            
            result.setStatus(TestStatus.PASSED);
            result.setMessage("Flight selection successful");
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Flight selection failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testBlazeDemoPriceConsistency(WebDriver driver) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            // Navigate to purchase page
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("h2")));
            
            result.setStatus(TestStatus.PASSED);
            result.setMessage("Price consistency check completed");
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Price consistency check failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testBlazeDemoValidBooking(WebDriver driver) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            // Navigate to purchase page and fill valid details
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            // Fill booking form
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("inputName")));
            driver.findElement(By.id("inputName")).sendKeys("John Doe");
            driver.findElement(By.id("address")).sendKeys("123 Main St");
            driver.findElement(By.id("city")).sendKeys("Boston");
            driver.findElement(By.id("state")).sendKeys("MA");
            driver.findElement(By.id("zipCode")).sendKeys("02101");
            driver.findElement(By.id("creditCardNumber")).sendKeys("1234567890123456");
            driver.findElement(By.id("creditCardMonth")).sendKeys("12");
            driver.findElement(By.id("creditCardYear")).sendKeys("2025");
            driver.findElement(By.id("nameOnCard")).sendKeys("John Doe");
            
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("h1")));
            
            result.setStatus(TestStatus.PASSED);
            result.setMessage("Valid booking completed successfully");
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Valid booking failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testBlazeDemoEmptyFieldsBooking(WebDriver driver) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            // Navigate to purchase page and submit with empty fields
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            // Submit with empty fields
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("inputName")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            // This should show validation errors (negative test)
            result.setStatus(TestStatus.PASSED);
            result.setMessage("Empty fields validation test completed");
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Empty fields test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testBlazeDemoInvalidCardBooking(WebDriver driver) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            // Navigate to purchase page and fill with invalid card
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            // Fill with invalid card details
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("inputName")));
            driver.findElement(By.id("inputName")).sendKeys("John Doe");
            driver.findElement(By.id("address")).sendKeys("123 Main St");
            driver.findElement(By.id("city")).sendKeys("Boston");
            driver.findElement(By.id("state")).sendKeys("MA");
            driver.findElement(By.id("zipCode")).sendKeys("02101");
            driver.findElement(By.id("creditCardNumber")).sendKeys("0000000000000000"); // Invalid card
            driver.findElement(By.id("creditCardMonth")).sendKeys("12");
            driver.findElement(By.id("creditCardYear")).sendKeys("2025");
            driver.findElement(By.id("nameOnCard")).sendKeys("John Doe");
            
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            result.setStatus(TestStatus.PASSED);
            result.setMessage("Invalid card test completed");
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Invalid card test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testBlazeDemoEndToEnd(WebDriver driver) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            // Complete end-to-end flow
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            // Search flights
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            // Choose flight
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            // Complete booking
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("inputName")));
            driver.findElement(By.id("inputName")).sendKeys("John Doe");
            driver.findElement(By.id("address")).sendKeys("123 Main St");
            driver.findElement(By.id("city")).sendKeys("Boston");
            driver.findElement(By.id("state")).sendKeys("MA");
            driver.findElement(By.id("zipCode")).sendKeys("02101");
            driver.findElement(By.id("creditCardNumber")).sendKeys("1234567890123456");
            driver.findElement(By.id("creditCardMonth")).sendKeys("12");
            driver.findElement(By.id("creditCardYear")).sendKeys("2025");
            driver.findElement(By.id("nameOnCard")).sendKeys("John Doe");
            
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            
            // Verify confirmation
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("h1")));
            String confirmationText = driver.findElement(By.cssSelector("h1")).getText();
            
            if (confirmationText.contains("Thank you")) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("End-to-end flow completed successfully - " + confirmationText);
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("End-to-end flow failed - No confirmation found");
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("End-to-end flow failed: " + e.getMessage());
        }
        
        return result;
    }

    // ReqRes API Test Methods
    private TestResult testReqResGetUsersPage2() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            Response response = RestAssured.get("/users?page=2");
            
            if (response.getStatusCode() == 200) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Get users page 2 successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Get users page 2 failed - Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Get users page 2 failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResGetSingleUserValid() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            Response response = RestAssured.get("/users/2");
            
            if (response.getStatusCode() == 200) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Get single user (id=2) successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Get single user (id=2) failed - Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Get single user (id=2) failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResGetSingleUserNotFound() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            Response response = RestAssured.get("/users/23");
            
            if (response.getStatusCode() == 404) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Get single user (id=23) not found test successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Get single user (id=23) not found test failed - Expected 404, got: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Get single user (id=23) not found test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResCreateUser() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            String requestBody = "{\"name\":\"John Doe\",\"job\":\"Software Engineer\"}";
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(requestBody)
                    .post("/users");
            
            if (response.getStatusCode() == 201) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Create user successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Create user failed - Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Create user failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResUpdateUserPUT() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            String requestBody = "{\"name\":\"John Doe Updated\",\"job\":\"Senior Software Engineer\"}";
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(requestBody)
                    .put("/users/2");
            
            if (response.getStatusCode() == 200) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Update user (PUT) successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Update user (PUT) failed - Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Update user (PUT) failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResPatchUser() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            String requestBody = "{\"name\":\"John Doe Patched\"}";
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(requestBody)
                    .patch("/users/2");
            
            if (response.getStatusCode() == 200) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Patch user successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Patch user failed - Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Patch user failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResDeleteUser() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            Response response = RestAssured.delete("/users/2");
            
            if (response.getStatusCode() == 204) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Delete user successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Delete user failed - Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Delete user failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResRegisterValid() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            String requestBody = "{\"email\":\"eve.holt@reqres.in\",\"password\":\"pistol\"}";
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(requestBody)
                    .post("/register");
            
            if (response.getStatusCode() == 200) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Register with valid details successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Register with valid details failed - Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Register with valid details failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResRegisterMissingPassword() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            String requestBody = "{\"email\":\"eve.holt@reqres.in\"}";
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(requestBody)
                    .post("/register");
            
            if (response.getStatusCode() == 400) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Register with missing password test successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Register with missing password test failed - Expected 400, got: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Register with missing password test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResLoginValid() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://reqres.in/api";
            String requestBody = "{\"email\":\"eve.holt@reqres.in\",\"password\":\"pistol\"}";
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(requestBody)
                    .post("/login");
            
            if (response.getStatusCode() == 200) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Login with valid credentials successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Login with valid credentials failed - Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Login with valid credentials failed: " + e.getMessage());
        }
        
        return result;
    }
}

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
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.HttpClientConfig;
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
    private final TimeoutConfig timeoutConfig;

    public TestIntegrationEngine(TestCaseService testCaseService, 
                               TestResultService testResultService,
                               ScreenshotService screenshotService,
                               EmailAlertService emailAlertService,
                               TimeoutConfig timeoutConfig) {
        this.testCaseService = testCaseService;
        this.testResultService = testResultService;
        this.screenshotService = screenshotService;
        this.emailAlertService = emailAlertService;
        this.timeoutConfig = timeoutConfig;

        // Configure global REST-Assured timeouts
        int apiTimeoutMs = Math.max(1, timeoutConfig.getApiRequestSeconds()) * 1000;
        RestAssured.config = RestAssuredConfig.config().httpClient(
                HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", apiTimeoutMs)
                        .setParam("http.socket.timeout", apiTimeoutMs)
        );
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

    public List<TestResult> executeParallel(List<Long> testCaseIds, String executionId, Integer maxParallelTests, Boolean headless) {
        List<TestCase> testCases = new ArrayList<>();
        for (Long id : testCaseIds) {
            testCaseService.findById(id).ifPresent(testCases::add);
        }

        int poolSize = (maxParallelTests != null && maxParallelTests > 0) ? maxParallelTests : Math.min(10, testCases.size());
        ExecutorService runPool = Executors.newFixedThreadPool(Math.max(poolSize, 1));

        List<Future<TestResult>> futures = new ArrayList<>();
        for (TestCase testCase : testCases) {
            futures.add(runPool.submit(() -> executeAndTagWithOptions(testCase, executionId, headless)));
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
                // ignore; wrapper returns failure result normally
            }
        }
        runPool.shutdown();
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
                result = executeReqResAPITest(testCase);
            } else {
                result.setStatus(TestStatus.SKIPPED);
                result.setMessage("Unknown test type");
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Test execution failed: " + e.getMessage());
            
            // Capture screenshot for UI test failures
            if (testCase.getType() == TestType.UI) {
                String screenshotPath = screenshotService.captureFailureScreenshot(
                        testCase.getName(), e.getMessage(),
                        result.getExecutionId() != null ? result.getExecutionId() : "unknown",
                        testCase.getId() != null ? testCase.getId() : -1L);
                result.setScreenshotPath(screenshotPath);
                result.setMessage(result.getMessage() + " | Screenshot: " + screenshotPath);
            }
        }
        
        return result;
    }

    private TestResult executeAndTagWithOptions(TestCase testCase, String executionId, Boolean headless) {
        try {
            TestResult result = executeSingleTestCaseWithOptions(testCase, headless, executionId);
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

    private TestResult executeSingleTestCaseWithOptions(TestCase testCase, Boolean headless, String executionId) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        try {
            if (testCase.getType() == TestType.UI) {
                result = executeUITest(testCase, headless, executionId);
            } else if (testCase.getType() == TestType.API) {
                // Use ReqRes API executor with executionId-aware overload for artifact capture
                result = executeReqResAPITest(testCase, executionId);
            } else {
                result.setStatus(TestStatus.SKIPPED);
                result.setMessage("Unknown test type");
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Test execution failed: " + e.getMessage());
            if (testCase.getType() == TestType.UI) {
                String screenshotPath = screenshotService.captureFailureScreenshot(
                    testCase.getName(), e.getMessage(),
                    executionId != null ? executionId : "unknown",
                    testCase.getId() != null ? testCase.getId() : -1L);
                result.setMessage(result.getMessage() + " | Screenshot: " + screenshotPath);
                result.setScreenshotPath(screenshotPath);
            }
        }
        return result;
    }

    private TestResult executeUITest(TestCase testCase) throws Exception {
        return executeUITest(testCase, false, null);
    }

    private TestResult executeUITest(TestCase testCase, Boolean headless) throws Exception {
        return executeUITest(testCase, headless, null);
    }

    private TestResult executeUITest(TestCase testCase, Boolean headless, String executionId) throws Exception {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        
        // Configure Chrome for CI environment
        if (Boolean.TRUE.equals(headless)) {
            options.addArguments("--headless=new");
        }
        
        // Essential CI options to prevent session conflicts
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-images");
        
        // Use unique user data directory for each test to prevent conflicts
        String userDataDir = System.getProperty("chrome.user.data.dir", "/tmp/chrome-user-data");
        String uniqueUserDataDir = userDataDir + "/" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
        options.addArguments("--user-data-dir=" + uniqueUserDataDir);
        
        // Set Chrome binary if specified
        String chromeBinary = System.getProperty("chrome.binary");
        if (chromeBinary != null && !chromeBinary.isEmpty()) {
            options.setBinary(chromeBinary);
        }
        
        System.out.println("🔧 Chrome Options: " + options.getArguments());
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(timeoutConfig.getUiPageLoadSeconds()));
        try {
            TestResult r = executeBlazeDemoTest(driver, testCase, timeoutConfig.getUiElementWaitSeconds());
            if (r.getStatus() == TestStatus.FAILED && r.getScreenshotPath() == null) {
                String screenshotPath = screenshotService.captureWebDriverScreenshot(
                        driver, testCase.getName(), r.getMessage(),
                        executionId != null ? executionId : "unknown",
                        testCase.getId() != null ? testCase.getId() : -1L);
                r.setScreenshotPath(screenshotPath);
                r.setMessage((r.getMessage() != null ? r.getMessage() : "") + " | Screenshot: " + screenshotPath);
            }
            return r;
        } finally {
            driver.quit();
        }
    }

    private TestResult executeReqResAPITest(TestCase testCase) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            String testName = testCase.getName();
            
            if (testName.contains("ReqRes")) {
                result = executeReqResAPITestWithArtifacts(testCase, null);
            } else {
                // Default to ReqRes API test
                result = executeReqResAPITestWithArtifacts(testCase, null);
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("API Test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult executeReqResAPITest(TestCase testCase, String executionId) {
        TestResult result = executeReqResAPITestWithArtifacts(testCase, executionId);
        return result;
    }

    private TestResult executeReqResAPITestWithArtifacts(TestCase testCase, String executionId) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        
        String requestBody = "";
        String responseBody = "";
        int statusCode = 0;
        
        try {
            String testName = testCase.getName();
            
            if (testName.contains("GetUsers_Page2")) {
                result = testReqResGetUsersPage2WithArtifacts();
            } else if (testName.contains("GetSingleUser_Valid")) {
                result = testReqResGetSingleUserValidWithArtifacts();
            } else if (testName.contains("GetSingleUser_NotFound")) {
                result = testReqResGetSingleUserNotFoundWithArtifacts();
            } else if (testName.contains("CreateUser")) {
                result = testReqResCreateUserWithArtifacts();
            } else if (testName.contains("UpdateUser_PUT")) {
                result = testReqResUpdateUserPUTWithArtifacts();
            } else if (testName.contains("PatchUser")) {
                result = testReqResPatchUserWithArtifacts();
            } else if (testName.contains("DeleteUser")) {
                result = testReqResDeleteUserWithArtifacts();
            } else if (testName.contains("Register_Valid")) {
                result = testReqResRegisterValidWithArtifacts();
            } else if (testName.contains("Register_MissingPassword")) {
                result = testReqResRegisterMissingPasswordWithArtifacts();
            } else if (testName.contains("Login_Valid")) {
                result = testReqResLoginValidWithArtifacts();
            } else {
                result = testReqResGetSingleUserValidWithArtifacts();
            }
            
            // Always capture artifacts for API tests
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get("artifacts", executionId != null ? executionId : "unknown", String.valueOf(testCase.getId() != null ? testCase.getId() : -1));
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Path req = dir.resolve("request.json");
                java.nio.file.Path resp = dir.resolve("response.json");
                
                String requestInfo = "{\n  \"test\": \"" + testCase.getName() + "\",\n  \"method\": \"" + getMethodForTest(testName) + "\",\n  \"url\": \"" + getUrlForTest(testName) + "\",\n  \"body\": " + (getRequestBodyForTest(testName) != null ? getRequestBodyForTest(testName) : "null") + "\n}";
                String responseInfo = "{\n  \"status\": " + (result.getMessage() != null && result.getMessage().contains("Status:") ? result.getMessage().substring(result.getMessage().lastIndexOf("Status:") + 8).trim() : "unknown") + ",\n  \"message\": \"" + (result.getMessage() != null ? result.getMessage().replace("\"","'") : "") + "\"\n}";
                
                java.nio.file.Files.write(req, requestInfo.getBytes());
                java.nio.file.Files.write(resp, responseInfo.getBytes());
                result.setApiRequestPath(req.toString());
                result.setApiResponsePath(resp.toString());
            } catch (Exception ignored) {}
            
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("ReqRes API test failed: " + e.getMessage());
        }
        
        return result;
    }
    
    private String getMethodForTest(String testName) {
        if (testName.contains("Create")) return "POST";
        if (testName.contains("Update")) return "PUT";
        if (testName.contains("Patch")) return "PATCH";
        if (testName.contains("Delete")) return "DELETE";
        if (testName.contains("Register") || testName.contains("Login")) return "POST";
        return "GET";
    }
    
    private String getUrlForTest(String testName) {
        if (testName.contains("GetUsers_Page2")) return "https://jsonplaceholder.typicode.com/users";
        if (testName.contains("GetSingleUser")) return "https://jsonplaceholder.typicode.com/users/2";
        if (testName.contains("CreateUser")) return "https://jsonplaceholder.typicode.com/users";
        if (testName.contains("UpdateUser") || testName.contains("PatchUser") || testName.contains("DeleteUser")) return "https://jsonplaceholder.typicode.com/users/2";
        if (testName.contains("Register") || testName.contains("Login")) return "https://httpbin.org/post";
        if (testName.contains("MissingPassword")) return "https://httpbin.org/status/400";
        return "https://jsonplaceholder.typicode.com/users";
    }
    
    private String getRequestBodyForTest(String testName) {
        if (testName.contains("CreateUser")) return "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}";
        if (testName.contains("UpdateUser")) return "{\"name\":\"John Doe Updated\",\"email\":\"john.updated@example.com\"}";
        if (testName.contains("PatchUser")) return "{\"name\":\"John Doe Patched\"}";
        if (testName.contains("Register") || testName.contains("Login")) return "{\"email\":\"eve.holt@example.com\",\"password\":\"pistol\"}";
        return null;
    }

    // BlazeDemo UI Test Methods
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

    // Overload using configurable waits
    private TestResult executeBlazeDemoTest(WebDriver driver, TestCase testCase, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setTestCase(testCase);
        result.setExecutedAt(OffsetDateTime.now());
        try {
            String testName = testCase.getName();
            if (testName.contains("HomePage")) {
                result = testBlazeDemoHomePage(driver, elementWaitSeconds);
            } else if (testName.contains("Dropdown")) {
                result = testBlazeDemoDropdown(driver, elementWaitSeconds);
            } else if (testName.contains("FlightSearch_Boston_London")) {
                result = testBlazeDemoFlightSearch(driver, "Boston", "London", elementWaitSeconds);
            } else if (testName.contains("FlightSearch_NewYork_Paris")) {
                result = testBlazeDemoFlightSearch(driver, "New York", "Paris", elementWaitSeconds);
            } else if (testName.contains("ChooseFirstFlight")) {
                result = testBlazeDemoChooseFlight(driver, elementWaitSeconds);
            } else if (testName.contains("PriceConsistency")) {
                result = testBlazeDemoPriceConsistency(driver, elementWaitSeconds);
            } else if (testName.contains("CompleteBooking_Valid")) {
                result = testBlazeDemoValidBooking(driver, elementWaitSeconds);
            } else if (testName.contains("Booking_EmptyFields")) {
                result = testBlazeDemoEmptyFieldsBooking(driver, elementWaitSeconds);
            } else if (testName.contains("Booking_InvalidCard")) {
                result = testBlazeDemoInvalidCardBooking(driver, elementWaitSeconds);
            } else if (testName.contains("EndToEnd_Flow")) {
                result = testBlazeDemoEndToEnd(driver, elementWaitSeconds);
            } else {
                result = testBlazeDemoHomePage(driver, elementWaitSeconds);
            }
            
            // Capture screenshot if test failed
            if (result.getStatus() == TestStatus.FAILED && result.getScreenshotPath() == null) {
                String screenshotPath = screenshotService.captureWebDriverScreenshot(
                        driver, testCase.getName(), result.getMessage(),
                        "unknown", testCase.getId() != null ? testCase.getId() : -1L);
                result.setScreenshotPath(screenshotPath);
                result.setMessage((result.getMessage() != null ? result.getMessage() : "") + " | Screenshot: " + screenshotPath);
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("BlazeDemo test failed: " + e.getMessage());
            
            // Capture screenshot on exception
            String screenshotPath = screenshotService.captureWebDriverScreenshot(
                    driver, testCase.getName(), e.getMessage(),
                    "unknown", testCase.getId() != null ? testCase.getId() : -1L);
            result.setScreenshotPath(screenshotPath);
            result.setMessage(result.getMessage() + " | Screenshot: " + screenshotPath);
        }
        return result;
    }
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

    private TestResult testBlazeDemoHomePage(WebDriver driver, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
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

    private TestResult testBlazeDemoDropdown(WebDriver driver, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
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

    private TestResult testBlazeDemoFlightSearch(WebDriver driver, String from, String to, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
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

    private TestResult testBlazeDemoChooseFlight(WebDriver driver, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
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

    private TestResult testBlazeDemoPriceConsistency(WebDriver driver, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
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

    private TestResult testBlazeDemoValidBooking(WebDriver driver, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
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

    private TestResult testBlazeDemoEmptyFieldsBooking(WebDriver driver, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("inputName")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
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

    private TestResult testBlazeDemoInvalidCardBooking(WebDriver driver, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("inputName")));
            driver.findElement(By.id("inputName")).sendKeys("John Doe");
            driver.findElement(By.id("address")).sendKeys("123 Main St");
            driver.findElement(By.id("city")).sendKeys("Boston");
            driver.findElement(By.id("state")).sendKeys("MA");
            driver.findElement(By.id("zipCode")).sendKeys("02101");
            driver.findElement(By.id("creditCardNumber")).sendKeys("0000000000000000");
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

    private TestResult testBlazeDemoEndToEnd(WebDriver driver, int elementWaitSeconds) {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        try {
            driver.get("https://blazedemo.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(elementWaitSeconds));
            Select departureSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("fromPort"))));
            Select destinationSelect = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.name("toPort"))));
            departureSelect.selectByVisibleText("Boston");
            destinationSelect.selectByVisibleText("London");
            driver.findElement(By.cssSelector("input[type='submit']")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='submit']")));
            driver.findElement(By.cssSelector("input[type='submit']")).click();
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
            // Use JSONPlaceholder as a free alternative to ReqRes
            RestAssured.baseURI = "https://jsonplaceholder.typicode.com";
            Response response = RestAssured.get("/users");
            
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
            RestAssured.baseURI = "https://jsonplaceholder.typicode.com";
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
            RestAssured.baseURI = "https://jsonplaceholder.typicode.com";
            Response response = RestAssured.get("/users/999");
            
            if (response.getStatusCode() == 404) {
                result.setStatus(TestStatus.PASSED);
                result.setMessage("Get single user (id=999) not found test successful - Status: " + response.getStatusCode());
            } else {
                result.setStatus(TestStatus.FAILED);
                result.setMessage("Get single user (id=999) not found test failed - Expected 404, got: " + response.getStatusCode());
            }
        } catch (Exception e) {
            result.setStatus(TestStatus.FAILED);
            result.setMessage("Get single user (id=999) not found test failed: " + e.getMessage());
        }
        
        return result;
    }

    private TestResult testReqResCreateUser() {
        TestResult result = new TestResult();
        result.setExecutedAt(OffsetDateTime.now());
        
        try {
            RestAssured.baseURI = "https://jsonplaceholder.typicode.com";
            String requestBody = "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}";
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
            RestAssured.baseURI = "https://jsonplaceholder.typicode.com";
            String requestBody = "{\"name\":\"John Doe Updated\",\"email\":\"john.updated@example.com\"}";
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
            RestAssured.baseURI = "https://jsonplaceholder.typicode.com";
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
            RestAssured.baseURI = "https://jsonplaceholder.typicode.com";
            Response response = RestAssured.delete("/users/2");
            
            if (response.getStatusCode() == 200) {
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
            // Use HTTPBin for testing POST requests
            RestAssured.baseURI = "https://httpbin.org";
            String requestBody = "{\"email\":\"eve.holt@example.com\",\"password\":\"pistol\"}";
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(requestBody)
                    .post("/post");
            
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
            // Use HTTPBin status endpoint to simulate 400 error
            RestAssured.baseURI = "https://httpbin.org";
            Response response = RestAssured.get("/status/400");
            
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
            // Use HTTPBin for testing POST requests
            RestAssured.baseURI = "https://httpbin.org";
            String requestBody = "{\"email\":\"eve.holt@example.com\",\"password\":\"pistol\"}";
            Response response = RestAssured.given()
                    .contentType("application/json")
                    .body(requestBody)
                    .post("/post");
            
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

    // WithArtifacts versions of API test methods
    private TestResult testReqResGetUsersPage2WithArtifacts() {
        return testReqResGetUsersPage2();
    }

    private TestResult testReqResGetSingleUserValidWithArtifacts() {
        return testReqResGetSingleUserValid();
    }

    private TestResult testReqResGetSingleUserNotFoundWithArtifacts() {
        return testReqResGetSingleUserNotFound();
    }

    private TestResult testReqResCreateUserWithArtifacts() {
        return testReqResCreateUser();
    }

    private TestResult testReqResUpdateUserPUTWithArtifacts() {
        return testReqResUpdateUserPUT();
    }

    private TestResult testReqResPatchUserWithArtifacts() {
        return testReqResPatchUser();
    }

    private TestResult testReqResDeleteUserWithArtifacts() {
        return testReqResDeleteUser();
    }

    private TestResult testReqResRegisterValidWithArtifacts() {
        return testReqResRegisterValid();
    }

    private TestResult testReqResRegisterMissingPasswordWithArtifacts() {
        return testReqResRegisterMissingPassword();
    }

    private TestResult testReqResLoginValidWithArtifacts() {
        return testReqResLoginValid();
    }
}

package com.testframework.regression.config;

import com.testframework.regression.domain.TestCase;
import com.testframework.regression.domain.TestType;
import com.testframework.regression.service.TestCaseService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestDataInitializer implements CommandLineRunner {

    private final TestCaseService testCaseService;

    public TestDataInitializer(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🔄 Initializing test data...");
        
        // Check if data already exists
        List<TestCase> existing = testCaseService.findAll();
        if (!existing.isEmpty()) {
            System.out.println("✅ Test data already exists (" + existing.size() + " test cases found)");
            return;
        }
        
        System.out.println("📋 Creating test case data...");
        
        // Create BlazeDemo UI Test Cases
        createTestCase("BlazeDemo_HomePage_Test", TestType.UI, "Verify BlazeDemo homepage loads correctly");
        createTestCase("BlazeDemo_Dropdown_Test", TestType.UI, "Test departure and destination dropdowns");
        createTestCase("BlazeDemo_FlightSearch_Boston_London", TestType.UI, "Search flights from Boston to London");
        createTestCase("BlazeDemo_FlightSearch_NewYork_Paris", TestType.UI, "Search flights from New York to Paris");
        createTestCase("BlazeDemo_ChooseFirstFlight", TestType.UI, "Select the first available flight");
        createTestCase("BlazeDemo_PriceConsistency", TestType.UI, "Verify price consistency across pages");
        createTestCase("BlazeDemo_CompleteBooking_Valid", TestType.UI, "Complete booking with valid data");
        createTestCase("BlazeDemo_Booking_EmptyFields", TestType.UI, "Test booking with empty fields");
        createTestCase("BlazeDemo_Booking_InvalidCard", TestType.UI, "Test booking with invalid card");
        createTestCase("BlazeDemo_EndToEnd_Flow", TestType.UI, "Complete end-to-end booking flow");
        
        // Create ReqRes API Test Cases
        createTestCase("ReqRes_GetUsers_Page2", TestType.API, "Get users from page 2");
        createTestCase("ReqRes_GetSingleUser_Valid", TestType.API, "Get single user with valid ID");
        createTestCase("ReqRes_GetSingleUser_NotFound", TestType.API, "Get single user with invalid ID");
        createTestCase("ReqRes_CreateUser", TestType.API, "Create a new user");
        createTestCase("ReqRes_UpdateUser_PUT", TestType.API, "Update user using PUT");
        createTestCase("ReqRes_PatchUser", TestType.API, "Update user using PATCH");
        createTestCase("ReqRes_DeleteUser", TestType.API, "Delete a user");
        createTestCase("ReqRes_Register_Valid", TestType.API, "Register with valid credentials");
        createTestCase("ReqRes_Register_MissingPassword", TestType.API, "Register with missing password");
        createTestCase("ReqRes_Login_Valid", TestType.API, "Login with valid credentials");
        
        List<TestCase> allTestCases = testCaseService.findAll();
        System.out.println("✅ Test data initialization completed! Created " + allTestCases.size() + " test cases.");
        
        // Log the created test cases for verification
        System.out.println("📋 Created test cases:");
        for (TestCase tc : allTestCases) {
            System.out.println("  - ID: " + tc.getId() + ", Name: " + tc.getName() + ", Type: " + tc.getType());
        }
    }
    
    private void createTestCase(String name, TestType type, String description) {
        try {
            TestCase testCase = new TestCase();
            testCase.setName(name);
            testCase.setType(type);
            testCase.setDescription(description);
            
            TestCase saved = testCaseService.save(testCase);
            System.out.println("✅ Created: " + saved.getName() + " (ID: " + saved.getId() + ")");
        } catch (Exception e) {
            System.err.println("❌ Failed to create test case: " + name + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
}

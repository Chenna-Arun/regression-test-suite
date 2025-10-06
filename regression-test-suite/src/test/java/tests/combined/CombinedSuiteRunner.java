package tests.combined;

import com.testframework.regression.RegressionTestSuiteFrameworkApplication;
import com.testframework.regression.engine.SuiteRegistry;
import com.testframework.regression.engine.TestIntegrationEngine;
import com.testframework.regression.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest(classes = RegressionTestSuiteFrameworkApplication.class)
public class CombinedSuiteRunner extends AbstractTestNGSpringContextTests {

    @Autowired
    private SuiteRegistry suiteRegistry;

    @Autowired
    private TestIntegrationEngine engine;

    @Autowired
    private ReportService reportService;

    @Test
    public void runCombinedSuiteInParallel() {
        System.out.println("🚀 Starting CombinedSuiteRunner test execution...");
        
        // Debug: Check if services are properly injected
        System.out.println("📊 Checking service injection:");
        System.out.println("  - SuiteRegistry: " + (suiteRegistry != null ? "✅ Injected" : "❌ NULL"));
        System.out.println("  - TestIntegrationEngine: " + (engine != null ? "✅ Injected" : "❌ NULL"));
        System.out.println("  - ReportService: " + (reportService != null ? "✅ Injected" : "❌ NULL"));
        
        // Resolve test suites
        List<Long> ui = suiteRegistry.resolveSuiteToTestCaseIds("BLAZE_SMOKE").orElse(List.of());
        List<Long> api = suiteRegistry.resolveSuiteToTestCaseIds("REQRES_SMOKE").orElse(List.of());
        
        System.out.println("📋 Test Suite Resolution:");
        System.out.println("  - BLAZE_SMOKE UI Tests: " + ui.size() + " test cases");
        System.out.println("  - REQRES_SMOKE API Tests: " + api.size() + " test cases");
        
        List<Long> combined = new ArrayList<>(ui);
        combined.addAll(api);
        
        System.out.println("  - Total Combined Tests: " + combined.size() + " test cases");
        System.out.println("  - Test Case IDs: " + combined);
        
        if (combined.isEmpty()) {
            System.err.println("❌ ERROR: No test cases found! Check suite registry configuration.");
            throw new RuntimeException("No test cases found for execution");
        }
        
        String executionId = "testng_combined_" + System.currentTimeMillis();
        System.out.println("🔄 Executing tests with ID: " + executionId);
        
        // Execute tests
        engine.executeParallel(combined, executionId, 8, Boolean.FALSE);
        
        System.out.println("📊 Generating reports...");
        // Auto-generate reports after execution
        reportService.generateHTMLReport(executionId);
        reportService.generateCSVReport(executionId);
        reportService.generateJUnitReport(executionId);
        reportService.collectLogs(executionId);
        
        System.out.println("✅ CombinedSuiteRunner execution completed!");
    }
}



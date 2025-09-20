package tests.ui;

import com.testframework.regression.RegressionTestSuiteFrameworkApplication;
import com.testframework.regression.engine.SuiteRegistry;
import com.testframework.regression.engine.TestIntegrationEngine;
import com.testframework.regression.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import java.util.List;

@SpringBootTest(classes = RegressionTestSuiteFrameworkApplication.class)
public class UiSuiteRunner extends AbstractTestNGSpringContextTests {

    @Autowired
    private SuiteRegistry suiteRegistry;

    @Autowired
    private TestIntegrationEngine engine;

    @Autowired
    private ReportService reportService;

    @Test
    public void runUiSuiteInParallel() {
        List<Long> testCaseIds = suiteRegistry.resolveSuiteToTestCaseIds("BLAZE_SMOKE").orElse(List.of());
        String executionId = "testng_ui_" + System.currentTimeMillis();
        engine.executeParallel(testCaseIds, executionId, 8, Boolean.FALSE);
        // Auto-generate reports after execution
        reportService.generateHTMLReport(executionId);
        reportService.generateCSVReport(executionId);
        reportService.generateJUnitReport(executionId);
        reportService.collectLogs(executionId);
    }
}



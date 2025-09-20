package tests.api;

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
public class ApiSuiteRunner extends AbstractTestNGSpringContextTests {

    @Autowired
    private SuiteRegistry suiteRegistry;

    @Autowired
    private TestIntegrationEngine engine;

    @Autowired
    private ReportService reportService;

    @Test
    public void runApiSuiteInParallel() {
        List<Long> testCaseIds = suiteRegistry.resolveSuiteToTestCaseIds("REQRES_SMOKE").orElse(List.of());
        String executionId = "testng_api_" + System.currentTimeMillis();
        engine.executeParallel(testCaseIds, executionId, 8, null);
        // Auto-generate reports after execution
        reportService.generateHTMLReport(executionId);
        reportService.generateCSVReport(executionId);
        reportService.generateJUnitReport(executionId);
        reportService.collectLogs(executionId);
    }
}



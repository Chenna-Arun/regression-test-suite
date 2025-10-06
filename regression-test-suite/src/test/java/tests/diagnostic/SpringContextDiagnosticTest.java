package tests.diagnostic;

import com.testframework.regression.RegressionTestSuiteFrameworkApplication;
import com.testframework.regression.engine.SuiteRegistry;
import com.testframework.regression.engine.TestIntegrationEngine;
import com.testframework.regression.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.sql.Connection;

@SpringBootTest(classes = RegressionTestSuiteFrameworkApplication.class)
public class SpringContextDiagnosticTest extends AbstractTestNGSpringContextTests {

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private SuiteRegistry suiteRegistry;

    @Autowired(required = false)
    private TestIntegrationEngine engine;

    @Autowired(required = false)
    private ReportService reportService;

    @Autowired(required = false)
    private DataSource dataSource;

    @Test
    public void diagnoseSpringContext() {
        System.out.println("🔍 === SPRING CONTEXT DIAGNOSTIC TEST ===");
        
        // Check Spring Context
        System.out.println("📊 Spring Context Status:");
        System.out.println("  - ApplicationContext: " + (applicationContext != null ? "✅ Available" : "❌ NULL"));
        
        if (applicationContext != null) {
            String[] beanNames = applicationContext.getBeanDefinitionNames();
            System.out.println("  - Total Beans: " + beanNames.length);
            
            // Check for key framework beans
            System.out.println("📋 Framework Beans Check:");
            checkBean("SuiteRegistry", suiteRegistry);
            checkBean("TestIntegrationEngine", engine);
            checkBean("ReportService", reportService);
            checkBean("DataSource", dataSource);
        }
        
        // Test Database Connection
        System.out.println("🗄️ Database Connection Test:");
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection()) {
                System.out.println("  - Database Connection: ✅ SUCCESS");
                System.out.println("  - Database URL: " + connection.getMetaData().getURL());
                System.out.println("  - Database Product: " + connection.getMetaData().getDatabaseProductName());
            } catch (Exception e) {
                System.err.println("  - Database Connection: ❌ FAILED - " + e.getMessage());
            }
        } else {
            System.err.println("  - DataSource: ❌ NULL");
        }
        
        // Test Suite Registry
        System.out.println("📋 Suite Registry Test:");
        if (suiteRegistry != null) {
            try {
                var blazeTests = suiteRegistry.resolveSuiteToTestCaseIds("BLAZE_SMOKE");
                var reqresTests = suiteRegistry.resolveSuiteToTestCaseIds("REQRES_SMOKE");
                
                System.out.println("  - BLAZE_SMOKE Suite: " + 
                    (blazeTests.isPresent() ? blazeTests.get().size() + " tests" : "Not found"));
                System.out.println("  - REQRES_SMOKE Suite: " + 
                    (reqresTests.isPresent() ? reqresTests.get().size() + " tests" : "Not found"));
                    
                if (blazeTests.isEmpty() && reqresTests.isEmpty()) {
                    System.err.println("  - ⚠️ WARNING: No test suites found! Check database initialization.");
                }
            } catch (Exception e) {
                System.err.println("  - Suite Registry Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("✅ === DIAGNOSTIC TEST COMPLETED ===");
    }
    
    private void checkBean(String name, Object bean) {
        System.out.println("  - " + name + ": " + (bean != null ? "✅ Injected" : "❌ NULL"));
    }
}

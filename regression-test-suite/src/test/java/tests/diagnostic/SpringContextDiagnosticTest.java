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
        
        // Test Suite Registry and Database Content
        System.out.println("📋 Suite Registry & Database Test:");
        if (suiteRegistry != null) {
            try {
                // First, check what's in the database
                System.out.println("🗄️ Database Content Check:");
                if (applicationContext.containsBean("testCaseService")) {
                    var testCaseService = applicationContext.getBean("testCaseService");
                    try {
                        var allTestCases = (java.util.List<?>) testCaseService.getClass().getMethod("findAll").invoke(testCaseService);
                        System.out.println("  - Total test cases in database: " + allTestCases.size());
                        
                        if (allTestCases.size() > 0) {
                            System.out.println("  - Sample test cases:");
                            for (int i = 0; i < Math.min(5, allTestCases.size()); i++) {
                                var tc = allTestCases.get(i);
                                var id = tc.getClass().getMethod("getId").invoke(tc);
                                var name = tc.getClass().getMethod("getName").invoke(tc);
                                var type = tc.getClass().getMethod("getType").invoke(tc);
                                System.out.println("    - ID: " + id + ", Name: " + name + ", Type: " + type);
                            }
                        } else {
                            System.err.println("  - ❌ DATABASE IS EMPTY! No test cases found.");
                            System.err.println("  - This explains why no test suites are resolved.");
                        }
                    } catch (Exception dbE) {
                        System.err.println("  - Database query error: " + dbE.getMessage());
                    }
                }
                
                // Now test suite resolution
                System.out.println("📋 Suite Resolution Test:");
                var blazeTests = suiteRegistry.resolveSuiteToTestCaseIds("BLAZE_SMOKE");
                var reqresTests = suiteRegistry.resolveSuiteToTestCaseIds("REQRES_SMOKE");
                
                System.out.println("  - BLAZE_SMOKE Suite: " + 
                    (blazeTests.isPresent() ? blazeTests.get().size() + " tests" : "Not found"));
                System.out.println("  - REQRES_SMOKE Suite: " + 
                    (reqresTests.isPresent() ? reqresTests.get().size() + " tests" : "Not found"));
                    
                if (blazeTests.isPresent() && blazeTests.get().isEmpty() && 
                    reqresTests.isPresent() && reqresTests.get().isEmpty()) {
                    System.err.println("  - ⚠️ WARNING: Suite registry found but returned empty lists!");
                    System.err.println("  - This indicates test cases exist but names don't match expected names.");
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

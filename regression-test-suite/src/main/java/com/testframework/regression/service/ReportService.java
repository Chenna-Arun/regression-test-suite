package com.testframework.regression.service;

import com.testframework.regression.domain.TestResult;
import com.testframework.regression.repository.TestResultRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {
    
    private static final String REPORT_DIR = "test-output/reports";
    private final TestResultRepository testResultRepository;
    
    public ReportService(TestResultRepository testResultRepository) {
        this.testResultRepository = testResultRepository;
        try {
            Files.createDirectories(Paths.get(REPORT_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Transactional(readOnly = true)
    public String generateHTMLReport(String executionId) {
        try {
            List<TestResult> results = testResultRepository.findByExecutionIdWithTestCase(executionId);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "test_report_" + executionId + "_" + timestamp + ".html";
            Path filePath = Paths.get(REPORT_DIR, fileName);
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n<head>\n");
            html.append("<title>Test Execution Report</title>\n");
            html.append("<style>\n");
            html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
            html.append("table { border-collapse: collapse; width: 100%; }\n");
            html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
            html.append("th { background-color: #f2f2f2; }\n");
            html.append(".passed { background-color: #d4edda; }\n");
            html.append(".failed { background-color: #f8d7da; }\n");
            html.append(".summary { background-color: #e2e3e5; padding: 15px; margin-bottom: 20px; }\n");
            html.append("</style>\n</head>\n<body>\n");
            
            // Summary section
            long totalTests = results.size();
            long passedTests = results.stream().filter(r -> "PASSED".equals(r.getStatus().name())).count();
            long failedTests = totalTests - passedTests;
            double passRate = totalTests > 0 ? (double) passedTests / totalTests * 100 : 0;
            
            html.append("<div class='summary'>\n");
            html.append("<h2>Test Execution Summary</h2>\n");
            html.append("<p><strong>Execution ID:</strong> ").append(executionId).append("</p>\n");
            // Derive duration from first/last executedAt
            java.time.OffsetDateTime min = results.stream().map(TestResult::getExecutedAt).min(java.util.Comparator.naturalOrder()).orElse(null);
            java.time.OffsetDateTime max = results.stream().map(TestResult::getExecutedAt).max(java.util.Comparator.naturalOrder()).orElse(null);
            long durationSec = (min != null && max != null) ? java.time.Duration.between(min, max).toSeconds() : 0;
            html.append("<p><strong>Duration:</strong> ").append(durationSec).append("s</p>\n");
            html.append("<p><strong>Total Tests:</strong> ").append(totalTests).append("</p>\n");
            html.append("<p><strong>Passed:</strong> ").append(passedTests).append("</p>\n");
            html.append("<p><strong>Failed:</strong> ").append(failedTests).append("</p>\n");
            html.append("<p><strong>Pass Rate:</strong> ").append(String.format("%.2f", passRate)).append("%</p>\n");
            html.append("<p><strong>Generated:</strong> ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");
            html.append("</div>\n");
            
            // Results table
            html.append("<h2>Test Results</h2>\n");
            html.append("<table>\n");
            html.append("<tr><th>Test Case ID</th><th>Test Case Name</th><th>Type</th><th>Status</th><th>Executed At</th><th>Artifacts</th><th>Message</th></tr>\n");
            
            for (TestResult result : results) {
                String statusClass = "PASSED".equals(result.getStatus().name()) ? "passed" : "failed";
                html.append("<tr class='").append(statusClass).append("'>\n");
                html.append("<td>").append(result.getTestCase().getId()).append("</td>\n");
                html.append("<td>").append(result.getTestCase().getName()).append("</td>\n");
                html.append("<td>").append(result.getTestCase().getType()).append("</td>\n");
                html.append("<td>").append(result.getStatus()).append("</td>\n");
                html.append("<td>").append(result.getExecutedAt()).append("</td>\n");
                String artifacts = "";
                if (result.getScreenshotPath() != null) {
                    // Use relative path to artifacts directory
                    String relativePath = "../../../" + result.getScreenshotPath().replace("\\", "/");
                    artifacts += "<a href='" + relativePath + "' target='_blank'>screenshot</a>";
                }
                if (result.getApiRequestPath() != null) {
                    String relativePath = "../../../" + result.getApiRequestPath().replace("\\", "/");
                    artifacts += (artifacts.isEmpty() ? "" : " | ") + "<a href='" + relativePath + "' target='_blank'>request</a>";
                }
                if (result.getApiResponsePath() != null) {
                    String relativePath = "../../../" + result.getApiResponsePath().replace("\\", "/");
                    artifacts += (artifacts.isEmpty() ? "" : " | ") + "<a href='" + relativePath + "' target='_blank'>response</a>";
                }
                html.append("<td>").append(artifacts).append("</td>\n");
                html.append("<td>").append(result.getMessage() != null ? result.getMessage() : "").append("</td>\n");
                html.append("</tr>\n");
            }
            
            html.append("</table>\n");
            html.append("</body>\n</html>");
            
            Files.write(filePath, html.toString().getBytes());
            return filePath.toString();
            
        } catch (IOException e) {
            return "Report generation failed: " + e.getMessage();
        }
    }
    
    @Transactional(readOnly = true)
    public String generateCSVReport(String executionId) {
        try {
            List<TestResult> results = testResultRepository.findByExecutionIdWithTestCase(executionId);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "test_report_" + executionId + "_" + timestamp + ".csv";
            Path filePath = Paths.get(REPORT_DIR, fileName);
            
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                // Write header
                writer.append("Test Case ID,Test Case Name,Type,Status,Executed At,Duration(s),Artifact(s),Message\n");
                
                // Write data
                for (TestResult result : results) {
                    String artifacts = result.getScreenshotPath() != null ? Paths.get(result.getScreenshotPath()).getFileName().toString() : "";
                    writer.append(String.valueOf(result.getTestCase().getId())).append(",");
                    writer.append("\"").append(result.getTestCase().getName()).append("\"").append(",");
                    writer.append(result.getTestCase().getType().name()).append(",");
                    writer.append(result.getStatus().name()).append(",");
                    writer.append(result.getExecutedAt().toString()).append(",");
                    writer.append("0").append(",");
                    writer.append("\"").append(artifacts).append("\"").append(",");
                    writer.append("\"").append(result.getMessage() != null ? result.getMessage() : "").append("\"");
                    writer.append("\n");
                }
            }
            
            return filePath.toString();
            
        } catch (IOException e) {
            return "CSV report generation failed: " + e.getMessage();
        }
    }
    
    @Transactional(readOnly = true)
    public String collectLogs(String executionId) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "test_logs_" + executionId + "_" + timestamp + ".txt";
            Path filePath = Paths.get(REPORT_DIR, fileName);
            
            StringBuilder logs = new StringBuilder();
            logs.append("Test Execution Logs\n");
            logs.append("==================\n");
            logs.append("Execution ID: ").append(executionId).append("\n");
            logs.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            
            List<TestResult> results = testResultRepository.findByExecutionIdWithTestCase(executionId);
            for (TestResult result : results) {
                logs.append("Test Case: ").append(result.getTestCase().getName()).append("\n");
                logs.append("Type: ").append(result.getTestCase().getType()).append("\n");
                logs.append("Status: ").append(result.getStatus()).append("\n");
                logs.append("Executed At: ").append(result.getExecutedAt()).append("\n");
                if (result.getMessage() != null) {
                    logs.append("Message: ").append(result.getMessage()).append("\n");
                }
                logs.append("---\n");
            }
            
            Files.write(filePath, logs.toString().getBytes());
            return filePath.toString();
            
        } catch (IOException e) {
            return "Log collection failed: " + e.getMessage();
        }
    }

    @Transactional(readOnly = true)
    public String generateJUnitReport(String executionId) {
        try {
            List<TestResult> results = testResultRepository.findByExecutionIdWithTestCase(executionId);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "junit_report_" + executionId + "_" + timestamp + ".xml";
            Path filePath = Paths.get(REPORT_DIR, fileName);

            long tests = results.size();
            long failures = results.stream().filter(r -> !"PASSED".equals(r.getStatus().name())).count();
            java.time.OffsetDateTime min = results.stream().map(TestResult::getExecutedAt).min(java.util.Comparator.naturalOrder()).orElse(null);
            java.time.OffsetDateTime max = results.stream().map(TestResult::getExecutedAt).max(java.util.Comparator.naturalOrder()).orElse(null);
            long durationSec = (min != null && max != null) ? java.time.Duration.between(min, max).toSeconds() : 0;

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<testsuite name=\"RegressionSuite\" tests=\"").append(tests)
               .append("\" failures=\"").append(failures)
               .append("\" time=\"").append(durationSec)
               .append("\">\n");

            for (TestResult r : results) {
                String className = r.getTestCase().getType().name();
                String testName = r.getTestCase().getName();
                xml.append("  <testcase classname=\"").append(className)
                   .append("\" name=\"").append(testName).append("\">\n");
                if (!"PASSED".equals(r.getStatus().name())) {
                    xml.append("    <failure message=\"")
                       .append(escapeXml(r.getMessage()))
                       .append("\"/>\n");
                }
                xml.append("  </testcase>\n");
            }

            xml.append("</testsuite>\n");
            Files.write(filePath, xml.toString().getBytes());
            return filePath.toString();
        } catch (IOException e) {
            return "JUnit report generation failed: " + e.getMessage();
        }
    }

    private static String escapeXml(String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                 .replace("\"", "&quot;").replace("'", "&apos;");
    }
}


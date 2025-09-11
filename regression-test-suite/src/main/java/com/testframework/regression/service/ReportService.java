package com.testframework.regression.service;

import com.testframework.regression.domain.TestResult;
import com.testframework.regression.repository.TestResultRepository;
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
    
    public String generateHTMLReport(String executionId) {
        try {
            List<TestResult> results = testResultRepository.findByExecutionId(executionId);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "test_report_" + executionId + "_" + timestamp + ".html";
            Path filePath = Paths.get(REPORT_DIR, fileName);
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n<head>\n");
            html.append("<meta charset='utf-8'/>\n");
            html.append("<meta name='viewport' content='width=device-width, initial-scale=1'/>\n");
            html.append("<title>Test Execution Report</title>\n");
            html.append("<style>\n");
            html.append(":root{--bg:#0f172a;--panel:#111827;--panel-2:#1f2937;--text:#e5e7eb;--muted:#9ca3af;--green:#22c55e;--red:#ef4444;--amber:#f59e0b;--border:#374151;}\n");
            html.append("*{box-sizing:border-box} body{margin:0;background:linear-gradient(180deg,#0b1023,#0f172a);color:var(--text);font-family:Inter,Segoe UI,Roboto,Arial,sans-serif;line-height:1.45} \n");
            html.append(".container{max-width:1200px;margin:32px auto;padding:0 16px} \n");
            html.append(".header{display:flex;align-items:center;justify-content:space-between;background:var(--panel);border:1px solid var(--border);padding:16px 20px;border-radius:12px;box-shadow:0 10px 30px rgba(0,0,0,.35);} \n");
            html.append(".title{margin:0;font-size:20px;font-weight:700;letter-spacing:.3px} .chip{font-size:12px;color:#111827;background:linear-gradient(90deg,#93c5fd,#60a5fa);padding:6px 10px;border-radius:999px;border:0;} \n");
            html.append(".grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:20px 0} @media(max-width:900px){.grid{grid-template-columns:repeat(2,1fr)}} @media(max-width:560px){.grid{grid-template-columns:1fr}} \n");
            html.append(".card{background:var(--panel-2);border:1px solid var(--border);padding:18px;border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,.25);} .card h3{margin:0 0 6px 0;font-size:12px;color:var(--muted);font-weight:600;text-transform:uppercase;letter-spacing:.6px} .metric{font-size:28px;font-weight:800} \n");
            html.append(".good{color:var(--green)} .bad{color:var(--red)} .warn{color:var(--amber)} \n");
            html.append(".bar{height:10px;border-radius:999px;background:#0b1222;border:1px solid var(--border);overflow:hidden} .bar > span{display:block;height:100%;background:linear-gradient(90deg,#22c55e,#16a34a)} \n");
            html.append(".table-wrap{background:var(--panel);border:1px solid var(--border);border-radius:12px;overflow:auto;box-shadow:0 10px 30px rgba(0,0,0,.35);} \n");
            html.append("table{width:100%;border-collapse:collapse;min-width:960px} thead th{position:sticky;top:0;background:#0b1222;border-bottom:1px solid var(--border);padding:14px 16px;text-align:left;font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px} \n");
            html.append("tbody td{padding:14px 16px;border-bottom:1px solid var(--border);vertical-align:top;word-break:break-word;overflow-wrap:anywhere;white-space:normal;font-size:13px;line-height:1.5} tbody tr:hover{background:#0c1327} tbody tr:nth-child(even){background:#0b1222} \n");
            html.append("/* Column sizing for clarity */ thead th:nth-child(1),tbody td:nth-child(1){width:80px;text-align:center} thead th:nth-child(2),tbody td:nth-child(2){width:280px} thead th:nth-child(3),tbody td:nth-child(3){width:80px;text-align:center} thead th:nth-child(4),tbody td:nth-child(4){width:110px;text-align:center} thead th:nth-child(5),tbody td:nth-child(5){width:220px} thead th:nth-child(6),tbody td:nth-child(6){width:auto} \n");
            html.append(".message{max-width:540px} \n");
            html.append("@media(max-width:680px){ thead{display:none} table,tbody,tr,td{display:block;width:100%} tr{margin:0 0 12px 0;border-bottom:1px solid var(--border)} td{padding:10px 12px} td::before{content:attr(data-label);display:block;font-size:12px;color:var(--muted);text-transform:uppercase;margin-bottom:4px} } \n");
            html.append(".badge{display:inline-block;padding:4px 10px;border-radius:999px;font-size:12px;font-weight:700;letter-spacing:.3px} .badge.pass{color:#052e16;background:linear-gradient(90deg,#86efac,#22c55e)} .badge.fail{color:#3b0b0b;background:linear-gradient(90deg,#fca5a5,#ef4444)} \n");
            html.append(".mute{color:var(--muted)} .footer{margin:18px 0 8px 0;color:var(--muted);font-size:12px;text-align:center} \n");
            html.append("</style>\n</head>\n<body>\n");
            html.append("<div class='container'>\n");
            html.append("  <div class='header'>\n");
            html.append("    <h1 class='title'>📊 Test Execution Report</h1>\n");
            html.append("    <span class='chip'>Execution: " + executionId + "</span>\n");
            html.append("  </div>\n");
            
            // Summary section
            long totalTests = results.size();
            long passedTests = results.stream().filter(r -> "PASSED".equals(r.getStatus().name())).count();
            long failedTests = totalTests - passedTests;
            double passRate = totalTests > 0 ? (double) passedTests / totalTests * 100 : 0;
            
            html.append("  <div class='grid'>\n");
            html.append("    <div class='card'><h3>Total Tests</h3><div class='metric'>").append(totalTests).append("</div></div>\n");
            html.append("    <div class='card'><h3>Passed</h3><div class='metric good'>").append(passedTests).append("</div></div>\n");
            html.append("    <div class='card'><h3>Failed</h3><div class='metric bad'>").append(failedTests).append("</div></div>\n");
            html.append("    <div class='card'><h3>Pass Rate</h3><div class='metric warn'>").append(String.format("%.0f", passRate)).append("%</div><div class='bar'><span style='width:").append((int)passRate).append("%'></span></div></div>\n");
            html.append("  </div>\n");
            html.append("  <div class='card' style='margin-top:8px'><h3>Generated</h3><div class='mute'>").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</div></div>\n");
            
            // Results table
            html.append("  <div class='table-wrap' style='margin-top:20px'>\n");
            html.append("  <table>\n");
            html.append("  <thead><tr><th>Test Case ID</th><th>Test Case Name</th><th>Type</th><th>Status</th><th>Executed At</th><th>Message</th></tr></thead>\n");
            html.append("  <tbody>\n");
            
            for (TestResult result : results) {
                String statusClass = "PASSED".equals(result.getStatus().name()) ? "pass" : "fail";
                html.append("<tr>\n");
                html.append("<td data-label='Test Case ID'>").append(result.getTestCase().getId()).append("</td>\n");
                html.append("<td data-label='Test Case Name'>").append(result.getTestCase().getName()).append("</td>\n");
                html.append("<td data-label='Type'>").append(result.getTestCase().getType()).append("</td>\n");
                html.append("<td data-label='Status'><span class='badge ").append(statusClass).append("'>").append(result.getStatus()).append("</span></td>\n");
                html.append("<td data-label='Executed At'>").append(result.getExecutedAt()).append("</td>\n");
                html.append("<td class='message' data-label='Message'>").append(result.getMessage() != null ? result.getMessage() : "").append("</td>\n");
                html.append("</tr>\n");
            }
            html.append("  </tbody></table>\n");
            html.append("  </div>\n");
            html.append("  <div class='footer'>Generated by Regression Test Suite Framework</div>\n");
            html.append("</div>\n</body>\n</html>");
            
            Files.write(filePath, html.toString().getBytes());
            return filePath.toString();
            
        } catch (IOException e) {
            return "Report generation failed: " + e.getMessage();
        }
    }
    
    public String generateCSVReport(String executionId) {
        try {
            List<TestResult> results = testResultRepository.findAll();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "test_report_" + executionId + "_" + timestamp + ".csv";
            Path filePath = Paths.get(REPORT_DIR, fileName);
            
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                // Write header
                writer.append("Test Case ID,Test Case Name,Type,Status,Executed At,Message\n");
                
                // Write data
                for (TestResult result : results) {
                    writer.append(String.valueOf(result.getTestCase().getId())).append(",");
                    writer.append("\"").append(result.getTestCase().getName()).append("\"").append(",");
                    writer.append(result.getTestCase().getType().name()).append(",");
                    writer.append(result.getStatus().name()).append(",");
                    writer.append(result.getExecutedAt().toString()).append(",");
                    writer.append("\"").append(result.getMessage() != null ? result.getMessage() : "").append("\"");
                    writer.append("\n");
                }
            }
            
            return filePath.toString();
            
        } catch (IOException e) {
            return "CSV report generation failed: " + e.getMessage();
        }
    }
    
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
            
            List<TestResult> results = testResultRepository.findAll();
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
}


package com.testframework.regression.web;

import com.testframework.regression.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/generate")
    public ResponseEntity<ReportResponse> generateReport(@RequestParam String executionId) {
        try {
            String htmlReport = reportService.generateHTMLReport(executionId);
            String csvReport = reportService.generateCSVReport(executionId);
            String logs = reportService.collectLogs(executionId);

            ReportResponse response = new ReportResponse();
            response.setExecutionId(executionId);
            response.setStatus("SUCCESS");
            response.setHtmlReportPath(htmlReport);
            response.setCsvReportPath(csvReport);
            response.setLogsPath(logs);
            response.setMessage("Reports generated successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ReportResponse response = new ReportResponse();
            response.setExecutionId(executionId);
            response.setStatus("FAILED");
            response.setMessage("Report generation failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/collect")
    public ResponseEntity<Map<String, String>> collectLogs(@RequestBody LogCollectionRequest request) {
        Map<String, String> response = new HashMap<>();
        try {
            String logsPath = reportService.collectLogs(request.getExecutionId());
            response.put("status", "SUCCESS");
            response.put("logsPath", logsPath);
            response.put("message", "Logs collected successfully");
        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("message", "Log collection failed: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    // Request/Response DTOs
    public static class ReportResponse {
        private String executionId;
        private String status;
        private String htmlReportPath;
        private String csvReportPath;
        private String logsPath;
        private String message;

        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getHtmlReportPath() { return htmlReportPath; }
        public void setHtmlReportPath(String htmlReportPath) { this.htmlReportPath = htmlReportPath; }
        public String getCsvReportPath() { return csvReportPath; }
        public void setCsvReportPath(String csvReportPath) { this.csvReportPath = csvReportPath; }
        public String getLogsPath() { return logsPath; }
        public void setLogsPath(String logsPath) { this.logsPath = logsPath; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class LogCollectionRequest {
        private String executionId;

        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
    }
}


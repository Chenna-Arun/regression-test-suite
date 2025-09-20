package com.testframework.regression.web;

import com.testframework.regression.service.ReportService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/{executionId}/download")
    public ResponseEntity<FileSystemResource> downloadReport(@PathVariable String executionId,
                                                             @RequestParam(name = "type", defaultValue = "html") String type) {
        String path;
        if ("csv".equalsIgnoreCase(type)) {
            path = reportService.generateCSVReport(executionId);
        } else if ("junit".equalsIgnoreCase(type)) {
            path = reportService.generateJUnitReport(executionId);
        } else {
            path = reportService.generateHTMLReport(executionId);
        }
        FileSystemResource resource = new FileSystemResource(path);
        String filename = resource.getFilename();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if ("csv".equalsIgnoreCase(type)) mediaType = MediaType.TEXT_PLAIN;
        else if ("html".equalsIgnoreCase(type)) mediaType = MediaType.TEXT_HTML;
        else if ("junit".equalsIgnoreCase(type)) mediaType = MediaType.APPLICATION_XML;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/artifacts/{executionId}/{testCaseId}/{file}")
    public ResponseEntity<FileSystemResource> downloadArtifact(@PathVariable String executionId,
                                                               @PathVariable Long testCaseId,
                                                               @PathVariable String file) {
        String path = "artifacts/" + executionId + "/" + testCaseId + "/" + file;
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + resource.getFilename())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
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


package com.testframework.regression.web;

import com.testframework.regression.domain.TestResult;
import com.testframework.regression.engine.TestIntegrationEngine;
import com.testframework.regression.service.TestResultService;
import com.testframework.regression.service.EmailAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    private final TestIntegrationEngine testIntegrationEngine;
    private final TestResultService testResultService;
    private final EmailAlertService emailAlertService;
    private final Map<String, ExecutionStatus> executionStatuses = new ConcurrentHashMap<>();

    public ScheduleController(TestIntegrationEngine testIntegrationEngine, 
                            TestResultService testResultService,
                            EmailAlertService emailAlertService) {
        this.testIntegrationEngine = testIntegrationEngine;
        this.testResultService = testResultService;
        this.emailAlertService = emailAlertService;
    }

    @PostMapping("/run")
    public ResponseEntity<ExecutionResponse> runTests(@RequestBody ExecutionRequest request) {
        String executionId = "exec_" + System.currentTimeMillis();
        
        ExecutionStatus status = new ExecutionStatus();
        status.setExecutionId(executionId);
        status.setStatus("RUNNING");
        status.setStartTime(OffsetDateTime.now());
        status.setTestCaseIds(request.getTestCaseIds());
        status.setExecutionMode(request.getMode());
        executionStatuses.put(executionId, status);

        // Execute tests asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                List<TestResult> results;
                if ("SEQUENTIAL".equalsIgnoreCase(request.getMode())) {
                    results = testIntegrationEngine.executeSequential(request.getTestCaseIds());
                    // Tag executionId for sequential as well
                    for (TestResult r : results) { r.setExecutionId(executionId); }
                } else {
                    // Use the overload that guarantees FK + executionId
                    results = testIntegrationEngine.executeParallel(request.getTestCaseIds(), executionId);
                }
                
                status.setStatus("COMPLETED");
                status.setEndTime(OffsetDateTime.now());
                status.setResults(results);
                status.setTotalTests(results.size());
                status.setPassedTests((int) results.stream().filter(r -> r.getStatus() != null && "PASSED".equals(r.getStatus().name())).count());
                status.setFailedTests((int) results.stream().filter(r -> r.getStatus() != null && "FAILED".equals(r.getStatus().name())).count());
                
                // Send email alert after execution completion
                emailAlertService.sendTestExecutionAlert(executionId, results);
                
            } catch (Exception e) {
                status.setStatus("FAILED");
                status.setEndTime(OffsetDateTime.now());
                status.setErrorMessage(e.getMessage());
            }
        });

        ExecutionResponse response = new ExecutionResponse();
        response.setExecutionId(executionId);
        response.setStatus("STARTED");
        response.setMessage("Test execution started");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/execution/status/{executionId}")
    public ResponseEntity<ExecutionStatus> getExecutionStatus(@PathVariable String executionId) {
        ExecutionStatus status = executionStatuses.get(executionId);
        if (status != null) {
            return ResponseEntity.ok(status);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/execution/status")
    public ResponseEntity<Map<String, ExecutionStatus>> getAllExecutionStatuses() {
        return ResponseEntity.ok(executionStatuses);
    }

    // Request/Response DTOs
    public static class ExecutionRequest {
        private List<Long> testCaseIds;
        private String mode; // SEQUENTIAL or PARALLEL

        public List<Long> getTestCaseIds() { return testCaseIds; }
        public void setTestCaseIds(List<Long> testCaseIds) { this.testCaseIds = testCaseIds; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }

    public static class ExecutionResponse {
        private String executionId;
        private String status;
        private String message;

        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ExecutionStatus {
        private String executionId;
        private String status;
        private OffsetDateTime startTime;
        private OffsetDateTime endTime;
        private List<Long> testCaseIds;
        private String executionMode;
        private List<TestResult> results;
        private int totalTests;
        private int passedTests;
        private int failedTests;
        private String errorMessage;

        // Getters and setters
        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public OffsetDateTime getStartTime() { return startTime; }
        public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
        public OffsetDateTime getEndTime() { return endTime; }
        public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
        public List<Long> getTestCaseIds() { return testCaseIds; }
        public void setTestCaseIds(List<Long> testCaseIds) { this.testCaseIds = testCaseIds; }
        public String getExecutionMode() { return executionMode; }
        public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
        public List<TestResult> getResults() { return results; }
        public void setResults(List<TestResult> results) { this.results = results; }
        public int getTotalTests() { return totalTests; }
        public void setTotalTests(int totalTests) { this.totalTests = totalTests; }
        public int getPassedTests() { return passedTests; }
        public void setPassedTests(int passedTests) { this.passedTests = passedTests; }
        public int getFailedTests() { return failedTests; }
        public void setFailedTests(int failedTests) { this.failedTests = failedTests; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}

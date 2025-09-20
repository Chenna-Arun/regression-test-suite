package com.testframework.regression.web;

import com.testframework.regression.domain.TestResult;
import com.testframework.regression.engine.TestIntegrationEngine;
import com.testframework.regression.service.TestResultService;
import com.testframework.regression.engine.SuiteRegistry;
import com.testframework.regression.domain.ExecutionRecord;
import com.testframework.regression.repository.ExecutionRecordRepository;
import org.springframework.scheduling.TaskScheduler;
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
    private final SuiteRegistry suiteRegistry;
    private final TaskScheduler taskScheduler;
    private final ExecutionRecordRepository executionRecordRepository;

    public ScheduleController(TestIntegrationEngine testIntegrationEngine, 
                            TestResultService testResultService,
                            EmailAlertService emailAlertService,
                            SuiteRegistry suiteRegistry,
                            TaskScheduler taskScheduler,
                            ExecutionRecordRepository executionRecordRepository) {
        this.testIntegrationEngine = testIntegrationEngine;
        this.testResultService = testResultService;
        this.emailAlertService = emailAlertService;
        this.suiteRegistry = suiteRegistry;
        this.taskScheduler = taskScheduler;
        this.executionRecordRepository = executionRecordRepository;
    }

    @PostMapping("/run")
    public ResponseEntity<ExecutionResponse> runTests(@RequestBody ExecutionRequest request) {
        String executionId = "exec_" + System.currentTimeMillis();
        
        ExecutionStatus status = new ExecutionStatus();
        status.setExecutionId(executionId);
        // Resolve suiteId if provided
        List<Long> ids = request.getTestCaseIds();
        if ((ids == null || ids.isEmpty()) && request.getSuiteId() != null) {
            ids = suiteRegistry.resolveSuiteToTestCaseIds(request.getSuiteId()).orElse(List.of());
        }

        // Determine if this is a scheduled (future) run
        if (request.getScheduledTime() != null && request.getScheduledTime().isAfter(OffsetDateTime.now())) {
            status.setStatus("QUEUED");
            status.setStartTime(null);
        } else {
            status.setStatus("RUNNING");
            status.setStartTime(OffsetDateTime.now());
        }
        // Resolve suiteId if provided
        status.setTestCaseIds(ids);
        status.setExecutionMode(request.getMode());
        executionStatuses.put(executionId, status);
        // Persist initial record
        ExecutionRecord rec = new ExecutionRecord();
        rec.setExecutionId(executionId);
        rec.setStatus(status.getStatus());
        rec.setStartTime(status.getStartTime());
        rec.setMode(request.getMode());
        rec.setTestCaseIdsCsv(ids != null ? ids.toString() : "");
        executionRecordRepository.save(rec);

        // Make effectively-final copies for lambda usage
        final String runExecutionId = executionId;
        final List<Long> runIds = ids;
        final ExecutionRequest runRequest = request;
        final ExecutionStatus runStatus = status;

        Runnable task = () -> {
            // transition to RUNNING if was queued
            if ("QUEUED".equals(runStatus.getStatus())) {
                runStatus.setStatus("RUNNING");
                runStatus.setStartTime(OffsetDateTime.now());
            }
            try {
                List<TestResult> results;
                if ("SEQUENTIAL".equalsIgnoreCase(runRequest.getMode())) {
                    results = testIntegrationEngine.executeSequential(runIds);
                    for (TestResult r : results) { r.setExecutionId(runExecutionId); }
                } else {
                    results = testIntegrationEngine.executeParallel(runIds, runExecutionId, runRequest.getMaxParallelTests(), runRequest.getHeadless());
                }
                runStatus.setStatus("COMPLETED");
                runStatus.setEndTime(OffsetDateTime.now());
                runStatus.setResults(results);
                runStatus.setTotalTests(results.size());
                runStatus.setPassedTests((int) results.stream().filter(r -> r.getStatus() != null && "PASSED".equals(r.getStatus().name())).count());
                runStatus.setFailedTests((int) results.stream().filter(r -> r.getStatus() != null && "FAILED".equals(r.getStatus().name())).count());
                emailAlertService.sendTestExecutionAlert(runExecutionId, results);
                // Persist completion
                ExecutionRecord done = executionRecordRepository.findByExecutionId(runExecutionId).orElse(new ExecutionRecord());
                done.setExecutionId(runExecutionId);
                done.setStatus("COMPLETED");
                done.setStartTime(runStatus.getStartTime());
                done.setEndTime(runStatus.getEndTime());
                done.setMode(runStatus.getExecutionMode());
                done.setTestCaseIdsCsv(runIds != null ? runIds.toString() : "");
                done.setTotalTests(runStatus.getTotalTests());
                done.setPassedTests(runStatus.getPassedTests());
                done.setFailedTests(runStatus.getFailedTests());
                executionRecordRepository.save(done);
            } catch (Exception e) {
                runStatus.setStatus("FAILED");
                runStatus.setEndTime(OffsetDateTime.now());
                runStatus.setErrorMessage(e.getMessage());
                // Persist failure
                ExecutionRecord fail = executionRecordRepository.findByExecutionId(runExecutionId).orElse(new ExecutionRecord());
                fail.setExecutionId(runExecutionId);
                fail.setStatus("FAILED");
                fail.setStartTime(runStatus.getStartTime());
                fail.setEndTime(runStatus.getEndTime());
                fail.setMode(runStatus.getExecutionMode());
                fail.setTestCaseIdsCsv(runIds != null ? runIds.toString() : "");
                fail.setErrorMessage(e.getMessage());
                executionRecordRepository.save(fail);
            }
        };

        if ("QUEUED".equals(runStatus.getStatus())) {
            taskScheduler.schedule(task, java.util.Date.from(runRequest.getScheduledTime().toInstant()));
        } else {
            CompletableFuture.runAsync(task);
        }

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
        private String suiteId; // e.g., BLAZE_SMOKE, REQRES_SMOKE
        private String mode; // SEQUENTIAL or PARALLEL
        private Integer maxParallelTests; // optional cap
        private Boolean headless; // UI browsers headless
        private OffsetDateTime scheduledTime; // optional future scheduling

        public List<Long> getTestCaseIds() { return testCaseIds; }
        public void setTestCaseIds(List<Long> testCaseIds) { this.testCaseIds = testCaseIds; }
        public String getSuiteId() { return suiteId; }
        public void setSuiteId(String suiteId) { this.suiteId = suiteId; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Integer getMaxParallelTests() { return maxParallelTests; }
        public void setMaxParallelTests(Integer maxParallelTests) { this.maxParallelTests = maxParallelTests; }
        public Boolean getHeadless() { return headless; }
        public void setHeadless(Boolean headless) { this.headless = headless; }
        public OffsetDateTime getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(OffsetDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
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

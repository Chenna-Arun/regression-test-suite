package com.testframework.regression.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "execution_records")
public class ExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", unique = true, nullable = false, length = 100)
    private String executionId;

    @Column(name = "status", length = 20, nullable = false)
    private String status; // QUEUED, RUNNING, COMPLETED, FAILED

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "mode", length = 20)
    private String mode; // SEQUENTIAL, PARALLEL

    @Column(name = "test_case_ids", length = 2000)
    private String testCaseIdsCsv; // comma-separated ids

    @Column(name = "total_tests")
    private Integer totalTests;

    @Column(name = "passed_tests")
    private Integer passedTests;

    @Column(name = "failed_tests")
    private Integer failedTests;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getTestCaseIdsCsv() { return testCaseIdsCsv; }
    public void setTestCaseIdsCsv(String testCaseIdsCsv) { this.testCaseIdsCsv = testCaseIdsCsv; }
    public Integer getTotalTests() { return totalTests; }
    public void setTotalTests(Integer totalTests) { this.totalTests = totalTests; }
    public Integer getPassedTests() { return passedTests; }
    public void setPassedTests(Integer passedTests) { this.passedTests = passedTests; }
    public Integer getFailedTests() { return failedTests; }
    public void setFailedTests(Integer failedTests) { this.failedTests = failedTests; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}









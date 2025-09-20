package com.testframework.regression.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "test_results")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    @JsonBackReference
    private TestCase testCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt = OffsetDateTime.now();

    @Column(length = 4000)
    private String message;

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "screenshot_path", length = 500)
    private String screenshotPath;

    @Column(name = "api_request_path", length = 500)
    private String apiRequestPath;

    @Column(name = "api_response_path", length = 500)
    private String apiResponsePath;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TestCase getTestCase() { return testCase; }
    public void setTestCase(TestCase testCase) { this.testCase = testCase; }

    public TestStatus getStatus() { return status; }
    public void setStatus(TestStatus status) { this.status = status; }

    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getScreenshotPath() { return screenshotPath; }
    public void setScreenshotPath(String screenshotPath) { this.screenshotPath = screenshotPath; }

    public String getApiRequestPath() { return apiRequestPath; }
    public void setApiRequestPath(String apiRequestPath) { this.apiRequestPath = apiRequestPath; }

    public String getApiResponsePath() { return apiResponsePath; }
    public void setApiResponsePath(String apiResponsePath) { this.apiResponsePath = apiResponsePath; }
}



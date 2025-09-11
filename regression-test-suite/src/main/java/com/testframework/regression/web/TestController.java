package com.testframework.regression.web;

import com.testframework.regression.domain.TestCase;
import com.testframework.regression.domain.TestResult;
import com.testframework.regression.domain.TestType;
import com.testframework.regression.engine.TestIntegrationEngine;
import com.testframework.regression.service.TestCaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tests")
public class TestController {

    private final TestCaseService testCaseService;
    private final TestIntegrationEngine testIntegrationEngine;

    public TestController(TestCaseService testCaseService, TestIntegrationEngine testIntegrationEngine) {
        this.testCaseService = testCaseService;
        this.testIntegrationEngine = testIntegrationEngine;
    }

    // Test Integration Engine APIs
    @PostMapping("/integrate")
    public ResponseEntity<?> createTestCase(@RequestBody TestCaseRequest request) {
        try {
            TestCase testCase = testIntegrationEngine.createTestCase(
                request.getName(), 
                request.getType(), 
                request.getDescription()
            );
            return ResponseEntity.ok(testCase);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body("Error creating test case: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestCase> getTestCase(@PathVariable Long id) {
        TestCase testCase = testIntegrationEngine.getTestCase(id);
        if (testCase != null) {
            return ResponseEntity.ok(testCase);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<TestCase>> getAllTestCases() {
        return ResponseEntity.ok(testIntegrationEngine.getAllTestCases());
    }

    // Legacy endpoints for backward compatibility
    @PostMapping
    public ResponseEntity<TestCase> create(@RequestBody TestCase testCase) {
        return ResponseEntity.ok(testCaseService.save(testCase));
    }

    @GetMapping("/list")
    public ResponseEntity<List<TestCase>> list() {
        return ResponseEntity.ok(testCaseService.findAll());
    }

    // Request DTOs
    public static class TestCaseRequest {
        private String name;
        private TestType type;
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public TestType getType() { return type; }
        public void setType(TestType type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
package com.testframework.regression.service;

import com.testframework.regression.domain.TestCase;
import com.testframework.regression.domain.TestResult;
import com.testframework.regression.repository.TestResultRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestResultService {

    private final TestResultRepository testResultRepository;

    public TestResultService(TestResultRepository testResultRepository) {
        this.testResultRepository = testResultRepository;
    }

    public TestResult save(TestResult testResult) {
        return testResultRepository.save(testResult);
    }

    public List<TestResult> findByTestCase(TestCase testCase) {
        return testResultRepository.findByTestCase(testCase);
    }
}




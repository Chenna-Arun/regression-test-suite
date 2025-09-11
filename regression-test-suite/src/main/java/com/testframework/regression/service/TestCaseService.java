package com.testframework.regression.service;

import com.testframework.regression.domain.TestCase;
import com.testframework.regression.domain.TestType;
import com.testframework.regression.repository.TestCaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;

    public TestCaseService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    public TestCase save(TestCase testCase) {
        return testCaseRepository.save(testCase);
    }

    public Optional<TestCase> findById(Long id) {
        return testCaseRepository.findById(id);
    }

    public List<TestCase> findAll() {
        return testCaseRepository.findAll();
    }

    public List<TestCase> findByType(TestType type) {
        return testCaseRepository.findByType(type);
    }

    public Optional<TestCase> findByName(String name) {
        return testCaseRepository.findByName(name);
    }

    public void delete(Long id) {
        testCaseRepository.deleteById(id);
    }
}




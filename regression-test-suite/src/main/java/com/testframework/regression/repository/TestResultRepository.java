package com.testframework.regression.repository;

import com.testframework.regression.domain.TestResult;
import com.testframework.regression.domain.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findByTestCase(TestCase testCase);
    List<TestResult> findByExecutionId(String executionId);
}




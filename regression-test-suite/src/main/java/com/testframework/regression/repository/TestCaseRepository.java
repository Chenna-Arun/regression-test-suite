package com.testframework.regression.repository;

import com.testframework.regression.domain.TestCase;
import com.testframework.regression.domain.TestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByType(TestType type);
    Optional<TestCase> findByName(String name);
}




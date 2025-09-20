package com.testframework.regression.repository;

import com.testframework.regression.domain.ExecutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExecutionRecordRepository extends JpaRepository<ExecutionRecord, Long> {
    Optional<ExecutionRecord> findByExecutionId(String executionId);
}









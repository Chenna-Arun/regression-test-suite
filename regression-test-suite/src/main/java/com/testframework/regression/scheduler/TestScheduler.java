package com.testframework.regression.scheduler;

import com.testframework.regression.domain.TestCase;
import com.testframework.regression.engine.TestIntegrationEngine;
import com.testframework.regression.service.TestCaseService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@EnableScheduling
public class TestScheduler {

    private final TestCaseService testCaseService;
    private final TestIntegrationEngine testIntegrationEngine;

    public TestScheduler(TestCaseService testCaseService, TestIntegrationEngine testIntegrationEngine) {
        this.testCaseService = testCaseService;
        this.testIntegrationEngine = testIntegrationEngine;
    }

    // Every Saturday at 22:00 local time
    @Scheduled(cron = "0 0 22 * * SAT")
    public void runWeeklyRegression() {
        List<TestCase> all = testCaseService.findAll();
        List<Long> testCaseIds = all.stream().map(TestCase::getId).toList();
        testIntegrationEngine.executeParallel(testCaseIds);
    }
}



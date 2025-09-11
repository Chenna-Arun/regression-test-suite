package com.testframework.regression.service;

import com.testframework.regression.domain.TestResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailAlertService {
    
    @Autowired(required = false)
    private JavaMailSender mailSender;
    
    public void sendTestExecutionAlert(String executionId, List<TestResult> results) {
        if (mailSender == null) {
            System.out.println("Email service not configured. Skipping email alert for execution: " + executionId);
            return;
        }
        
        try {
            long totalTests = results.size();
            long passedTests = results.stream().filter(r -> "PASSED".equals(r.getStatus().name())).count();
            long failedTests = totalTests - passedTests;
            double passRate = totalTests > 0 ? (double) passedTests / totalTests * 100 : 0;
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("admin@testframework.com"); // Configure recipient
            message.setSubject("Test Execution Alert - " + executionId);
            
            StringBuilder body = new StringBuilder();
            body.append("Test Execution Summary\n");
            body.append("=====================\n");
            body.append("Execution ID: ").append(executionId).append("\n");
            body.append("Total Tests: ").append(totalTests).append("\n");
            body.append("Passed: ").append(passedTests).append("\n");
            body.append("Failed: ").append(failedTests).append("\n");
            body.append("Pass Rate: ").append(String.format("%.2f", passRate)).append("%\n\n");
            
            if (failedTests > 0) {
                body.append("Failed Tests:\n");
                results.stream()
                    .filter(r -> "FAILED".equals(r.getStatus().name()))
                    .forEach(r -> body.append("- ").append(r.getTestCase().getName())
                        .append(": ").append(r.getMessage()).append("\n"));
            }
            
            message.setText(body.toString());
            mailSender.send(message);
            
            System.out.println("Email alert sent for execution: " + executionId);
            
        } catch (Exception e) {
            System.err.println("Failed to send email alert: " + e.getMessage());
        }
    }
    
    public void sendFailureAlert(String executionId, TestResult failedResult) {
        if (mailSender == null) {
            System.out.println("Email service not configured. Skipping failure alert for execution: " + executionId);
            return;
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo("admin@testframework.com"); // Configure recipient
            message.setSubject("Test Failure Alert - " + failedResult.getTestCase().getName());
            
            StringBuilder body = new StringBuilder();
            body.append("Test Failure Alert\n");
            body.append("==================\n");
            body.append("Execution ID: ").append(executionId).append("\n");
            body.append("Test Case: ").append(failedResult.getTestCase().getName()).append("\n");
            body.append("Type: ").append(failedResult.getTestCase().getType()).append("\n");
            body.append("Failed At: ").append(failedResult.getExecutedAt()).append("\n");
            body.append("Error Message: ").append(failedResult.getMessage()).append("\n");
            
            message.setText(body.toString());
            mailSender.send(message);
            
            System.out.println("Failure alert sent for test: " + failedResult.getTestCase().getName());
            
        } catch (Exception e) {
            System.err.println("Failed to send failure alert: " + e.getMessage());
        }
    }
}


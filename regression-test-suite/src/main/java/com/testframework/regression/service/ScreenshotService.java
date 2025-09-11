package com.testframework.regression.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ScreenshotService {
    
    private static final String SCREENSHOT_DIR = "test-output/screenshots";
    
    public ScreenshotService() {
        try {
            Files.createDirectories(Paths.get(SCREENSHOT_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public String captureFailureScreenshot(String testName, String errorMessage) {
        try {
            // Create a placeholder screenshot file for demonstration
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "FAILED_" + testName + "_" + timestamp + ".txt";
            Path filePath = Paths.get(SCREENSHOT_DIR, fileName);
            
            // Write error details to file (in real implementation, this would be an actual screenshot)
            String content = "Test: " + testName + "\n" +
                           "Error: " + errorMessage + "\n" +
                           "Timestamp: " + timestamp + "\n" +
                           "Thread: " + Thread.currentThread().getId();
            
            Files.write(filePath, content.getBytes());
            
            return filePath.toString();
        } catch (IOException e) {
            return "Screenshot capture failed: " + e.getMessage();
        }
    }
    
    public String getScreenshotPath(String testName) {
        return SCREENSHOT_DIR + "/" + testName;
    }
}


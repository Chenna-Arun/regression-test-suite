package com.testframework.regression.service;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

@Service
public class ScreenshotService {
    
    private static final String SCREENSHOT_DIR = "artifacts";
    
    public ScreenshotService() {
        try {
            Files.createDirectories(Paths.get(SCREENSHOT_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public String captureFailureScreenshot(String testName, String errorMessage) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "FAILED_" + testName + "_" + timestamp + ".png";
            Path filePath = Paths.get(SCREENSHOT_DIR, fileName);
            Files.createDirectories(filePath.getParent());
            
            // Create a simple error screenshot with text
            BufferedImage image = createErrorScreenshot(testName, errorMessage);
            ImageIO.write(image, "PNG", filePath.toFile());
            
            return filePath.toString();
        } catch (IOException e) {
            return "Screenshot capture failed: " + e.getMessage();
        }
    }

    public String captureFailureScreenshot(String testName, String errorMessage, String executionId, Long testCaseId) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "FAILED_" + testName + "_" + timestamp + ".png";
            Path dir = Paths.get(SCREENSHOT_DIR, executionId, String.valueOf(testCaseId));
            Files.createDirectories(dir);
            Path filePath = dir.resolve(fileName);
            
            // Create a simple error screenshot with text
            BufferedImage image = createErrorScreenshot(testName, errorMessage);
            ImageIO.write(image, "PNG", filePath.toFile());
            
            return filePath.toString();
        } catch (IOException e) {
            return "Screenshot capture failed: " + e.getMessage();
        }
    }
    
    public String captureWebDriverScreenshot(WebDriver driver, String testName, String errorMessage, String executionId, Long testCaseId) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "FAILED_" + testName + "_" + timestamp + ".png";
            Path dir = Paths.get(SCREENSHOT_DIR, executionId, String.valueOf(testCaseId));
            Files.createDirectories(dir);
            Path filePath = dir.resolve(fileName);
            
            if (driver instanceof TakesScreenshot) {
                byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Files.write(filePath, screenshot);
            } else {
                // Fallback to error screenshot
                BufferedImage image = createErrorScreenshot(testName, errorMessage);
                ImageIO.write(image, "PNG", filePath.toFile());
            }
            
            return filePath.toString();
        } catch (Exception e) {
            return "Screenshot capture failed: " + e.getMessage();
        }
    }
    
    private BufferedImage createErrorScreenshot(String testName, String errorMessage) {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // Set background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 800, 600);
        
        // Set text properties
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        
        // Draw test name
        g2d.drawString("Test Failed: " + testName, 20, 50);
        
        // Draw error message (wrap text)
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        String[] words = errorMessage.split(" ");
        int y = 100;
        StringBuilder line = new StringBuilder();
        
        for (String word : words) {
            if (g2d.getFontMetrics().stringWidth(line + word + " ") < 750) {
                line.append(word).append(" ");
            } else {
                g2d.drawString(line.toString(), 20, y);
                line = new StringBuilder(word + " ");
                y += 20;
                if (y > 550) break; // Prevent overflow
            }
        }
        if (line.length() > 0) {
            g2d.drawString(line.toString(), 20, y);
        }
        
        g2d.dispose();
        return image;
    }
    
    public String getScreenshotPath(String testName) {
        return SCREENSHOT_DIR + "/" + testName;
    }
}


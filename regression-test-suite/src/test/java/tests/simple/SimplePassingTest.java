package tests.simple;

import org.testng.annotations.Test;
import org.testng.Assert;

public class SimplePassingTest {

    @Test
    public void simplePassingTest() {
        System.out.println("🎯 Running simple passing test...");
        Assert.assertTrue(true, "This test should always pass");
        System.out.println("✅ Simple test passed successfully!");
    }
    
    @Test
    public void basicMathTest() {
        System.out.println("🔢 Running basic math test...");
        int result = 2 + 2;
        Assert.assertEquals(result, 4, "Basic math should work");
        System.out.println("✅ Math test passed!");
    }
    
    @Test
    public void stringTest() {
        System.out.println("📝 Running string test...");
        String hello = "Hello";
        String world = "World";
        String combined = hello + " " + world;
        Assert.assertEquals(combined, "Hello World", "String concatenation should work");
        System.out.println("✅ String test passed!");
    }
}

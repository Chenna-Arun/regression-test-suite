package tests.simple;

import org.testng.annotations.Test;

public class SimplePassingTest {

    @Test
    public void simplePassingTest() {
        System.out.println("✅ Simple passing test executed successfully");
        // This test always passes - used as a fallback
        assert true;
    }
    
    @Test
    public void anotherSimpleTest() {
        System.out.println("✅ Another simple test executed successfully");
        // Basic assertion
        String expected = "Hello";
        String actual = "Hello";
        assert expected.equals(actual) : "Strings should match";
    }
}

package com.testframework.regression;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RegressionTestSuiteFrameworkApplication {

	public static void main(String[] args) {
		SpringApplication.run(RegressionTestSuiteFrameworkApplication.class, args);
	}

}

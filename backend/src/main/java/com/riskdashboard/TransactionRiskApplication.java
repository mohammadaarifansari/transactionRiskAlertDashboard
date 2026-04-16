package com.riskdashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Transaction Risk Alert Dashboard backend.
 *
 * <p>Starts an embedded Tomcat server on port 8080 (configurable via
 * {@code application.properties}). All service beans are wired in
 * {@link com.riskdashboard.config.ApplicationConfig}.</p>
 */
@SpringBootApplication
public class TransactionRiskApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionRiskApplication.class, args);
    }
}

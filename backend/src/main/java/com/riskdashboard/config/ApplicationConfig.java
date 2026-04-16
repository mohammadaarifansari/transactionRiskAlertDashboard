package com.riskdashboard.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskdashboard.model.TransactionData;
import com.riskdashboard.service.TransactionDataLoaderService.DataLoadException;
import com.riskdashboard.service.FraudSignalCalculatorService;
import com.riskdashboard.service.HourlyWindowAggregatorService;
import com.riskdashboard.service.RiskScoringConfig;
import com.riskdashboard.service.RiskScoringService;
import com.riskdashboard.service.TransactionDataLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Spring bean configuration for all domain services.
 *
 * <p>All scoring and aggregation services are plain Java objects — no Spring
 * annotations inside them. This class is the only place where they are
 * wired together and exposed as managed beans.</p>
 *
 * <p>Transaction data is loaded eagerly at startup from the classpath resource
 * {@code data/transactions.json}. A startup failure here is intentional:
 * no request should be served without valid data.</p>
 */
@Configuration
public class ApplicationConfig {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public TransactionData.DataFile transactionDataFile(ObjectMapper objectMapper) {
        TransactionDataLoaderService loader = new TransactionDataLoaderService(objectMapper);
        ClassPathResource resource = new ClassPathResource("data/transactions.json");
        try (InputStream is = resource.getInputStream()) {
            TransactionData.DataFile dataFile = loader.load(is);
            log.info("Startup data load complete: {} accounts, {} transactions",
                    dataFile.accounts().size(), dataFile.transactions().size());
            return dataFile;
        } catch (IOException e) {
            throw new DataLoadException("Cannot open classpath resource data/transactions.json: " + e.getMessage(), e);
        }
    }

    @Bean
    public RiskScoringConfig riskScoringConfig() {
        return new RiskScoringConfig();
    }

    @Bean
    public FraudSignalCalculatorService fraudSignalCalculatorService(RiskScoringConfig config) {
        return new FraudSignalCalculatorService(config);
    }

    @Bean
    public RiskScoringService riskScoringService(FraudSignalCalculatorService calculatorService,
                                                   RiskScoringConfig config) {
        return new RiskScoringService(calculatorService, config);
    }

    @Bean
    public HourlyWindowAggregatorService hourlyWindowAggregatorService(RiskScoringConfig config) {
        return new HourlyWindowAggregatorService(config);
    }
}

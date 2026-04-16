package com.riskdashboard.service;

import com.riskdashboard.model.FraudSignalAssessment;
import com.riskdashboard.model.FraudSignalType;
import com.riskdashboard.model.TransactionData.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Calculates individual fraud signal assessments for a given account's transaction list.
 *
 * <p>All public methods are stateless and deterministic for the same input.
 * Malformed timestamps in individual transactions are logged as warnings and those
 * transactions are excluded from time-windowed signals (VELOCITY and GEO) only;
 * they still contribute to non-temporal signals (MCC, HIGH_VALUE).</p>
 *
 * <p>An empty transaction list returns a zero-scored signal for every type.</p>
 */
public class FraudSignalCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(FraudSignalCalculatorService.class);

    private final RiskScoringConfig config;

    public FraudSignalCalculatorService(RiskScoringConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("RiskScoringConfig must not be null");
        }
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Public signal calculation methods
    // -------------------------------------------------------------------------

    /**
     * Calculates the velocity anomaly signal.
     *
     * <p>Raw value = number of transactions in the 1-hour window ending at
     * {@code referenceTime}. Normalised score = {@code min(rawValue / velocityThreshold, 1.0)}.</p>
     *
     * @param transactions  non-null account transaction list
     * @param referenceTime the instant representing "now"
     * @return {@link FraudSignalAssessment} with type {@link FraudSignalType#VELOCITY}
     * @throws IllegalArgumentException if either argument is null
     */
    public FraudSignalAssessment calculateVelocity(List<Transaction> transactions,
                                                    Instant referenceTime) {
        validateTimeWindowInputs(transactions, referenceTime);

        Instant windowStart = referenceTime.minusSeconds(3600);
        int count = 0;
        for (Transaction tx : transactions) {
            Optional<Instant> parsed = parseTimestamp(tx.transactionId(), tx.timestamp());
            if (parsed.isPresent()) {
                Instant ts = parsed.get();
                if (!ts.isBefore(windowStart) && !ts.isAfter(referenceTime)) {
                    count++;
                }
            }
        }

        double normalizedScore = Math.min((double) count / config.getVelocityThreshold(), 1.0);
        double weightedContribution = normalizedScore * config.getVelocityWeight();
        String explanation = count == 1
                ? "1 transaction recorded in the last hour"
                : count + " transactions recorded in the last hour";

        return new FraudSignalAssessment(
                FraudSignalType.VELOCITY,
                count,
                normalizedScore,
                config.getVelocityWeight(),
                weightedContribution,
                explanation
        );
    }

    /**
     * Calculates the geo anomaly signal.
     *
     * <p>Raw value = number of distinct non-null destination countries in the 24-hour
     * window ending at {@code referenceTime}. Normalised score =
     * {@code min(rawValue / geoThreshold, 1.0)}.</p>
     *
     * @param transactions  non-null account transaction list
     * @param referenceTime the instant representing "now"
     * @return {@link FraudSignalAssessment} with type {@link FraudSignalType#GEO}
     * @throws IllegalArgumentException if either argument is null
     */
    public FraudSignalAssessment calculateGeo(List<Transaction> transactions,
                                               Instant referenceTime) {
        validateTimeWindowInputs(transactions, referenceTime);

        Instant windowStart = referenceTime.minusSeconds(86_400);
        Set<String> distinctCountries = new HashSet<>();
        for (Transaction tx : transactions) {
            Optional<Instant> parsed = parseTimestamp(tx.transactionId(), tx.timestamp());
            if (parsed.isPresent()) {
                Instant ts = parsed.get();
                if (!ts.isBefore(windowStart) && !ts.isAfter(referenceTime)) {
                    if (tx.destinationCountry() != null && !tx.destinationCountry().isBlank()) {
                        distinctCountries.add(tx.destinationCountry().trim().toUpperCase());
                    }
                }
            }
        }

        int rawValue = distinctCountries.size();
        double normalizedScore = Math.min((double) rawValue / config.getGeoThreshold(), 1.0);
        double weightedContribution = normalizedScore * config.getGeoWeight();
        String explanation = rawValue == 1
                ? "Activity in 1 destination country in the last 24 hours"
                : "Activity spans " + rawValue + " distinct destination countries in the last 24 hours";

        return new FraudSignalAssessment(
                FraudSignalType.GEO,
                rawValue,
                normalizedScore,
                config.getGeoWeight(),
                weightedContribution,
                explanation
        );
    }

    /**
     * Calculates the unusual merchant category (MCC) signal.
     *
     * <p>Raw value = count of transactions in elevated-risk merchant categories.
     * Normalised score = {@code rawValue / totalTransactionCount}, or {@code 0.0} when
     * the list is empty.</p>
     *
     * @param transactions non-null account transaction list
     * @return {@link FraudSignalAssessment} with type {@link FraudSignalType#MCC}
     * @throws IllegalArgumentException if {@code transactions} is null
     */
    public FraudSignalAssessment calculateMcc(List<Transaction> transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transactions list must not be null");
        }
        if (transactions.isEmpty()) {
            return zeroSignal(FraudSignalType.MCC, config.getMccWeight(),
                    "No transactions to evaluate for merchant category risk");
        }

        int highRiskCount = 0;
        for (Transaction tx : transactions) {
            if (tx.merchantCategory() != null
                    && config.getHighRiskMerchantCategories()
                             .contains(tx.merchantCategory().trim().toUpperCase())) {
                highRiskCount++;
            }
        }

        double normalizedScore = (double) highRiskCount / transactions.size();
        double weightedContribution = normalizedScore * config.getMccWeight();
        String explanation = highRiskCount + " of " + transactions.size()
                + " transaction(s) in elevated-risk merchant categories";

        return new FraudSignalAssessment(
                FraudSignalType.MCC,
                highRiskCount,
                normalizedScore,
                config.getMccWeight(),
                weightedContribution,
                explanation
        );
    }

    /**
     * Calculates the high-value spike signal.
     *
     * <p>Raw value = maximum single transaction amount across all provided transactions.
     * Normalised score = {@code min(rawValue / highValueThreshold, 1.0)}.</p>
     *
     * @param transactions non-null account transaction list
     * @return {@link FraudSignalAssessment} with type {@link FraudSignalType#HIGH_VALUE}
     * @throws IllegalArgumentException if {@code transactions} is null
     */
    public FraudSignalAssessment calculateHighValue(List<Transaction> transactions) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transactions list must not be null");
        }
        if (transactions.isEmpty()) {
            return zeroSignal(FraudSignalType.HIGH_VALUE, config.getHighValueWeight(),
                    "No transactions to evaluate for high-value spike");
        }

        BigDecimal maxAmount = transactions.stream()
                .map(Transaction::amount)
                .filter(a -> a != null)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        double rawValue = maxAmount.doubleValue();
        double normalizedScore = Math.min(rawValue / config.getHighValueThreshold(), 1.0);
        double weightedContribution = normalizedScore * config.getHighValueWeight();
        String explanation = String.format(
                "Largest single transaction: %.2f (threshold: %.0f)",
                rawValue, config.getHighValueThreshold());

        return new FraudSignalAssessment(
                FraudSignalType.HIGH_VALUE,
                rawValue,
                normalizedScore,
                config.getHighValueWeight(),
                weightedContribution,
                explanation
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateTimeWindowInputs(List<Transaction> transactions, Instant referenceTime) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transactions list must not be null");
        }
        if (referenceTime == null) {
            throw new IllegalArgumentException("Reference time must not be null");
        }
    }

    private Optional<Instant> parseTimestamp(String transactionId, String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            log.warn("Transaction [{}] has a blank or null timestamp; excluded from time-windowed signals",
                    transactionId);
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(timestamp));
        } catch (DateTimeParseException e) {
            log.warn("Transaction [{}] has an unparseable timestamp '{}'; excluded from time-windowed signals",
                    transactionId, timestamp);
            return Optional.empty();
        }
    }

    private FraudSignalAssessment zeroSignal(FraudSignalType type, double weight, String explanation) {
        return new FraudSignalAssessment(type, 0.0, 0.0, weight, 0.0, explanation);
    }
}

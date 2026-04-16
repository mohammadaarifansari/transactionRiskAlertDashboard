package com.riskdashboard.service;

import com.riskdashboard.model.HourlyRiskWindow;
import com.riskdashboard.model.TransactionData.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates transaction data into hourly risk windows for the 24-hour activity timeline.
 *
 * <p>Windows are aligned to hour boundaries and ordered oldest-first. Each window
 * covers the half-open interval {@code [hourStart, hourEnd)}. Transactions with
 * malformed or missing timestamps are excluded with a warning log and never cause a
 * full aggregation failure.</p>
 *
 * <p>{@link HourlyRiskWindow#windowRiskScore()} is derived from the average
 * pre-computed {@code riskScore} of transactions in the window, normalised to [0.0, 1.0].
 * When no transactions carry a {@code riskScore}, the window score is {@code 0.0}.</p>
 */
public class HourlyWindowAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(HourlyWindowAggregatorService.class);

    private final RiskScoringConfig config;

    public HourlyWindowAggregatorService(RiskScoringConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("RiskScoringConfig must not be null");
        }
        this.config = config;
    }

    /**
     * Produces hourly window aggregations for the rolling look-back period.
     *
     * <p>Windows are aligned to the ceiling of the current hour derived from
     * {@code referenceTime}. The first window in the returned list is the oldest.</p>
     *
     * @param transactions  non-null transaction list for one account
     * @param referenceTime the instant representing "now"
     * @param hours         how many rolling hours to include; must be &gt; 0 and is capped at 24
     * @return an ordered, unmodifiable list of hourly windows (oldest first); never null
     * @throws IllegalArgumentException if {@code transactions} or {@code referenceTime} is null,
     *                                  or {@code hours} is not positive
     */
    public List<HourlyRiskWindow> aggregate(List<Transaction> transactions,
                                             Instant referenceTime,
                                             int hours) {
        if (transactions == null) {
            throw new IllegalArgumentException("Transactions list must not be null");
        }
        if (referenceTime == null) {
            throw new IllegalArgumentException("Reference time must not be null");
        }
        if (hours <= 0) {
            throw new IllegalArgumentException("Hours must be greater than zero");
        }

        int effectiveHours = Math.min(hours, 24);
        // Align to current-hour ceiling so the most recent window always covers the active hour
        Instant windowCeiling = referenceTime.truncatedTo(ChronoUnit.HOURS).plusSeconds(3600);

        List<HourlyRiskWindow> windows = new ArrayList<>(effectiveHours);
        for (int i = effectiveHours - 1; i >= 0; i--) {
            Instant hourEnd = windowCeiling.minusSeconds((long) i * 3600);
            Instant hourStart = hourEnd.minusSeconds(3600);
            windows.add(buildWindow(transactions, hourStart, hourEnd));
        }

        return Collections.unmodifiableList(windows);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private HourlyRiskWindow buildWindow(List<Transaction> transactions,
                                          Instant hourStart,
                                          Instant hourEnd) {
        List<Transaction> inWindow = new ArrayList<>();
        for (Transaction tx : transactions) {
            Optional<Instant> parsed = parseTimestamp(tx.transactionId(), tx.timestamp());
            if (parsed.isPresent()) {
                Instant ts = parsed.get();
                if (!ts.isBefore(hourStart) && ts.isBefore(hourEnd)) {
                    inWindow.add(tx);
                }
            }
        }

        int count = inWindow.size();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Transaction tx : inWindow) {
            if (tx.amount() != null) {
                totalAmount = totalAmount.add(tx.amount());
            }
        }

        BigDecimal averageAmount = count > 0
                ? totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : null;

        double windowRiskScore = computeWindowRiskScore(inWindow);
        boolean elevatedSuspicion = windowRiskScore > config.getElevatedSuspicionThreshold();

        return new HourlyRiskWindow(
                hourStart.toString(),
                count,
                totalAmount,
                averageAmount,
                windowRiskScore,
                elevatedSuspicion
        );
    }

    /**
     * Derives a normalised window score from the average pre-computed transaction risk scores.
     * Returns {@code 0.0} when no transactions are present or none carry a {@code riskScore}.
     */
    private double computeWindowRiskScore(List<Transaction> windowTransactions) {
        if (windowTransactions.isEmpty()) {
            return 0.0;
        }
        List<Integer> scores = new ArrayList<>();
        for (Transaction tx : windowTransactions) {
            if (tx.riskScore() != null) {
                scores.add(tx.riskScore());
            }
        }
        if (scores.isEmpty()) {
            return 0.0;
        }
        double average = scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        return Math.min(average / 100.0, 1.0);
    }

    private Optional<Instant> parseTimestamp(String transactionId, String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            log.warn("Transaction [{}] has a blank or null timestamp; excluded from hourly window",
                    transactionId);
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(timestamp));
        } catch (DateTimeParseException e) {
            log.warn("Transaction [{}] has an unparseable timestamp '{}'; excluded from hourly window",
                    transactionId, timestamp);
            return Optional.empty();
        }
    }
}

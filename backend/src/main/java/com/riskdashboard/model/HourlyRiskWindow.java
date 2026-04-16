package com.riskdashboard.model;

import java.math.BigDecimal;

/**
 * An aggregated hourly bucket in the 24-hour transaction timeline.
 *
 * <p>{@code averageAmount} is {@code null} when the window contains no transactions.
 * {@code windowRiskScore} is in [0.0, 1.0], derived from the average pre-computed
 * risk score of transactions that fall in the window.</p>
 *
 * @param hourStart          ISO 8601 timestamp marking the start of the one-hour bucket
 * @param transactionCount   number of transactions in the window
 * @param totalAmount        sum of all transaction amounts in the window
 * @param averageAmount      average transaction amount, or {@code null} when count is zero
 * @param windowRiskScore    aggregated risk score for the window in [0.0, 1.0]
 * @param elevatedSuspicion  {@code true} when {@code windowRiskScore} exceeds the configured threshold
 */
public record HourlyRiskWindow(
        String hourStart,
        int transactionCount,
        BigDecimal totalAmount,
        BigDecimal averageAmount,
        double windowRiskScore,
        boolean elevatedSuspicion
) {}

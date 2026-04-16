package com.riskdashboard.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Namespace class holding all core domain records for the transaction risk dashboard.
 *
 * <p>Records reflect the flat JSON data format where transactions are the primary data source.
 * {@link Account} instances are derived from unique {@code accountId}/{@code customerName}
 * pairs found within the transaction list.</p>
 */
public final class TransactionData {

    private TransactionData() {
        // Utility namespace — not instantiable
    }

    /**
     * Represents a customer account derived from transaction data.
     *
     * <p>Required fields: {@code accountId}, {@code customerName}.</p>
     */
    public record Account(
            String accountId,
            String customerName
    ) {}

    /**
     * Represents an individual financial transaction.
     *
     * <p>Required fields: {@code transactionId}, {@code accountId}, {@code customerName},
     * {@code timestamp}, {@code amount}, {@code currency}, {@code merchantCategory}.</p>
     *
     * <p>{@code amount} uses {@link BigDecimal} for decimal-safe monetary representation.
     * {@code riskScore} carries the raw pre-computed score (0–100) when present.
     * {@code fraudIndicators} lists signal type labels; empty list when none are present.</p>
     */
    public record Transaction(
            String transactionId,
            String accountId,
            String customerName,
            String timestamp,
            BigDecimal amount,
            String currency,
            String merchantName,
            String merchantCategory,
            String originCountry,
            String destinationCountry,
            Integer riskScore,
            List<String> fraudIndicators,
            String status
    ) {}

    /**
     * Top-level container produced by loading and parsing the flat transaction JSON array.
     * Accounts are synthesised from unique {@code accountId}/{@code customerName} pairs.
     */
    public record DataFile(
            List<Account> accounts,
            List<Transaction> transactions
    ) {}
}

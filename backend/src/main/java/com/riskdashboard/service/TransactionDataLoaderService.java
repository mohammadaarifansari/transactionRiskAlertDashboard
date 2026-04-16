package com.riskdashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskdashboard.model.TransactionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and validates {@code data/transactions.json}, returning a normalized
 * {@link TransactionData.DataFile}.
 *
 * <p>The data file must be a JSON array of flat transaction objects.
 * {@link TransactionData.Account} instances are derived from unique
 * {@code accountId}/{@code customerName} pairs within the array.
 * Malformed individual records are skipped with a warning log and never cause a full load
 * failure. A failure to read or parse the file itself throws {@link DataLoadException}.</p>
 *
 * <p>Callers must supply a configured {@link ObjectMapper}. Constructor injection is required.</p>
 */
public class TransactionDataLoaderService {

    private static final Logger log = LoggerFactory.getLogger(TransactionDataLoaderService.class);

    private final ObjectMapper objectMapper;

    public TransactionDataLoaderService(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    /**
     * Loads and validates transaction data from the given {@link InputStream}.
     *
     * @param inputStream non-null JSON stream containing a flat array of transaction objects
     * @return validated {@link TransactionData.DataFile} with derived accounts
     * @throws IllegalArgumentException if {@code inputStream} is null
     * @throws DataLoadException        if the stream cannot be read, is not valid JSON,
     *                                  or the root element is not a JSON array
     */
    public TransactionData.DataFile load(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream must not be null");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(inputStream);
        } catch (IOException e) {
            throw new DataLoadException("Failed to parse JSON data file: " + e.getMessage(), e);
        }

        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new DataLoadException("Data file is empty or contains a null root");
        }

        if (!root.isArray()) {
            throw new DataLoadException("Data file root must be a JSON array of transaction objects");
        }

        List<TransactionData.Transaction> transactions = parseTransactions(root);
        List<TransactionData.Account> accounts = deriveAccounts(transactions);

        log.info("Loaded {} transaction(s), derived {} unique account(s)",
                transactions.size(), accounts.size());

        return new TransactionData.DataFile(accounts, transactions);
    }

    /**
     * Loads and validates transaction data from a classpath resource.
     *
     * @param resourcePath classpath-relative path (e.g. {@code "data/transactions.json"})
     * @return validated {@link TransactionData.DataFile}
     * @throws DataLoadException if the resource is not found or cannot be parsed
     */
    public TransactionData.DataFile loadFromClasspath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("Resource path must not be blank");
        }

        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new DataLoadException("Data file not found on classpath: " + resourcePath);
        }

        return load(stream);
    }

    // -------------------------------------------------------------------------
    // Private parsing helpers
    // -------------------------------------------------------------------------

    private List<TransactionData.Transaction> parseTransactions(JsonNode arrayNode) {
        List<TransactionData.Transaction> result = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            try {
                result.add(parseTransaction(node));
            } catch (InvalidRecordException e) {
                log.warn("Skipping malformed transaction record [{}]: {}",
                        summarise(node), e.getMessage());
            }
        }
        return result;
    }

    private TransactionData.Transaction parseTransaction(JsonNode node) {
        String transactionId = requireNonBlank(node, "transactionId");
        String accountId = requireNonBlank(node, "accountId");
        String customerName = requireNonBlank(node, "customerName");
        String timestamp = requireNonBlank(node, "timestamp");
        BigDecimal amount = requireDecimal(node, "amount");
        String currency = requireNonBlank(node, "currency");
        String merchantCategory = requireNonBlank(node, "merchantCategory");

        return new TransactionData.Transaction(
                transactionId,
                accountId,
                customerName,
                timestamp,
                amount,
                currency,
                textOrNull(node, "merchantName"),
                merchantCategory,
                textOrNull(node, "originCountry"),
                textOrNull(node, "destinationCountry"),
                intOrNull(node, "riskScore"),
                stringList(node, "fraudIndicators"),
                textOrNull(node, "status")
        );
    }

    /**
     * Derives unique {@link TransactionData.Account} instances from the parsed transaction list.
     * Insertion order is preserved; when the same {@code accountId} appears multiple times,
     * the {@code customerName} from the first occurrence is used.
     */
    private List<TransactionData.Account> deriveAccounts(
            List<TransactionData.Transaction> transactions) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (TransactionData.Transaction tx : transactions) {
            seen.putIfAbsent(tx.accountId(), tx.customerName());
        }
        List<TransactionData.Account> accounts = new ArrayList<>();
        seen.forEach((id, name) -> accounts.add(new TransactionData.Account(id, name)));
        return Collections.unmodifiableList(accounts);
    }

    // -------------------------------------------------------------------------
    // Field extraction helpers
    // -------------------------------------------------------------------------

    private String requireNonBlank(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new InvalidRecordException("Required field missing: '" + field + "'");
        }
        String text = value.asText().trim();
        if (text.isBlank()) {
            throw new InvalidRecordException("Required field is blank: '" + field + "'");
        }
        return text;
    }

    /**
     * Extracts a {@link BigDecimal} from a numeric or string-valued JSON field.
     *
     * <p>Using {@code asText()} instead of {@code doubleValue()} avoids IEEE 754
     * floating-point precision loss for values like {@code 1299.99}.</p>
     */
    private BigDecimal requireDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new InvalidRecordException("Required field missing: '" + field + "'");
        }
        String text = value.asText("").trim();
        if (text.isBlank()) {
            throw new InvalidRecordException("Required field is blank: '" + field + "'");
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new InvalidRecordException(
                    "Field '" + field + "' is not a valid decimal number: '" + text + "'");
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            log.warn("Field '{}' is not numeric, ignoring value '{}'", field, value.asText());
            return null;
        }
        return value.intValue();
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isNull() && !item.isMissingNode()) {
                String text = item.asText().trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private String summarise(JsonNode node) {
        JsonNode txId = node.path("transactionId");
        if (!txId.isMissingNode() && !txId.isNull()) {
            return "transactionId=" + txId.asText();
        }
        JsonNode accId = node.path("accountId");
        if (!accId.isMissingNode() && !accId.isNull()) {
            return "accountId=" + accId.asText();
        }
        return "(unidentified record)";
    }

    // -------------------------------------------------------------------------
    // Exception types
    // -------------------------------------------------------------------------

    /**
     * Thrown when the data file cannot be found, read, or parsed as JSON.
     * Callers should treat this as a non-recoverable load failure.
     */
    public static class DataLoadException extends RuntimeException {
        public DataLoadException(String message) {
            super(message);
        }

        public DataLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Internal signal for a single malformed record. Always caught and logged;
     * never propagated to callers.
     */
    private static class InvalidRecordException extends RuntimeException {
        InvalidRecordException(String message) {
            super(message);
        }
    }
}

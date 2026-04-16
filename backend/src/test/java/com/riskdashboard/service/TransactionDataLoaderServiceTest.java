package com.riskdashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskdashboard.model.TransactionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TransactionDataLoaderService}.
 *
 * <p>Tests are written to drive the implementation (TDD). They cover:
 * <ul>
 *   <li>Valid full flat-array payload loading</li>
 *   <li>Account derivation from unique accountId/customerName pairs</li>
 *   <li>Empty array</li>
 *   <li>Missing required transaction fields per Blueprint</li>
 *   <li>Blank required fields</li>
 *   <li>Malformed decimal amount</li>
 *   <li>Mixed valid/invalid records — only valid ones returned</li>
 *   <li>Optional fields absent — null or empty list returned</li>
 *   <li>Null stream, malformed JSON, and non-array root error paths</li>
 *   <li>Classpath loading — file not found</li>
 *   <li>Classpath loading — sample data file present and valid</li>
 * </ul>
 * </p>
 */
class TransactionDataLoaderServiceTest {

    private TransactionDataLoaderService service;

    @BeforeEach
    void setUp() {
        service = new TransactionDataLoaderService(new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InputStream json(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static final String MINIMAL_TRANSACTION =
            """
            [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":100.00,"currency":"EUR","merchantCategory":"RETAIL"}]
            """;

    // -------------------------------------------------------------------------
    // Valid payload
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Valid payload")
    class ValidPayload {

        @Test
        @DisplayName("full record with all fields returns complete transaction and derived account")
        void fullRecord_returnsCompleteTransactionAndDerivedAccount() {
            String payload = """
                    [
                      {
                        "transactionId": "550e8400-e29b-41d4-a716-446655440001",
                        "accountId": "ACC-001",
                        "customerName": "Marcus Lindqvist",
                        "timestamp": "2026-04-11T08:14:22Z",
                        "amount": 4850.00,
                        "currency": "EUR",
                        "merchantName": "CryptoNow Exchange",
                        "merchantCategory": "CRYPTO_EXCHANGE",
                        "originCountry": "SE",
                        "destinationCountry": "MT",
                        "riskScore": 87,
                        "fraudIndicators": ["VELOCITY_ANOMALY", "UNUSUAL_MERCHANT"],
                        "status": "PENDING_REVIEW"
                      }
                    ]
                    """;

            TransactionData.DataFile result = service.load(json(payload));

            assertEquals(1, result.accounts().size());
            assertEquals(1, result.transactions().size());

            TransactionData.Account account = result.accounts().getFirst();
            assertEquals("ACC-001", account.accountId());
            assertEquals("Marcus Lindqvist", account.customerName());

            TransactionData.Transaction tx = result.transactions().getFirst();
            assertEquals("550e8400-e29b-41d4-a716-446655440001", tx.transactionId());
            assertEquals("ACC-001", tx.accountId());
            assertEquals("Marcus Lindqvist", tx.customerName());
            assertEquals("2026-04-11T08:14:22Z", tx.timestamp());
            assertEquals(0, new BigDecimal("4850.00").compareTo(tx.amount()));
            assertEquals("EUR", tx.currency());
            assertEquals("CryptoNow Exchange", tx.merchantName());
            assertEquals("CRYPTO_EXCHANGE", tx.merchantCategory());
            assertEquals("SE", tx.originCountry());
            assertEquals("MT", tx.destinationCountry());
            assertEquals(87, tx.riskScore());
            assertEquals(2, tx.fraudIndicators().size());
            assertEquals("VELOCITY_ANOMALY", tx.fraudIndicators().get(0));
            assertEquals("UNUSUAL_MERCHANT", tx.fraudIndicators().get(1));
            assertEquals("PENDING_REVIEW", tx.status());
        }

        @Test
        @DisplayName("multiple transactions are all returned")
        void multipleTransactions_allReturned() {
            String payload = """
                    [
                      {"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL"},
                      {"transactionId":"TXN002","accountId":"ACC-002","customerName":"Bob","timestamp":"2026-04-11T09:00:00Z","amount":200,"currency":"EUR","merchantCategory":"TRAVEL"},
                      {"transactionId":"TXN003","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T10:00:00Z","amount":75,"currency":"EUR","merchantCategory":"UTILITIES"}
                    ]
                    """;

            TransactionData.DataFile result = service.load(json(payload));

            assertEquals(3, result.transactions().size());
        }

        @Test
        @DisplayName("zero-value amount is accepted")
        void zeroAmount_isAccepted() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":0,"currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            TransactionData.DataFile result = service.load(json(payload));

            assertEquals(1, result.transactions().size());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.transactions().getFirst().amount()));
        }
    }

    // -------------------------------------------------------------------------
    // Empty arrays
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Empty array")
    class EmptyArray {

        @Test
        @DisplayName("empty JSON array returns empty accounts and transactions")
        void emptyArray_returnsEmptyLists() {
            TransactionData.DataFile result = service.load(json("[]"));

            assertTrue(result.accounts().isEmpty());
            assertTrue(result.transactions().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Account derivation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Account derivation")
    class AccountDerivation {

        @Test
        @DisplayName("unique accounts are derived from distinct accountId values")
        void uniqueAccounts_derivedFromTransactions() {
            String payload = """
                    [
                      {"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL"},
                      {"transactionId":"TXN002","accountId":"ACC-002","customerName":"Bob","timestamp":"2026-04-11T09:00:00Z","amount":100,"currency":"EUR","merchantCategory":"TRAVEL"},
                      {"transactionId":"TXN003","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T10:00:00Z","amount":75,"currency":"EUR","merchantCategory":"RETAIL"}
                    ]
                    """;

            TransactionData.DataFile result = service.load(json(payload));

            assertEquals(2, result.accounts().size());
            assertEquals("ACC-001", result.accounts().get(0).accountId());
            assertEquals("Alice", result.accounts().get(0).customerName());
            assertEquals("ACC-002", result.accounts().get(1).accountId());
        }

        @Test
        @DisplayName("duplicate accountId uses customerName from first occurrence")
        void duplicateAccountId_usesFirstCustomerName() {
            String payload = """
                    [
                      {"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL"},
                      {"transactionId":"TXN002","accountId":"ACC-001","customerName":"Alice Updated","timestamp":"2026-04-11T09:00:00Z","amount":100,"currency":"EUR","merchantCategory":"RETAIL"}
                    ]
                    """;

            TransactionData.DataFile result = service.load(json(payload));

            assertEquals(1, result.accounts().size());
            assertEquals("Alice", result.accounts().getFirst().customerName());
        }

        @Test
        @DisplayName("account insertion order matches first appearance in transaction array")
        void accountOrder_preservesInsertionOrder() {
            String payload = """
                    [
                      {"transactionId":"TXN001","accountId":"ACC-003","customerName":"Carol","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL"},
                      {"transactionId":"TXN002","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T09:00:00Z","amount":100,"currency":"EUR","merchantCategory":"RETAIL"},
                      {"transactionId":"TXN003","accountId":"ACC-002","customerName":"Bob","timestamp":"2026-04-11T10:00:00Z","amount":200,"currency":"EUR","merchantCategory":"RETAIL"}
                    ]
                    """;

            TransactionData.DataFile result = service.load(json(payload));

            assertEquals("ACC-003", result.accounts().get(0).accountId());
            assertEquals("ACC-001", result.accounts().get(1).accountId());
            assertEquals("ACC-002", result.accounts().get(2).accountId());
        }
    }

    // -------------------------------------------------------------------------
    // Missing required fields — transaction
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Transaction missing required fields")
    class TransactionRequiredFields {

        @Test
        @DisplayName("missing transactionId skips the record")
        void missingTransactionId_skipsRecord() {
            String payload = """
                    [{"accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":100,"currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }

        @Test
        @DisplayName("missing accountId skips the record")
        void missingAccountId_skipsRecord() {
            String payload = """
                    [{"transactionId":"TXN001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":100,"currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }

        @Test
        @DisplayName("missing customerName skips the record")
        void missingCustomerName_skipsRecord() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","timestamp":"2026-04-11T08:00:00Z","amount":100,"currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }

        @Test
        @DisplayName("blank customerName skips the record")
        void blankCustomerName_skipsRecord() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"","timestamp":"2026-04-11T08:00:00Z","amount":100,"currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }

        @Test
        @DisplayName("missing timestamp skips the record")
        void missingTimestamp_skipsRecord() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","amount":100,"currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }

        @Test
        @DisplayName("missing amount skips the record")
        void missingAmount_skipsRecord() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }

        @Test
        @DisplayName("missing currency skips the record")
        void missingCurrency_skipsRecord() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":100,"merchantCategory":"RETAIL"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }

        @Test
        @DisplayName("missing merchantCategory skips the record")
        void missingMerchantCategory_skipsRecord() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":100,"currency":"EUR"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }

        @Test
        @DisplayName("non-numeric amount string skips the record")
        void nonNumericAmount_skipsRecord() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":"not-a-number","currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            assertTrue(service.load(json(payload)).transactions().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Optional fields
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Optional fields")
    class OptionalFields {

        @Test
        @DisplayName("absent optional transaction fields return null")
        void absentOptionalTransactionFields_returnNull() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            TransactionData.Transaction tx = service.load(json(payload)).transactions().getFirst();

            assertNull(tx.merchantName());
            assertNull(tx.originCountry());
            assertNull(tx.destinationCountry());
            assertNull(tx.riskScore());
            assertNull(tx.status());
        }

        @Test
        @DisplayName("absent fraudIndicators returns empty list (not null)")
        void absentFraudIndicators_returnsEmptyList() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL"}]
                    """;

            TransactionData.Transaction tx = service.load(json(payload)).transactions().getFirst();

            assertNotNull(tx.fraudIndicators());
            assertTrue(tx.fraudIndicators().isEmpty());
        }

        @Test
        @DisplayName("empty fraudIndicators array returns empty list")
        void emptyFraudIndicators_returnsEmptyList() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL","fraudIndicators":[]}]
                    """;

            assertTrue(service.load(json(payload)).transactions().getFirst().fraudIndicators().isEmpty());
        }

        @Test
        @DisplayName("present fraudIndicators are parsed into ordered list")
        void presentFraudIndicators_parsedCorrectly() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL","fraudIndicators":["VELOCITY_ANOMALY","HIGH_VALUE_SPIKE"]}]
                    """;

            TransactionData.Transaction tx = service.load(json(payload)).transactions().getFirst();

            assertEquals(2, tx.fraudIndicators().size());
            assertEquals("VELOCITY_ANOMALY", tx.fraudIndicators().get(0));
            assertEquals("HIGH_VALUE_SPIKE", tx.fraudIndicators().get(1));
        }

        @Test
        @DisplayName("present riskScore is parsed as integer")
        void presentRiskScore_parsedAsInteger() {
            String payload = """
                    [{"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":50,"currency":"EUR","merchantCategory":"RETAIL","riskScore":92}]
                    """;

            assertEquals(92, service.load(json(payload)).transactions().getFirst().riskScore());
        }
    }

    // -------------------------------------------------------------------------
    // Mixed valid/invalid records
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Mixed valid and invalid records")
    class MixedRecords {

        @Test
        @DisplayName("only valid records are returned when invalid ones are interspersed")
        void mixedTransactions_returnsOnlyValidOnes() {
            String payload = """
                    [
                      {"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":100,"currency":"EUR","merchantCategory":"RETAIL"},
                      {"accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T09:00:00Z","amount":200,"currency":"EUR","merchantCategory":"RETAIL"},
                      {"transactionId":"TXN003","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T10:00:00Z","amount":300,"currency":"EUR","merchantCategory":"RETAIL"}
                    ]
                    """;

            TransactionData.DataFile result = service.load(json(payload));

            assertEquals(2, result.transactions().size());
            assertEquals("TXN001", result.transactions().get(0).transactionId());
            assertEquals("TXN003", result.transactions().get(1).transactionId());
        }

        @Test
        @DisplayName("only valid records contribute to derived accounts")
        void invalidRecords_doNotContributeToAccounts() {
            String payload = """
                    [
                      {"transactionId":"TXN001","accountId":"ACC-001","customerName":"Alice","timestamp":"2026-04-11T08:00:00Z","amount":100,"currency":"EUR","merchantCategory":"RETAIL"},
                      {"transactionId":"TXN002","accountId":"ACC-002","timestamp":"2026-04-11T09:00:00Z","amount":200,"currency":"EUR","merchantCategory":"RETAIL"}
                    ]
                    """;

            TransactionData.DataFile result = service.load(json(payload));

            assertEquals(1, result.accounts().size());
            assertEquals("ACC-001", result.accounts().getFirst().accountId());
        }
    }

    // -------------------------------------------------------------------------
    // Missing top-level keys
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Error paths")
    class ErrorPaths {

        @Test
        @DisplayName("null input stream throws IllegalArgumentException")
        void nullInputStream_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> service.load(null));
        }

        @Test
        @DisplayName("malformed JSON throws DataLoadException")
        void malformedJson_throwsDataLoadException() {
            InputStream malformed = json("{ this is : not valid json }");
            assertThrows(TransactionDataLoaderService.DataLoadException.class,
                    () -> service.load(malformed));
        }

        @Test
        @DisplayName("non-array root JSON object throws DataLoadException")
        void nonArrayRoot_throwsDataLoadException() {
            InputStream objectRoot = json("{\"key\":\"value\"}");
            assertThrows(TransactionDataLoaderService.DataLoadException.class,
                    () -> service.load(objectRoot));
        }

        @Test
        @DisplayName("classpath resource not found throws DataLoadException")
        void classpathFileNotFound_throwsDataLoadException() {
            assertThrows(TransactionDataLoaderService.DataLoadException.class,
                    () -> service.loadFromClasspath("data/nonexistent-file.json"));
        }

        @Test
        @DisplayName("blank resource path throws IllegalArgumentException")
        void blankResourcePath_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.loadFromClasspath("   "));
        }
    }

    // -------------------------------------------------------------------------
    // Classpath integration — sample data file
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Classpath loading — sample data")
    class ClasspathLoading {

        @Test
        @DisplayName("sample data/transactions.json loads with 40 transactions and 10 derived accounts")
        void sampleDataFile_loadsSuccessfully() {
            TransactionData.DataFile result = service.loadFromClasspath("data/transactions.json");

            assertFalse(result.transactions().isEmpty(),
                    "Expected transactions in sample data/transactions.json");
            assertFalse(result.accounts().isEmpty(),
                    "Expected derived accounts in sample data/transactions.json");
            assertEquals(40, result.transactions().size());
            assertEquals(10, result.accounts().size());
        }

        @Test
        @DisplayName("all derived accounts have non-blank required fields")
        void sampleDataAccounts_haveRequiredFields() {
            TransactionData.DataFile result = service.loadFromClasspath("data/transactions.json");

            for (TransactionData.Account account : result.accounts()) {
                assertNotNull(account.accountId(), "accountId must not be null");
                assertFalse(account.accountId().isBlank(), "accountId must not be blank");
                assertNotNull(account.customerName(), "customerName must not be null");
                assertFalse(account.customerName().isBlank(), "customerName must not be blank");
            }
        }

        @Test
        @DisplayName("all transactions from sample data have required fields and non-negative amounts")
        void sampleDataTransactions_haveRequiredFields() {
            TransactionData.DataFile result = service.loadFromClasspath("data/transactions.json");

            for (TransactionData.Transaction tx : result.transactions()) {
                assertNotNull(tx.transactionId(), "transactionId must not be null");
                assertFalse(tx.transactionId().isBlank(), "transactionId must not be blank");
                assertNotNull(tx.accountId(), "accountId must not be null");
                assertNotNull(tx.customerName(), "customerName must not be null");
                assertFalse(tx.customerName().isBlank(), "customerName must not be blank");
                assertNotNull(tx.timestamp(), "timestamp must not be null");
                assertNotNull(tx.amount(), "amount must not be null");
                assertTrue(tx.amount().compareTo(BigDecimal.ZERO) >= 0, "amount must be non-negative");
                assertNotNull(tx.currency(), "currency must not be null");
                assertNotNull(tx.merchantCategory(), "merchantCategory must not be null");
                assertNotNull(tx.fraudIndicators(), "fraudIndicators must not be null");
            }
        }

        @Test
        @DisplayName("high-risk transactions in sample data have non-empty fraudIndicators")
        void highRiskTransactions_haveFraudIndicators() {
            TransactionData.DataFile result = service.loadFromClasspath("data/transactions.json");

            long highRiskWithIndicators = result.transactions().stream()
                    .filter(tx -> tx.riskScore() != null && tx.riskScore() >= 70)
                    .filter(tx -> !tx.fraudIndicators().isEmpty())
                    .count();

            assertTrue(highRiskWithIndicators > 0,
                    "Expected at least one high-risk transaction with fraud indicators");
        }
    }
}

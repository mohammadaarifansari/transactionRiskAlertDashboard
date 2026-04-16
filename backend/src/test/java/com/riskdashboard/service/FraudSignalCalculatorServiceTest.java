package com.riskdashboard.service;

import com.riskdashboard.model.FraudSignalAssessment;
import com.riskdashboard.model.FraudSignalType;
import com.riskdashboard.model.TransactionData.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FraudSignalCalculatorService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>VELOCITY: null inputs, empty list, count within window, cap at 1.0, malformed timestamps</li>
 *   <li>GEO: null inputs, empty list, distinct-country counting, cap at 1.0</li>
 *   <li>MCC: null input, empty list, no high-risk categories, all high-risk, mixed ratio</li>
 *   <li>HIGH_VALUE: null input, empty list, below threshold, above threshold, cap at 1.0</li>
 *   <li>Signal output fields: type, weight, explanation non-blank</li>
 * </ul>
 * </p>
 */
class FraudSignalCalculatorServiceTest {

    /** Fixed "now" for all time-windowed tests: 2026-04-11T10:00:00Z */
    private static final Instant REF = Instant.parse("2026-04-11T10:00:00Z");

    private RiskScoringConfig config;
    private FraudSignalCalculatorService service;

    @BeforeEach
    void setUp() {
        config = new RiskScoringConfig();
        service = new FraudSignalCalculatorService(config);
    }

    // -------------------------------------------------------------------------
    // Helper: minimal Transaction builder
    // -------------------------------------------------------------------------

    private Transaction tx(String id, String timestamp, BigDecimal amount,
                            String merchantCategory, String destinationCountry,
                            Integer riskScore) {
        return new Transaction(
                id, "ACC-TEST", "Test User",
                timestamp, amount, "EUR",
                null, merchantCategory,
                null, destinationCountry,
                riskScore, Collections.emptyList(), "APPROVED"
        );
    }

    private Transaction txAt(String timestamp) {
        return tx("TXN-" + timestamp, timestamp, BigDecimal.valueOf(100), "RETAIL", "FI", null);
    }

    // -------------------------------------------------------------------------
    // Constructor guard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("null config throws IllegalArgumentException")
    void nullConfig_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new FraudSignalCalculatorService(null));
    }

    // =========================================================================
    // VELOCITY signal
    // =========================================================================

    @Nested
    @DisplayName("Velocity signal")
    class VelocitySignal {

        @Test
        @DisplayName("null transactions throws IllegalArgumentException")
        void nullTransactions_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateVelocity(null, REF));
        }

        @Test
        @DisplayName("null referenceTime throws IllegalArgumentException")
        void nullReferenceTime_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateVelocity(List.of(), null));
        }

        @Test
        @DisplayName("empty transaction list returns zero-scored signal")
        void emptyList_returnsZeroScore() {
            FraudSignalAssessment result = service.calculateVelocity(List.of(), REF);

            assertEquals(FraudSignalType.VELOCITY, result.signalType());
            assertEquals(0.0, result.rawValue());
            assertEquals(0.0, result.normalizedScore());
            assertEquals(0.0, result.weightedContribution());
            assertFalse(result.explanation().isBlank());
        }

        @Test
        @DisplayName("transaction outside 1-hour window is not counted")
        void transactionOutsideWindow_notCounted() {
            // 1h 1s before REF — just outside the 1-hour window
            Transaction outside = txAt("2026-04-11T08:59:59Z");
            FraudSignalAssessment result = service.calculateVelocity(List.of(outside), REF);

            assertEquals(0.0, result.rawValue());
            assertEquals(0.0, result.normalizedScore());
        }

        @Test
        @DisplayName("transaction exactly at window start is counted")
        void transactionAtWindowStart_isCounted() {
            // Exactly 1 hour before REF = T09:00:00Z
            Transaction atStart = txAt("2026-04-11T09:00:00Z");
            FraudSignalAssessment result = service.calculateVelocity(List.of(atStart), REF);

            assertEquals(1.0, result.rawValue());
        }

        @Test
        @DisplayName("transactions inside window are counted correctly")
        void transactionsInsideWindow_countedCorrectly() {
            // 3 transactions at T09:15, T09:30, T09:45 (all within last hour)
            List<Transaction> txns = List.of(
                    txAt("2026-04-11T09:15:00Z"),
                    txAt("2026-04-11T09:30:00Z"),
                    txAt("2026-04-11T09:45:00Z")
            );
            FraudSignalAssessment result = service.calculateVelocity(txns, REF);

            assertEquals(3.0, result.rawValue());
            // normalised = 3 / 5 = 0.6
            assertEquals(0.6, result.normalizedScore(), 1e-9);
            assertEquals(0.6 * RiskScoringConfig.DEFAULT_VELOCITY_WEIGHT,
                    result.weightedContribution(), 1e-9);
        }

        @Test
        @DisplayName("normalised score is capped at 1.0 when transactions exceed threshold")
        void exceedsThreshold_scoreIsCappedAtOne() {
            // 10 transactions in the last hour (threshold = 5)
            List<Transaction> txns = List.of(
                    txAt("2026-04-11T09:05:00Z"), txAt("2026-04-11T09:10:00Z"),
                    txAt("2026-04-11T09:15:00Z"), txAt("2026-04-11T09:20:00Z"),
                    txAt("2026-04-11T09:25:00Z"), txAt("2026-04-11T09:30:00Z"),
                    txAt("2026-04-11T09:35:00Z"), txAt("2026-04-11T09:40:00Z"),
                    txAt("2026-04-11T09:45:00Z"), txAt("2026-04-11T09:50:00Z")
            );
            FraudSignalAssessment result = service.calculateVelocity(txns, REF);

            assertEquals(1.0, result.normalizedScore());
            assertEquals(RiskScoringConfig.DEFAULT_VELOCITY_WEIGHT, result.weightedContribution(), 1e-9);
        }

        @Test
        @DisplayName("transaction with malformed timestamp is excluded and does not throw")
        void malformedTimestamp_excludedGracefully() {
            Transaction bad = tx("TXN-BAD", "not-a-timestamp", BigDecimal.TEN, "RETAIL", "FI", null);
            Transaction good = txAt("2026-04-11T09:30:00Z");

            FraudSignalAssessment result = service.calculateVelocity(List.of(bad, good), REF);

            assertEquals(1.0, result.rawValue(), "Only the valid timestamp transaction counted");
        }

        @Test
        @DisplayName("transaction with null timestamp is excluded and does not throw")
        void nullTimestamp_excludedGracefully() {
            Transaction nullTs = tx("TXN-NULL", null, BigDecimal.TEN, "RETAIL", "FI", null);

            FraudSignalAssessment result = service.calculateVelocity(List.of(nullTs), REF);
            assertEquals(0.0, result.rawValue());
        }

        @Test
        @DisplayName("signal type and weight are correct")
        void signalTypeAndWeight_areCorrect() {
            FraudSignalAssessment result = service.calculateVelocity(List.of(), REF);

            assertEquals(FraudSignalType.VELOCITY, result.signalType());
            assertEquals(RiskScoringConfig.DEFAULT_VELOCITY_WEIGHT, result.weight(), 1e-9);
        }
    }

    // =========================================================================
    // GEO signal
    // =========================================================================

    @Nested
    @DisplayName("Geo signal")
    class GeoSignal {

        @Test
        @DisplayName("null transactions throws IllegalArgumentException")
        void nullTransactions_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateGeo(null, REF));
        }

        @Test
        @DisplayName("null referenceTime throws IllegalArgumentException")
        void nullReferenceTime_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateGeo(List.of(), null));
        }

        @Test
        @DisplayName("empty transaction list returns zero-scored signal")
        void emptyList_returnsZeroScore() {
            FraudSignalAssessment result = service.calculateGeo(List.of(), REF);

            assertEquals(FraudSignalType.GEO, result.signalType());
            assertEquals(0.0, result.normalizedScore());
            assertEquals(0.0, result.weightedContribution());
        }

        @Test
        @DisplayName("transaction outside 24-hour window is not counted")
        void transactionOutside24HourWindow_notCounted() {
            // 24h 1s before REF
            Transaction outside = txGeo("2026-04-10T09:59:59Z", "MT");
            FraudSignalAssessment result = service.calculateGeo(List.of(outside), REF);

            assertEquals(0.0, result.rawValue());
        }

        @Test
        @DisplayName("single distinct destination country normalises correctly")
        void singleCountry_normalisesCorrectly() {
            Transaction t1 = txGeo("2026-04-11T08:00:00Z", "MT");
            Transaction t2 = txGeo("2026-04-11T09:00:00Z", "MT"); // same country, same window
            FraudSignalAssessment result = service.calculateGeo(List.of(t1, t2), REF);

            assertEquals(1.0, result.rawValue(), "One distinct country despite two transactions");
            // normalised = 1 / 4 = 0.25
            assertEquals(0.25, result.normalizedScore(), 1e-9);
        }

        @Test
        @DisplayName("multiple distinct countries are counted and score computed")
        void multipleCountries_scoreComputedCorrectly() {
            List<Transaction> txns = List.of(
                    txGeo("2026-04-11T07:00:00Z", "MT"),
                    txGeo("2026-04-11T08:00:00Z", "CY"),
                    txGeo("2026-04-11T09:00:00Z", "RU")
            );
            FraudSignalAssessment result = service.calculateGeo(txns, REF);

            assertEquals(3.0, result.rawValue());
            // normalised = 3 / 4 = 0.75
            assertEquals(0.75, result.normalizedScore(), 1e-9);
            assertEquals(0.75 * RiskScoringConfig.DEFAULT_GEO_WEIGHT,
                    result.weightedContribution(), 1e-9);
        }

        @Test
        @DisplayName("normalised score capped at 1.0 when distinct countries exceed threshold")
        void exceedsThreshold_scoreIsCappedAtOne() {
            List<Transaction> txns = List.of(
                    txGeo("2026-04-11T06:00:00Z", "MT"),
                    txGeo("2026-04-11T07:00:00Z", "CY"),
                    txGeo("2026-04-11T08:00:00Z", "RU"),
                    txGeo("2026-04-11T09:00:00Z", "AE"),
                    txGeo("2026-04-11T09:30:00Z", "BZ") // 5 > threshold of 4
            );
            FraudSignalAssessment result = service.calculateGeo(txns, REF);

            assertEquals(1.0, result.normalizedScore());
        }

        @Test
        @DisplayName("transaction with null destinationCountry is treated as no country")
        void nullDestinationCountry_notCounted() {
            Transaction noCountry = tx("TXN-NC", "2026-04-11T09:00:00Z",
                    BigDecimal.TEN, "RETAIL", null, null);
            FraudSignalAssessment result = service.calculateGeo(List.of(noCountry), REF);

            assertEquals(0.0, result.rawValue());
        }

        @Test
        @DisplayName("country matching is case-insensitive")
        void countryMatching_isCaseInsensitive() {
            Transaction t1 = txGeo("2026-04-11T08:00:00Z", "mt");
            Transaction t2 = txGeo("2026-04-11T09:00:00Z", "MT");
            FraudSignalAssessment result = service.calculateGeo(List.of(t1, t2), REF);

            assertEquals(1.0, result.rawValue(), "mt and MT should be the same country");
        }

        private Transaction txGeo(String timestamp, String destinationCountry) {
            return tx("TXN-" + timestamp, timestamp, BigDecimal.TEN, "RETAIL",
                    destinationCountry, null);
        }
    }

    // =========================================================================
    // MCC signal
    // =========================================================================

    @Nested
    @DisplayName("MCC signal")
    class MccSignal {

        @Test
        @DisplayName("null transactions throws IllegalArgumentException")
        void nullTransactions_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateMcc(null));
        }

        @Test
        @DisplayName("empty transaction list returns zero-scored signal")
        void emptyList_returnsZeroScore() {
            FraudSignalAssessment result = service.calculateMcc(List.of());

            assertEquals(FraudSignalType.MCC, result.signalType());
            assertEquals(0.0, result.normalizedScore());
            assertEquals(0.0, result.weightedContribution());
            assertFalse(result.explanation().isBlank());
        }

        @Test
        @DisplayName("no high-risk merchant categories returns zero score")
        void noHighRiskCategories_returnsZeroScore() {
            List<Transaction> txns = List.of(
                    txMcc("RETAIL"),
                    txMcc("GROCERY"),
                    txMcc("ATM")
            );
            FraudSignalAssessment result = service.calculateMcc(txns);

            assertEquals(0.0, result.normalizedScore());
        }

        @Test
        @DisplayName("all transactions in high-risk categories returns score 1.0")
        void allHighRiskCategories_returnsMaxScore() {
            List<Transaction> txns = List.of(
                    txMcc("CRYPTO_EXCHANGE"),
                    txMcc("GAMBLING")
            );
            FraudSignalAssessment result = service.calculateMcc(txns);

            assertEquals(1.0, result.normalizedScore(), 1e-9);
            assertEquals(RiskScoringConfig.DEFAULT_MCC_WEIGHT, result.weightedContribution(), 1e-9);
        }

        @Test
        @DisplayName("mixed categories return proportional normalised score")
        void mixedCategories_returnProportionalScore() {
            List<Transaction> txns = List.of(
                    txMcc("CRYPTO_EXCHANGE"), // high-risk
                    txMcc("RETAIL"),          // low-risk
                    txMcc("GAMBLING"),        // high-risk
                    txMcc("GROCERY")          // low-risk
            );
            FraudSignalAssessment result = service.calculateMcc(txns);

            // 2 high-risk of 4 total = 0.5
            assertEquals(0.5, result.normalizedScore(), 1e-9);
            assertEquals(2.0, result.rawValue());
        }

        @Test
        @DisplayName("category matching is case-insensitive")
        void categoryMatching_isCaseInsensitive() {
            List<Transaction> txns = List.of(txMcc("crypto_exchange"));
            FraudSignalAssessment result = service.calculateMcc(txns);

            assertEquals(1.0, result.normalizedScore(), 1e-9);
        }

        @Test
        @DisplayName("transaction with null merchantCategory is not counted as high-risk")
        void nullMerchantCategory_notCountedAsHighRisk() {
            Transaction noCategory = tx("TXN-NC", "2026-04-11T09:00:00Z",
                    BigDecimal.TEN, null, null, null);
            FraudSignalAssessment result = service.calculateMcc(List.of(noCategory));

            assertEquals(0.0, result.rawValue());
        }

        @Test
        @DisplayName("explanation contains transaction counts")
        void explanation_containsTransactionCounts() {
            List<Transaction> txns = List.of(txMcc("GAMBLING"), txMcc("RETAIL"));
            FraudSignalAssessment result = service.calculateMcc(txns);

            assertTrue(result.explanation().contains("1"), "Should mention 1 high-risk tx");
            assertTrue(result.explanation().contains("2"), "Should mention total 2 txs");
        }

        private Transaction txMcc(String category) {
            return tx("TXN-" + category, "2026-04-11T09:00:00Z",
                    BigDecimal.TEN, category, "FI", null);
        }
    }

    // =========================================================================
    // HIGH_VALUE signal
    // =========================================================================

    @Nested
    @DisplayName("High-value signal")
    class HighValueSignal {

        @Test
        @DisplayName("null transactions throws IllegalArgumentException")
        void nullTransactions_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateHighValue(null));
        }

        @Test
        @DisplayName("empty transaction list returns zero-scored signal")
        void emptyList_returnsZeroScore() {
            FraudSignalAssessment result = service.calculateHighValue(List.of());

            assertEquals(FraudSignalType.HIGH_VALUE, result.signalType());
            assertEquals(0.0, result.normalizedScore());
            assertEquals(0.0, result.weightedContribution());
        }

        @Test
        @DisplayName("max amount below threshold normalises correctly")
        void belowThreshold_normalisesCorrectly() {
            // amount = 2500, threshold = 10000 → normalised = 0.25
            Transaction t = tx("TXN-1", "2026-04-11T09:00:00Z",
                    BigDecimal.valueOf(2500), "RETAIL", null, null);
            FraudSignalAssessment result = service.calculateHighValue(List.of(t));

            assertEquals(2500.0, result.rawValue(), 1e-9);
            assertEquals(0.25, result.normalizedScore(), 1e-9);
            assertEquals(0.25 * RiskScoringConfig.DEFAULT_HIGH_VALUE_WEIGHT,
                    result.weightedContribution(), 1e-9);
        }

        @Test
        @DisplayName("max amount exactly at threshold gives score 1.0")
        void exactThreshold_givesScoreOne() {
            Transaction t = tx("TXN-1", "2026-04-11T09:00:00Z",
                    BigDecimal.valueOf(10_000), "RETAIL", null, null);
            FraudSignalAssessment result = service.calculateHighValue(List.of(t));

            assertEquals(1.0, result.normalizedScore(), 1e-9);
        }

        @Test
        @DisplayName("max amount above threshold is capped at 1.0")
        void aboveThreshold_scoreIsCappedAtOne() {
            Transaction t = tx("TXN-1", "2026-04-11T09:00:00Z",
                    BigDecimal.valueOf(50_000), "RETAIL", null, null);
            FraudSignalAssessment result = service.calculateHighValue(List.of(t));

            assertEquals(1.0, result.normalizedScore());
            assertEquals(RiskScoringConfig.DEFAULT_HIGH_VALUE_WEIGHT,
                    result.weightedContribution(), 1e-9);
        }

        @Test
        @DisplayName("the maximum amount across multiple transactions is used")
        void multipleTransactions_maxAmountUsed() {
            List<Transaction> txns = List.of(
                    tx("TXN-1", "2026-04-11T08:00:00Z", BigDecimal.valueOf(500), "RETAIL", null, null),
                    tx("TXN-2", "2026-04-11T09:00:00Z", BigDecimal.valueOf(7500), "RETAIL", null, null),
                    tx("TXN-3", "2026-04-11T09:30:00Z", BigDecimal.valueOf(200), "RETAIL", null, null)
            );
            FraudSignalAssessment result = service.calculateHighValue(txns);

            assertEquals(7500.0, result.rawValue(), 1e-9);
            assertEquals(0.75, result.normalizedScore(), 1e-9);
        }

        @Test
        @DisplayName("transaction with null amount is ignored in max calculation")
        void nullAmount_isIgnored() {
            Transaction nullAmt = new Transaction(
                    "TXN-NULL", "ACC-TEST", "Test User",
                    "2026-04-11T09:00:00Z", null, "EUR",
                    null, "RETAIL", null, null, null,
                    Collections.emptyList(), "APPROVED");
            Transaction valid = tx("TXN-1", "2026-04-11T09:00:00Z",
                    BigDecimal.valueOf(1000), "RETAIL", null, null);

            FraudSignalAssessment result = service.calculateHighValue(List.of(nullAmt, valid));

            assertEquals(1000.0, result.rawValue(), 1e-9);
        }

        @Test
        @DisplayName("signal type and weight are correct")
        void signalTypeAndWeight_areCorrect() {
            FraudSignalAssessment result = service.calculateHighValue(List.of());

            assertEquals(FraudSignalType.HIGH_VALUE, result.signalType());
            assertEquals(RiskScoringConfig.DEFAULT_HIGH_VALUE_WEIGHT, result.weight(), 1e-9);
        }
    }

    // =========================================================================
    // Cross-signal: weighted contribution invariant
    // =========================================================================

    @Test
    @DisplayName("weightedContribution always equals normalizedScore times weight")
    void weightedContribution_alwaysEqualsNormalizedScoreTimesWeight() {
        List<Transaction> txns = List.of(
                tx("T1", "2026-04-11T09:30:00Z", BigDecimal.valueOf(4850),
                        "CRYPTO_EXCHANGE", "MT", 87)
        );

        FraudSignalAssessment vel = service.calculateVelocity(txns, REF);
        FraudSignalAssessment geo = service.calculateGeo(txns, REF);
        FraudSignalAssessment mcc = service.calculateMcc(txns);
        FraudSignalAssessment hv = service.calculateHighValue(txns);

        for (FraudSignalAssessment sig : List.of(vel, geo, mcc, hv)) {
            assertEquals(sig.normalizedScore() * sig.weight(), sig.weightedContribution(), 1e-9,
                    "Failed for signal: " + sig.signalType());
        }
    }
}

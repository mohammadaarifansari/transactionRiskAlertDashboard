package com.riskdashboard.service;

import com.riskdashboard.model.FraudSignalAssessment;
import com.riskdashboard.model.FraudSignalType;
import com.riskdashboard.model.RiskAssessment;
import com.riskdashboard.model.RiskTier;
import com.riskdashboard.model.TransactionData.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RiskScoringService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Input validation: null/blank accountId, null transactions, null referenceTime</li>
 *   <li>Empty account — GREEN tier, zero score</li>
 *   <li>GREEN, YELLOW, and RED tier scenarios</li>
 *   <li>Exact tier boundary transitions at 0.39/0.40 and 0.69/0.70</li>
 *   <li>Contributing signal ordering (highest weighted contribution first)</li>
 *   <li>Recommended action text for each tier</li>
 *   <li>Assessment timestamp equals referenceTime ISO string</li>
 *   <li>Determinism: identical inputs produce identical outputs</li>
 * </ul>
 * </p>
 */
class RiskScoringServiceTest {

    private static final Instant REF = Instant.parse("2026-04-11T10:00:00Z");

    private RiskScoringConfig defaultConfig;
    private FraudSignalCalculatorService signalCalculator;
    private RiskScoringService service;

    @BeforeEach
    void setUp() {
        defaultConfig = new RiskScoringConfig();
        signalCalculator = new FraudSignalCalculatorService(defaultConfig);
        service = new RiskScoringService(signalCalculator, defaultConfig);
    }

    // -------------------------------------------------------------------------
    // Helper: minimal Transaction builder
    // -------------------------------------------------------------------------

    private Transaction tx(String id, String timestamp, BigDecimal amount,
                            String merchantCategory, String destinationCountry) {
        return new Transaction(
                id, "ACC-TEST", "Test User",
                timestamp, amount, "EUR",
                null, merchantCategory,
                null, destinationCountry,
                null, Collections.emptyList(), "APPROVED"
        );
    }

    /**
     * Creates {@code n} transactions timestamped 30 minutes before REF.
     * Useful for controlling velocity count precisely.
     */
    private List<Transaction> nTransactionsInLastHour(int n, String category,
                                                       String destCountry,
                                                       BigDecimal amount) {
        var list = new ArrayList<Transaction>(n);
        for (int i = 0; i < n; i++) {
            list.add(tx("TXN-" + i, "2026-04-11T09:30:00Z", amount, category, destCountry));
        }
        return list;
    }

    // =========================================================================
    // Constructor guards
    // =========================================================================

    @Test
    @DisplayName("null signalCalculator throws IllegalArgumentException")
    void nullSignalCalculator_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RiskScoringService(null, defaultConfig));
    }

    @Test
    @DisplayName("null config throws IllegalArgumentException")
    void nullConfig_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RiskScoringService(signalCalculator, null));
    }

    // =========================================================================
    // Input validation
    // =========================================================================

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("null accountId throws IllegalArgumentException")
        void nullAccountId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.assess(null, List.of(), REF));
        }

        @Test
        @DisplayName("blank accountId throws IllegalArgumentException")
        void blankAccountId_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.assess("  ", List.of(), REF));
        }

        @Test
        @DisplayName("null transactions throws IllegalArgumentException")
        void nullTransactions_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.assess("ACC-001", null, REF));
        }

        @Test
        @DisplayName("null referenceTime throws IllegalArgumentException")
        void nullReferenceTime_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.assess("ACC-001", List.of(), null));
        }
    }

    // =========================================================================
    // Risk tiers
    // =========================================================================

    @Nested
    @DisplayName("Risk tier assignment")
    class RiskTierAssignment {

        @Test
        @DisplayName("empty transaction list produces GREEN tier with zero score")
        void emptyTransactions_greenTierZeroScore() {
            RiskAssessment result = service.assess("ACC-001", List.of(), REF);

            assertEquals(RiskTier.GREEN, result.riskTier());
            assertEquals(0.0, result.totalRiskScore());
            assertEquals("ACC-001", result.accountId());
            assertEquals(4, result.contributingSignals().size(), "All 4 signals must be present");
        }

        @Test
        @DisplayName("low-risk transactions produce GREEN tier")
        void lowRiskTransactions_greenTier() {
            // Single low-amount retail transaction outside velocity window
            List<Transaction> txns = List.of(
                    tx("TXN-1", "2026-04-10T08:00:00Z",
                            BigDecimal.valueOf(42.50), "RETAIL", "FI")
            );
            RiskAssessment result = service.assess("ACC-001", txns, REF);

            assertEquals(RiskTier.GREEN, result.riskTier());
            assertTrue(result.totalRiskScore() < 0.40);
        }

        @Test
        @DisplayName("moderate-risk transactions produce YELLOW tier")
        void moderateRiskTransactions_yellowTier() {
            // 2 transactions in last hour, 2 distinct countries, 1 high-risk category, moderate amount
            // Expected: vel=2/5=0.4→0.14, geo=2/4=0.5→0.125, mcc=1/2=0.5→0.075, hv=4000/10000=0.4→0.1
            // Total ≈ 0.44 → YELLOW
            List<Transaction> txns = List.of(
                    tx("TXN-1", "2026-04-11T09:30:00Z",
                            BigDecimal.valueOf(4000), "CRYPTO_EXCHANGE", "MT"),
                    tx("TXN-2", "2026-04-11T09:45:00Z",
                            BigDecimal.valueOf(200), "RETAIL", "CY")
            );
            RiskAssessment result = service.assess("ACC-001", txns, REF);

            assertEquals(RiskTier.YELLOW, result.riskTier());
            assertTrue(result.totalRiskScore() >= 0.40 && result.totalRiskScore() < 0.70);
        }

        @Test
        @DisplayName("high-risk transactions produce RED tier")
        void highRiskTransactions_redTier() {
            // 6 transactions in last hour (exceeds threshold), 4+ countries, all CRYPTO_EXCHANGE, huge amounts
            List<Transaction> txns = List.of(
                    tx("TXN-1", "2026-04-11T09:05:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "MT"),
                    tx("TXN-2", "2026-04-11T09:10:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "CY"),
                    tx("TXN-3", "2026-04-11T09:15:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "RU"),
                    tx("TXN-4", "2026-04-11T09:20:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "AE"),
                    tx("TXN-5", "2026-04-11T09:25:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "BZ"),
                    tx("TXN-6", "2026-04-11T09:30:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "SC")
            );
            RiskAssessment result = service.assess("ACC-001", txns, REF);

            assertEquals(RiskTier.RED, result.riskTier());
            assertEquals(1.0, result.totalRiskScore(), 1e-9);
        }
    }

    // =========================================================================
    // Tier boundary transitions
    // =========================================================================

    @Nested
    @DisplayName("Tier boundary transitions")
    class TierBoundaryTransitions {

        /**
         * Uses a custom config where only velocity weight is non-zero (weight=1.0),
         * threshold=100, so score = count/100 exactly and tier transitions are precise.
         */
        private RiskScoringService boundaryService(double yellowThreshold, double redThreshold) {
            RiskScoringConfig cfg = new RiskScoringConfig(
                    1.0, 0.0, 0.0, 0.0,
                    100, 4, 10_000.0,
                    yellowThreshold, redThreshold, 0.50,
                    Set.of()
            );
            return new RiskScoringService(new FraudSignalCalculatorService(cfg), cfg);
        }

        private List<Transaction> velocityTransactions(int count) {
            return nTransactionsInLastHour(count, "RETAIL", "FI", BigDecimal.TEN);
        }

        @Test
        @DisplayName("score 0.39 maps to GREEN, not YELLOW")
        void score039_isGreen() {
            RiskScoringService svc = boundaryService(0.40, 0.70);
            RiskAssessment result = svc.assess("ACC-001", velocityTransactions(39), REF);

            assertEquals(RiskTier.GREEN, result.riskTier());
            assertEquals(0.39, result.totalRiskScore(), 1e-9);
        }

        @Test
        @DisplayName("score 0.40 maps to YELLOW, not GREEN")
        void score040_isYellow() {
            RiskScoringService svc = boundaryService(0.40, 0.70);
            RiskAssessment result = svc.assess("ACC-001", velocityTransactions(40), REF);

            assertEquals(RiskTier.YELLOW, result.riskTier());
            assertEquals(0.40, result.totalRiskScore(), 1e-9);
        }

        @Test
        @DisplayName("score 0.69 maps to YELLOW, not RED")
        void score069_isYellow() {
            RiskScoringService svc = boundaryService(0.40, 0.70);
            RiskAssessment result = svc.assess("ACC-001", velocityTransactions(69), REF);

            assertEquals(RiskTier.YELLOW, result.riskTier());
            assertEquals(0.69, result.totalRiskScore(), 1e-9);
        }

        @Test
        @DisplayName("score 0.70 maps to RED, not YELLOW")
        void score070_isRed() {
            RiskScoringService svc = boundaryService(0.40, 0.70);
            RiskAssessment result = svc.assess("ACC-001", velocityTransactions(70), REF);

            assertEquals(RiskTier.RED, result.riskTier());
            assertEquals(0.70, result.totalRiskScore(), 1e-9);
        }
    }

    // =========================================================================
    // Recommended action
    // =========================================================================

    @Nested
    @DisplayName("Recommended action")
    class RecommendedAction {

        @Test
        @DisplayName("GREEN tier recommends Monitor")
        void greenTier_recommendsMonitor() {
            RiskAssessment result = service.assess("ACC-001", List.of(), REF);
            assertEquals("Monitor", result.recommendedAction());
        }

        @Test
        @DisplayName("YELLOW tier recommends Review")
        void yellowTier_recommendsReview() {
            List<Transaction> txns = List.of(
                    tx("TXN-1", "2026-04-11T09:30:00Z",
                            BigDecimal.valueOf(4000), "CRYPTO_EXCHANGE", "MT"),
                    tx("TXN-2", "2026-04-11T09:45:00Z",
                            BigDecimal.valueOf(200), "RETAIL", "CY")
            );
            RiskAssessment result = service.assess("ACC-001", txns, REF);
            // Verify we got YELLOW before checking the action
            assertEquals(RiskTier.YELLOW, result.riskTier());
            assertEquals("Review", result.recommendedAction());
        }

        @Test
        @DisplayName("RED tier recommends Block and escalate")
        void redTier_recommendsBlockAndEscalate() {
            List<Transaction> txns = List.of(
                    tx("TXN-1", "2026-04-11T09:05:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "MT"),
                    tx("TXN-2", "2026-04-11T09:10:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "CY"),
                    tx("TXN-3", "2026-04-11T09:15:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "RU"),
                    tx("TXN-4", "2026-04-11T09:20:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "AE"),
                    tx("TXN-5", "2026-04-11T09:25:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "BZ"),
                    tx("TXN-6", "2026-04-11T09:30:00Z", BigDecimal.valueOf(10_000), "CRYPTO_EXCHANGE", "SC")
            );
            RiskAssessment result = service.assess("ACC-001", txns, REF);
            assertEquals(RiskTier.RED, result.riskTier());
            assertEquals("Block and escalate", result.recommendedAction());
        }
    }

    // =========================================================================
    // Signal ordering
    // =========================================================================

    @Test
    @DisplayName("contributing signals are ordered by weightedContribution descending")
    void contributingSignals_orderedByWeightedContributionDescending() {
        List<Transaction> txns = List.of(
                tx("TXN-1", "2026-04-11T09:30:00Z",
                        BigDecimal.valueOf(4000), "CRYPTO_EXCHANGE", "MT")
        );
        RiskAssessment result = service.assess("ACC-001", txns, REF);

        List<FraudSignalAssessment> signals = result.contributingSignals();
        assertEquals(4, signals.size());
        for (int i = 0; i < signals.size() - 1; i++) {
            assertTrue(signals.get(i).weightedContribution()
                            >= signals.get(i + 1).weightedContribution(),
                    "Signal at index " + i + " should have contribution >= signal at " + (i + 1));
        }
    }

    @Test
    @DisplayName("all four signal types are present in the assessment")
    void allFourSignalTypes_presentInAssessment() {
        RiskAssessment result = service.assess("ACC-001", List.of(), REF);

        Set<FraudSignalType> types = new HashSet<>();
        result.contributingSignals().forEach(s -> types.add(s.signalType()));
        assertTrue(types.contains(FraudSignalType.VELOCITY));
        assertTrue(types.contains(FraudSignalType.GEO));
        assertTrue(types.contains(FraudSignalType.MCC));
        assertTrue(types.contains(FraudSignalType.HIGH_VALUE));
    }

    // =========================================================================
    // Assessment metadata
    // =========================================================================

    @Test
    @DisplayName("assessmentTimestamp equals referenceTime ISO string")
    void assessmentTimestamp_equalsReferenceTimeIso() {
        RiskAssessment result = service.assess("ACC-001", List.of(), REF);
        assertEquals(REF.toString(), result.assessmentTimestamp());
    }

    @Test
    @DisplayName("accountId is preserved in the returned assessment")
    void accountId_preservedInAssessment() {
        RiskAssessment result = service.assess("ACC-SPECIAL-99", List.of(), REF);
        assertEquals("ACC-SPECIAL-99", result.accountId());
    }

    @Test
    @DisplayName("totalRiskScore is clamped to 1.0 and never exceeds it")
    void totalRiskScore_neverExceedsOne() {
        List<Transaction> maxRisk = List.of(
                tx("TXN-1", "2026-04-11T09:05:00Z", BigDecimal.valueOf(100_000), "GAMBLING", "MT"),
                tx("TXN-2", "2026-04-11T09:10:00Z", BigDecimal.valueOf(100_000), "GAMBLING", "CY"),
                tx("TXN-3", "2026-04-11T09:15:00Z", BigDecimal.valueOf(100_000), "GAMBLING", "RU"),
                tx("TXN-4", "2026-04-11T09:20:00Z", BigDecimal.valueOf(100_000), "GAMBLING", "AE"),
                tx("TXN-5", "2026-04-11T09:25:00Z", BigDecimal.valueOf(100_000), "GAMBLING", "BZ"),
                tx("TXN-6", "2026-04-11T09:30:00Z", BigDecimal.valueOf(100_000), "GAMBLING", "SC")
        );
        RiskAssessment result = service.assess("ACC-001", maxRisk, REF);
        assertTrue(result.totalRiskScore() <= 1.0);
    }

    // =========================================================================
    // Determinism
    // =========================================================================

    @Test
    @DisplayName("same inputs always produce identical assessment (determinism)")
    void sameInputs_produceDeterministicResults() {
        List<Transaction> txns = List.of(
                tx("TXN-1", "2026-04-11T09:15:00Z",
                        BigDecimal.valueOf(4850), "CRYPTO_EXCHANGE", "MT"),
                tx("TXN-2", "2026-04-11T09:45:00Z",
                        BigDecimal.valueOf(200), "RETAIL", "FI")
        );

        RiskAssessment first = service.assess("ACC-001", txns, REF);
        RiskAssessment second = service.assess("ACC-001", txns, REF);

        assertEquals(first.totalRiskScore(), second.totalRiskScore(), 1e-15);
        assertEquals(first.riskTier(), second.riskTier());
        assertEquals(first.recommendedAction(), second.recommendedAction());
        assertEquals(first.assessmentTimestamp(), second.assessmentTimestamp());
    }
}

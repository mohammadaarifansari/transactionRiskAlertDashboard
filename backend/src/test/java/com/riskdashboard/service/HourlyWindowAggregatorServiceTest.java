package com.riskdashboard.service;

import com.riskdashboard.model.HourlyRiskWindow;
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
 * Unit tests for {@link HourlyWindowAggregatorService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Input validation: null transactions, null referenceTime, non-positive hours</li>
 *   <li>Window count: exact hours requested up to a cap of 24</li>
 *   <li>Window ordering: oldest window first</li>
 *   <li>Bucket assignment: transactions fall into the correct hourly slot</li>
 *   <li>Boundary: transaction at hourStart is included; transaction at hourEnd is not</li>
 *   <li>Empty windows: zero count, zero totalAmount, null averageAmount, zero score</li>
 *   <li>Amount aggregation: totalAmount and averageAmount computed correctly</li>
 *   <li>Window risk score: derived from average normalised transaction riskScore</li>
 *   <li>elevatedSuspicion: flag set correctly relative to configured threshold</li>
 *   <li>Malformed timestamps: excluded gracefully without throwing</li>
 * </ul>
 * </p>
 */
class HourlyWindowAggregatorServiceTest {

    /** Fixed "now": 2026-04-11T10:30:00Z — mid-hour to test window alignment */
    private static final Instant REF = Instant.parse("2026-04-11T10:30:00Z");

    /**
     * With REF=T10:30Z, truncatedTo(HOURS)=T10:00Z, windowCeiling=T11:00Z.
     * For hours=2: [T09:00-T10:00) is window[0], [T10:00-T11:00) is window[1].
     */

    private RiskScoringConfig config;
    private HourlyWindowAggregatorService service;

    @BeforeEach
    void setUp() {
        config = new RiskScoringConfig();
        service = new HourlyWindowAggregatorService(config);
    }

    // -------------------------------------------------------------------------
    // Helper: minimal Transaction builder
    // -------------------------------------------------------------------------

    private Transaction tx(String id, String timestamp, BigDecimal amount, Integer riskScore) {
        return new Transaction(
                id, "ACC-TEST", "Test User",
                timestamp, amount, "EUR",
                null, "RETAIL",
                null, "FI",
                riskScore, Collections.emptyList(), "APPROVED"
        );
    }

    private Transaction txAt(String timestamp) {
        return tx("TXN-" + timestamp, timestamp, BigDecimal.valueOf(100), null);
    }

    private Transaction txAt(String timestamp, BigDecimal amount) {
        return tx("TXN-" + timestamp, timestamp, amount, null);
    }

    private Transaction txAt(String timestamp, int riskScore) {
        return tx("TXN-" + timestamp, timestamp, BigDecimal.valueOf(100), riskScore);
    }

    // =========================================================================
    // Constructor guard
    // =========================================================================

    @Test
    @DisplayName("null config throws IllegalArgumentException")
    void nullConfig_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new HourlyWindowAggregatorService(null));
    }

    // =========================================================================
    // Input validation
    // =========================================================================

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("null transactions throws IllegalArgumentException")
        void nullTransactions_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.aggregate(null, REF, 24));
        }

        @Test
        @DisplayName("null referenceTime throws IllegalArgumentException")
        void nullReferenceTime_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.aggregate(List.of(), null, 24));
        }

        @Test
        @DisplayName("hours = 0 throws IllegalArgumentException")
        void zeroHours_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.aggregate(List.of(), REF, 0));
        }

        @Test
        @DisplayName("hours < 0 throws IllegalArgumentException")
        void negativeHours_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.aggregate(List.of(), REF, -1));
        }
    }

    // =========================================================================
    // Window count and cap
    // =========================================================================

    @Nested
    @DisplayName("Window count")
    class WindowCount {

        @Test
        @DisplayName("requesting 3 hours returns exactly 3 windows")
        void requestThreeHours_returnsThreeWindows() {
            List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 3);
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("requesting exactly 24 hours returns 24 windows")
        void request24Hours_returns24Windows() {
            List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 24);
            assertEquals(24, result.size());
        }

        @Test
        @DisplayName("requesting more than 24 hours is capped at 24 windows")
        void requestOver24Hours_cappedAt24() {
            List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 48);
            assertEquals(24, result.size());
        }

        @Test
        @DisplayName("requesting 1 hour returns exactly 1 window")
        void requestOneHour_returnsOneWindow() {
            List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 1);
            assertEquals(1, result.size());
        }
    }

    // =========================================================================
    // Window ordering
    // =========================================================================

    @Test
    @DisplayName("windows are ordered oldest first")
    void windows_orderedOldestFirst() {
        List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 3);

        // Window starts should be strictly ascending
        for (int i = 0; i < result.size() - 1; i++) {
            Instant current = Instant.parse(result.get(i).hourStart());
            Instant next = Instant.parse(result.get(i + 1).hourStart());
            assertTrue(current.isBefore(next),
                    "Window " + i + " should start before window " + (i + 1));
        }
    }

    @Test
    @DisplayName("last window hourStart is current hour aligned to floor")
    void lastWindow_hourStartIsCurrentHourFloor() {
        // windowCeiling = T11:00, last window (i=0): hourEnd=T11, hourStart=T10
        List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 2);

        HourlyRiskWindow lastWindow = result.get(result.size() - 1);
        assertEquals("2026-04-11T10:00:00Z", lastWindow.hourStart());
    }

    // =========================================================================
    // Bucket assignment
    // =========================================================================

    @Nested
    @DisplayName("Bucket assignment")
    class BucketAssignment {

        @Test
        @DisplayName("transaction at T09:15 falls in [T09:00, T10:00) window when hours=2")
        void transactionAtT0915_inFirstWindow() {
            Transaction t = txAt("2026-04-11T09:15:00Z");
            List<HourlyRiskWindow> result = service.aggregate(List.of(t), REF, 2);

            // Window[0] = [T09:00, T10:00)
            assertEquals(1, result.get(0).transactionCount());
            assertEquals(0, result.get(1).transactionCount());
        }

        @Test
        @DisplayName("transaction at T10:15 falls in [T10:00, T11:00) window when hours=2")
        void transactionAtT1015_inSecondWindow() {
            Transaction t = txAt("2026-04-11T10:15:00Z");
            List<HourlyRiskWindow> result = service.aggregate(List.of(t), REF, 2);

            // Window[1] = [T10:00, T11:00)
            assertEquals(0, result.get(0).transactionCount());
            assertEquals(1, result.get(1).transactionCount());
        }

        @Test
        @DisplayName("transaction at hourStart is included in that window (inclusive boundary)")
        void transactionAtHourStart_included() {
            // Window[1] starts at T10:00; a transaction at exactly T10:00 should be in it
            Transaction t = txAt("2026-04-11T10:00:00Z");
            List<HourlyRiskWindow> result = service.aggregate(List.of(t), REF, 2);

            assertEquals(1, result.get(1).transactionCount(), "T10:00:00 should be in [T10, T11)");
        }

        @Test
        @DisplayName("transaction at hourEnd is excluded from that window (exclusive boundary)")
        void transactionAtHourEnd_excluded() {
            // Window[1] ends at T11:00; a transaction at exactly T11:00 should NOT be in it
            Transaction t = txAt("2026-04-11T11:00:00Z");
            List<HourlyRiskWindow> result = service.aggregate(List.of(t), REF, 2);

            assertEquals(0, result.get(1).transactionCount(), "T11:00:00 should not be in [T10, T11)");
        }

        @Test
        @DisplayName("transaction outside all windows is not counted in any window")
        void transactionOutsideAllWindows_notCounted() {
            // Before the 2-hour window: prior to T09:00
            Transaction t = txAt("2026-04-11T08:59:59Z");
            List<HourlyRiskWindow> result = service.aggregate(List.of(t), REF, 2);

            assertEquals(0, result.get(0).transactionCount());
            assertEquals(0, result.get(1).transactionCount());
        }

        @Test
        @DisplayName("multiple transactions distributed correctly across windows")
        void multipleTransactions_distributedCorrectly() {
            List<Transaction> txns = List.of(
                    txAt("2026-04-11T09:10:00Z"),
                    txAt("2026-04-11T09:50:00Z"),
                    txAt("2026-04-11T10:05:00Z"),
                    txAt("2026-04-11T10:45:00Z"),
                    txAt("2026-04-11T10:55:00Z")
            );
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 2);

            assertEquals(2, result.get(0).transactionCount(), "T09 window");
            assertEquals(3, result.get(1).transactionCount(), "T10 window");
        }
    }

    // =========================================================================
    // Empty windows
    // =========================================================================

    @Test
    @DisplayName("empty transaction list produces windows with zero count and zero score")
    void emptyTransactions_windowsHaveZeroValues() {
        List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 3);

        for (HourlyRiskWindow window : result) {
            assertEquals(0, window.transactionCount());
            assertEquals(BigDecimal.ZERO, window.totalAmount());
            assertNull(window.averageAmount(), "averageAmount should be null for empty window");
            assertEquals(0.0, window.windowRiskScore());
            assertFalse(window.elevatedSuspicion());
        }
    }

    // =========================================================================
    // Amount aggregation
    // =========================================================================

    @Nested
    @DisplayName("Amount aggregation")
    class AmountAggregation {

        @Test
        @DisplayName("totalAmount is the sum of transaction amounts in the window")
        void totalAmount_isSumOfAmounts() {
            List<Transaction> txns = List.of(
                    txAt("2026-04-11T10:10:00Z", BigDecimal.valueOf(100.50)),
                    txAt("2026-04-11T10:20:00Z", BigDecimal.valueOf(200.75))
            );
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 1);

            assertEquals(0, new BigDecimal("301.25").compareTo(result.get(0).totalAmount()));
        }

        @Test
        @DisplayName("averageAmount is correct for multiple transactions")
        void averageAmount_isCorrectForMultipleTransactions() {
            List<Transaction> txns = List.of(
                    txAt("2026-04-11T10:10:00Z", BigDecimal.valueOf(100)),
                    txAt("2026-04-11T10:20:00Z", BigDecimal.valueOf(200)),
                    txAt("2026-04-11T10:30:00Z", BigDecimal.valueOf(300))
            );
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 1);

            // average = 600 / 3 = 200.00
            assertEquals(0, new BigDecimal("200.00").compareTo(result.get(0).averageAmount()));
        }

        @Test
        @DisplayName("averageAmount is null for empty window")
        void averageAmount_isNullForEmptyWindow() {
            List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 1);
            assertNull(result.get(0).averageAmount());
        }
    }

    // =========================================================================
    // Window risk score and elevated suspicion
    // =========================================================================

    @Nested
    @DisplayName("Window risk score and elevated suspicion")
    class WindowRiskScore {

        @Test
        @DisplayName("windowRiskScore is zero when transactions have no riskScore")
        void noRiskScores_windowRiskScoreIsZero() {
            List<Transaction> txns = List.of(txAt("2026-04-11T10:10:00Z"));
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 1);

            assertEquals(0.0, result.get(0).windowRiskScore());
        }

        @Test
        @DisplayName("windowRiskScore is average of normalised transaction risk scores")
        void windowRiskScore_isAverageNormalisedScore() {
            // 80 and 60 → average = 70 → normalised = 0.70
            List<Transaction> txns = List.of(
                    txAt("2026-04-11T10:10:00Z", 80),
                    txAt("2026-04-11T10:20:00Z", 60)
            );
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 1);

            assertEquals(0.70, result.get(0).windowRiskScore(), 1e-9);
        }

        @Test
        @DisplayName("windowRiskScore is capped at 1.0")
        void windowRiskScore_cappedAtOne() {
            // riskScore of 120 (invalid but should not crash; capped)
            List<Transaction> txns = List.of(txAt("2026-04-11T10:10:00Z", 120));
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 1);

            assertEquals(1.0, result.get(0).windowRiskScore());
        }

        @Test
        @DisplayName("elevatedSuspicion is false when windowRiskScore is below threshold")
        void belowThreshold_notElevated() {
            // riskScore = 40 → normalised = 0.40, below default threshold of 0.50
            List<Transaction> txns = List.of(txAt("2026-04-11T10:10:00Z", 40));
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 1);

            assertFalse(result.get(0).elevatedSuspicion());
        }

        @Test
        @DisplayName("elevatedSuspicion is false when windowRiskScore equals threshold (not strictly above)")
        void atThreshold_notElevated() {
            // riskScore = 50 → normalised = 0.50 → equal to threshold, not strictly above
            List<Transaction> txns = List.of(txAt("2026-04-11T10:10:00Z", 50));
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 1);

            assertFalse(result.get(0).elevatedSuspicion(),
                    "elevatedSuspicion should require strictly greater than threshold");
        }

        @Test
        @DisplayName("elevatedSuspicion is true when windowRiskScore is above threshold")
        void aboveThreshold_isElevated() {
            // riskScore = 80 → normalised = 0.80 > 0.50 threshold
            List<Transaction> txns = List.of(txAt("2026-04-11T10:10:00Z", 80));
            List<HourlyRiskWindow> result = service.aggregate(txns, REF, 1);

            assertTrue(result.get(0).elevatedSuspicion());
        }

        @Test
        @DisplayName("custom elevated suspicion threshold is respected")
        void customThreshold_respected() {
            // Set threshold to 0.90 — a score of 0.80 should NOT be elevated
            RiskScoringConfig customConfig = new RiskScoringConfig(
                    0.35, 0.25, 0.15, 0.25,
                    5, 4, 10_000.0,
                    0.40, 0.70, 0.90,
                    Set.of()
            );
            HourlyWindowAggregatorService customService =
                    new HourlyWindowAggregatorService(customConfig);

            List<Transaction> txns = List.of(txAt("2026-04-11T10:10:00Z", 80));
            List<HourlyRiskWindow> result = customService.aggregate(txns, REF, 1);

            assertFalse(result.get(0).elevatedSuspicion(),
                    "Score 0.80 should not be elevated when threshold is 0.90");
        }
    }

    // =========================================================================
    // Malformed timestamps
    // =========================================================================

    @Test
    @DisplayName("transaction with malformed timestamp is excluded and does not throw")
    void malformedTimestamp_excludedGracefully() {
        Transaction bad = tx("TXN-BAD", "not-a-timestamp", BigDecimal.TEN, null);
        Transaction good = txAt("2026-04-11T10:15:00Z");

        List<HourlyRiskWindow> result = service.aggregate(List.of(bad, good), REF, 1);

        assertEquals(1, result.get(0).transactionCount(), "Only the valid transaction should count");
    }

    @Test
    @DisplayName("transaction with null timestamp is excluded and does not throw")
    void nullTimestamp_excludedGracefully() {
        Transaction nullTs = tx("TXN-NULL", null, BigDecimal.TEN, null);

        assertDoesNotThrow(() -> service.aggregate(List.of(nullTs), REF, 1));
        List<HourlyRiskWindow> result = service.aggregate(List.of(nullTs), REF, 1);
        assertEquals(0, result.get(0).transactionCount());
    }

    // =========================================================================
    // Result is unmodifiable
    // =========================================================================

    @Test
    @DisplayName("returned list is unmodifiable")
    void returnedList_isUnmodifiable() {
        List<HourlyRiskWindow> result = service.aggregate(List.of(), REF, 3);
        assertThrows(UnsupportedOperationException.class, () -> result.add(null));
    }
}

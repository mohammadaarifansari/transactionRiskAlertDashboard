package com.riskdashboard.service;

import com.riskdashboard.model.FraudSignalAssessment;
import com.riskdashboard.model.RiskAssessment;
import com.riskdashboard.model.RiskTier;
import com.riskdashboard.model.TransactionData.Transaction;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the four fraud signal calculators to produce a full {@link RiskAssessment}
 * for a given account.
 *
 * <p>Scoring is deterministic: the same account transactions and reference time always
 * produce an identical score, tier, and ordered signal list.</p>
 *
 * <p>The {@code totalRiskScore} is the sum of all four weighted contributions, clamped
 * to {@code [0.0, 1.0]}. Signals in the returned assessment are ordered by
 * {@code weightedContribution} descending.</p>
 */
public class RiskScoringService {

    private final FraudSignalCalculatorService signalCalculator;
    private final RiskScoringConfig config;

    public RiskScoringService(FraudSignalCalculatorService signalCalculator,
                               RiskScoringConfig config) {
        if (signalCalculator == null) {
            throw new IllegalArgumentException("FraudSignalCalculatorService must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("RiskScoringConfig must not be null");
        }
        this.signalCalculator = signalCalculator;
        this.config = config;
    }

    /**
     * Computes a full risk assessment for the given account at the given reference time.
     *
     * @param accountId     account identifier; must not be null or blank
     * @param transactions  the account's transaction list; must not be null
     * @param referenceTime the instant representing "now"; must not be null
     * @return a complete, deterministic {@link RiskAssessment}
     * @throws IllegalArgumentException if any argument is null or {@code accountId} is blank
     */
    public RiskAssessment assess(String accountId,
                                  List<Transaction> transactions,
                                  Instant referenceTime) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID must not be null or blank");
        }
        if (transactions == null) {
            throw new IllegalArgumentException("Transactions list must not be null");
        }
        if (referenceTime == null) {
            throw new IllegalArgumentException("Reference time must not be null");
        }

        FraudSignalAssessment velocity = signalCalculator.calculateVelocity(transactions, referenceTime);
        FraudSignalAssessment geo = signalCalculator.calculateGeo(transactions, referenceTime);
        FraudSignalAssessment mcc = signalCalculator.calculateMcc(transactions);
        FraudSignalAssessment highValue = signalCalculator.calculateHighValue(transactions);

        double totalRiskScore = Math.min(
                velocity.weightedContribution()
                        + geo.weightedContribution()
                        + mcc.weightedContribution()
                        + highValue.weightedContribution(),
                1.0);

        List<FraudSignalAssessment> orderedSignals = List.of(velocity, geo, mcc, highValue).stream()
                .sorted(Comparator.comparingDouble(FraudSignalAssessment::weightedContribution).reversed())
                .toList();

        RiskTier riskTier = determineTier(totalRiskScore);
        String recommendedAction = determineRecommendedAction(riskTier);

        return new RiskAssessment(
                accountId,
                referenceTime.toString(),
                totalRiskScore,
                riskTier,
                orderedSignals,
                recommendedAction
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private RiskTier determineTier(double totalRiskScore) {
        if (totalRiskScore >= config.getRedTierThreshold()) {
            return RiskTier.RED;
        }
        if (totalRiskScore >= config.getYellowTierThreshold()) {
            return RiskTier.YELLOW;
        }
        return RiskTier.GREEN;
    }

    private String determineRecommendedAction(RiskTier tier) {
        return switch (tier) {
            case RED -> "Block and escalate";
            case YELLOW -> "Review";
            case GREEN -> "Monitor";
        };
    }
}

package com.riskdashboard.model;

import java.util.List;

/**
 * The account-level risk decision produced by the scoring engine.
 *
 * <p>{@code totalRiskScore} is in [0.0, 1.0]. {@code contributingSignals} is ordered
 * by {@code weightedContribution} descending so the highest-impact signal appears first.</p>
 *
 * @param accountId            the account that was assessed
 * @param assessmentTimestamp  ISO 8601 timestamp of when the assessment was computed
 * @param totalRiskScore       weighted sum of all signal contributions, clamped to [0.0, 1.0]
 * @param riskTier             GREEN, YELLOW, or RED tier derived from {@code totalRiskScore}
 * @param contributingSignals  all four signals ordered by impact (highest first)
 * @param recommendedAction    plain-language operational recommendation for this tier
 */
public record RiskAssessment(
        String accountId,
        String assessmentTimestamp,
        double totalRiskScore,
        RiskTier riskTier,
        List<FraudSignalAssessment> contributingSignals,
        String recommendedAction
) {}

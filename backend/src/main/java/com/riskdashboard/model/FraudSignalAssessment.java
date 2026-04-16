package com.riskdashboard.model;

/**
 * The calculated outcome for a single fraud signal.
 *
 * <p>All numeric fields are bounded: {@code normalizedScore} and
 * {@code weightedContribution} are in [0.0, 1.0]; {@code rawValue} carries
 * the raw measured quantity (e.g. transaction count, amount).</p>
 *
 * @param signalType           which fraud signal produced this assessment
 * @param rawValue             the raw measured value (unit depends on signal type)
 * @param normalizedScore      score normalised to [0.0, 1.0]
 * @param weight               the configured contribution weight
 * @param weightedContribution {@code normalizedScore × weight}
 * @param explanation          plain-language summary suitable for display
 */
public record FraudSignalAssessment(
        FraudSignalType signalType,
        double rawValue,
        double normalizedScore,
        double weight,
        double weightedContribution,
        String explanation
) {}

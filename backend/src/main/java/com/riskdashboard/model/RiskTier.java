package com.riskdashboard.model;

/**
 * Represents the three possible account risk tiers per Blueprint thresholds.
 *
 * <ul>
 *   <li>{@link #GREEN} — total risk score 0.00 to 0.39</li>
 *   <li>{@link #YELLOW} — total risk score 0.40 to 0.69</li>
 *   <li>{@link #RED} — total risk score 0.70 to 1.00</li>
 * </ul>
 */
public enum RiskTier {
    GREEN,
    YELLOW,
    RED
}

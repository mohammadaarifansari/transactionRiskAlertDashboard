package com.riskdashboard.model;

/**
 * Identifies each of the four weighted fraud signal types evaluated in risk scoring.
 *
 * <ul>
 *   <li>{@link #VELOCITY} — unusual transaction frequency in a short period</li>
 *   <li>{@link #GEO} — unusual country or location pattern</li>
 *   <li>{@link #MCC} — unexpected merchant category behaviour</li>
 *   <li>{@link #HIGH_VALUE} — amount significantly above normal pattern</li>
 * </ul>
 */
public enum FraudSignalType {
    VELOCITY,
    GEO,
    MCC,
    HIGH_VALUE
}

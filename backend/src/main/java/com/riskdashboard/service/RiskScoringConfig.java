package com.riskdashboard.service;

import java.util.Collections;
import java.util.Set;

/**
 * Centralised configuration for all risk-scoring weights, thresholds, and signal parameters.
 *
 * <p>Construct via the no-arg constructor to get Blueprint-specified defaults.
 * The package-private all-args constructor is available for test customisation.</p>
 *
 * <p>Signal weights: VELOCITY=0.35, GEO=0.25, MCC=0.15, HIGH_VALUE=0.25 (total = 1.00).</p>
 * <p>Tier thresholds: GREEN 0.00–0.39, YELLOW 0.40–0.69, RED 0.70–1.00.</p>
 */
public final class RiskScoringConfig {

    // -------------------------------------------------------------------------
    // Default constants (Blueprint-specified values)
    // -------------------------------------------------------------------------

    public static final double DEFAULT_VELOCITY_WEIGHT = 0.35;
    public static final double DEFAULT_GEO_WEIGHT = 0.25;
    public static final double DEFAULT_MCC_WEIGHT = 0.15;
    public static final double DEFAULT_HIGH_VALUE_WEIGHT = 0.25;

    /** Transactions per 1-hour window at which velocity normalised score reaches 1.0. */
    public static final int DEFAULT_VELOCITY_THRESHOLD = 5;

    /** Distinct destination countries in 24 hours at which geo normalised score reaches 1.0. */
    public static final int DEFAULT_GEO_THRESHOLD = 4;

    /** Transaction amount at which high-value normalised score reaches 1.0. */
    public static final double DEFAULT_HIGH_VALUE_THRESHOLD = 10_000.0;

    /** Minimum total risk score for YELLOW tier. */
    public static final double DEFAULT_YELLOW_THRESHOLD = 0.40;

    /** Minimum total risk score for RED tier. */
    public static final double DEFAULT_RED_THRESHOLD = 0.70;

    /** Hourly window risk score above which {@code elevatedSuspicion} is set to {@code true}. */
    public static final double DEFAULT_ELEVATED_SUSPICION_THRESHOLD = 0.50;

    /** Merchant category codes treated as elevated risk for the MCC signal. */
    public static final Set<String> DEFAULT_HIGH_RISK_MERCHANT_CATEGORIES = Set.of(
            "CRYPTO_EXCHANGE", "GAMBLING", "PAWN_SHOP", "WIRE_TRANSFER", "MONEY_SERVICE"
    );

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    private final double velocityWeight;
    private final double geoWeight;
    private final double mccWeight;
    private final double highValueWeight;
    private final int velocityThreshold;
    private final int geoThreshold;
    private final double highValueThreshold;
    private final double yellowTierThreshold;
    private final double redTierThreshold;
    private final double elevatedSuspicionThreshold;
    private final Set<String> highRiskMerchantCategories;

    /** Creates a configuration instance using Blueprint-specified defaults. */
    public RiskScoringConfig() {
        this.velocityWeight = DEFAULT_VELOCITY_WEIGHT;
        this.geoWeight = DEFAULT_GEO_WEIGHT;
        this.mccWeight = DEFAULT_MCC_WEIGHT;
        this.highValueWeight = DEFAULT_HIGH_VALUE_WEIGHT;
        this.velocityThreshold = DEFAULT_VELOCITY_THRESHOLD;
        this.geoThreshold = DEFAULT_GEO_THRESHOLD;
        this.highValueThreshold = DEFAULT_HIGH_VALUE_THRESHOLD;
        this.yellowTierThreshold = DEFAULT_YELLOW_THRESHOLD;
        this.redTierThreshold = DEFAULT_RED_THRESHOLD;
        this.elevatedSuspicionThreshold = DEFAULT_ELEVATED_SUSPICION_THRESHOLD;
        this.highRiskMerchantCategories = DEFAULT_HIGH_RISK_MERCHANT_CATEGORIES;
    }

    /**
     * Full constructor for test customisation; allows overriding individual thresholds
     * without requiring a mocking framework.
     */
    RiskScoringConfig(
            double velocityWeight,
            double geoWeight,
            double mccWeight,
            double highValueWeight,
            int velocityThreshold,
            int geoThreshold,
            double highValueThreshold,
            double yellowTierThreshold,
            double redTierThreshold,
            double elevatedSuspicionThreshold,
            Set<String> highRiskMerchantCategories) {
        this.velocityWeight = velocityWeight;
        this.geoWeight = geoWeight;
        this.mccWeight = mccWeight;
        this.highValueWeight = highValueWeight;
        this.velocityThreshold = velocityThreshold;
        this.geoThreshold = geoThreshold;
        this.highValueThreshold = highValueThreshold;
        this.yellowTierThreshold = yellowTierThreshold;
        this.redTierThreshold = redTierThreshold;
        this.elevatedSuspicionThreshold = elevatedSuspicionThreshold;
        this.highRiskMerchantCategories = Collections.unmodifiableSet(highRiskMerchantCategories);
    }

    public double getVelocityWeight() { return velocityWeight; }
    public double getGeoWeight() { return geoWeight; }
    public double getMccWeight() { return mccWeight; }
    public double getHighValueWeight() { return highValueWeight; }
    public int getVelocityThreshold() { return velocityThreshold; }
    public int getGeoThreshold() { return geoThreshold; }
    public double getHighValueThreshold() { return highValueThreshold; }
    public double getYellowTierThreshold() { return yellowTierThreshold; }
    public double getRedTierThreshold() { return redTierThreshold; }
    public double getElevatedSuspicionThreshold() { return elevatedSuspicionThreshold; }
    public Set<String> getHighRiskMerchantCategories() { return highRiskMerchantCategories; }
}

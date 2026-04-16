/**
 * Centralised risk scoring configuration.
 *
 * Weights and thresholds match Blueprint defaults.
 * These constants must not be duplicated in components or templates.
 */

export interface SignalWeights {
  readonly VELOCITY: number;
  readonly GEO: number;
  readonly MCC: number;
  readonly HIGH_VALUE: number;
}

export interface TierThresholds {
  readonly GREEN_MAX: number;
  readonly YELLOW_MIN: number;
  readonly YELLOW_MAX: number;
  readonly RED_MIN: number;
}

export interface RiskScoringConfig {
  readonly weights: SignalWeights;
  readonly thresholds: TierThresholds;
  /** Number of transactions per hour above which velocity normalised score reaches 1.0. */
  readonly velocityThreshold: number;
  /** Number of distinct destination countries in 24h above which geo score reaches 1.0. */
  readonly geoThreshold: number;
  /** Transaction amount at which high-value normalised score reaches 1.0. */
  readonly highValueThreshold: number;
  /** Hourly window risk score at or above which elevatedSuspicion is flagged. */
  readonly elevatedSuspicionThreshold: number;
  readonly highRiskMerchantCategories: ReadonlySet<string>;
}

export const RISK_SCORING_CONFIG: RiskScoringConfig = {
  weights: {
    VELOCITY: 0.35,
    GEO: 0.25,
    MCC: 0.15,
    HIGH_VALUE: 0.25
  },
  thresholds: {
    GREEN_MAX: 0.39,
    YELLOW_MIN: 0.40,
    YELLOW_MAX: 0.69,
    RED_MIN: 0.70
  },
  velocityThreshold: 5,
  geoThreshold: 4,
  highValueThreshold: 10_000,
  elevatedSuspicionThreshold: 0.50,
  highRiskMerchantCategories: new Set([
    'CRYPTO_EXCHANGE',
    'GAMBLING',
    'PAWN_SHOP',
    'WIRE_TRANSFER',
    'MONEY_SERVICE'
  ])
} as const;

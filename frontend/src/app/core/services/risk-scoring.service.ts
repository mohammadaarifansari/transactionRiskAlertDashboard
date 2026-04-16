import { Injectable, inject } from '@angular/core';
import { Transaction } from '../../shared/models/transaction.model';
import { FraudSignalAssessment, FraudSignalType } from '../../shared/models/fraud-signal-assessment.model';
import { RiskAssessment, RiskTier } from '../../shared/models/risk-assessment.model';
import { DataLoaderService } from './data-loader.service';
import { RISK_SCORING_CONFIG } from '../../shared/config/risk-scoring.config';

/**
 * Computes a deterministic RiskAssessment for a given account by evaluating
 * the four configured fraud signals against the account's recent transactions.
 */
@Injectable({ providedIn: 'root' })
export class RiskScoringService {
  private readonly dataLoader = inject(DataLoaderService);
  private readonly config = RISK_SCORING_CONFIG;

  getLatestRiskAssessment(accountId: string): RiskAssessment {
    const transactions = this.dataLoader.getTransactionsByAccountId(accountId);
    const now = this.referenceTime(transactions);
    const recent = this.last24Hours(transactions, now);

    const signals: FraudSignalAssessment[] = [
      this.velocitySignal(recent, now),
      this.geoSignal(recent),
      this.mccSignal(recent),
      this.highValueSignal(recent)
    ];

    const totalRiskScore = signals.reduce((sum, s) => sum + s.weightedContribution, 0);
    const clamped = Math.min(1.0, Math.max(0.0, Number(totalRiskScore.toFixed(4))));
    const riskTier = this.toTier(clamped);

    return {
      accountId,
      assessmentTimestamp: now.toISOString(),
      totalRiskScore: clamped,
      riskTier,
      contributingSignals: [...signals].sort(
        (a, b) => b.weightedContribution - a.weightedContribution
      ),
      recommendedAction: this.toRecommendedAction(riskTier)
    };
  }

  // ── Signal calculators ───────────────────────────────────────────────────

  private velocitySignal(transactions: Transaction[], now: Date): FraudSignalAssessment {
    const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);
    const count = transactions.filter((t) => new Date(t.timestamp) >= oneHourAgo).length;
    const normalized = Math.min(1.0, count / this.config.velocityThreshold);
    const weight = this.config.weights.VELOCITY;
    return {
      signalType: 'VELOCITY' as FraudSignalType,
      rawValue: count,
      normalizedScore: round4(normalized),
      weight,
      weightedContribution: round4(normalized * weight),
      explanation: `${count} transaction${count !== 1 ? 's' : ''} in the last hour (threshold: ${this.config.velocityThreshold})`
    };
  }

  private geoSignal(transactions: Transaction[]): FraudSignalAssessment {
    const countries = new Set(
      transactions.map((t) => t.merchantCountry).filter((c): c is string => !!c)
    );
    const count = countries.size;
    const normalized = Math.min(1.0, count / this.config.geoThreshold);
    const weight = this.config.weights.GEO;
    return {
      signalType: 'GEO' as FraudSignalType,
      rawValue: count,
      normalizedScore: round4(normalized),
      weight,
      weightedContribution: round4(normalized * weight),
      explanation: `Transactions across ${count} distinct countr${count !== 1 ? 'ies' : 'y'} in 24 hours`
    };
  }

  private mccSignal(transactions: Transaction[]): FraudSignalAssessment {
    const highRisk = transactions.filter((t) =>
      this.config.highRiskMerchantCategories.has(t.merchantCategory)
    );
    const ratio = transactions.length === 0 ? 0 : highRisk.length / transactions.length;
    const normalized = Math.min(1.0, ratio);
    const weight = this.config.weights.MCC;
    return {
      signalType: 'MCC' as FraudSignalType,
      rawValue: highRisk.length,
      normalizedScore: round4(normalized),
      weight,
      weightedContribution: round4(normalized * weight),
      explanation: `${highRisk.length} of ${transactions.length} transaction${transactions.length !== 1 ? 's' : ''} at elevated-risk merchant categories`
    };
  }

  private highValueSignal(transactions: Transaction[]): FraudSignalAssessment {
    const maxAmount = transactions.reduce((m, t) => Math.max(m, t.amount), 0);
    const normalized = Math.min(1.0, maxAmount / this.config.highValueThreshold);
    const weight = this.config.weights.HIGH_VALUE;
    return {
      signalType: 'HIGH_VALUE' as FraudSignalType,
      rawValue: maxAmount,
      normalizedScore: round4(normalized),
      weight,
      weightedContribution: round4(normalized * weight),
      explanation: `Peak transaction amount: ${maxAmount.toLocaleString(undefined, { maximumFractionDigits: 2 })} (threshold: ${this.config.highValueThreshold.toLocaleString()})`
    };
  }

  // ── Tier and action mappers ──────────────────────────────────────────────

  private toTier(score: number): RiskTier {
    if (score >= this.config.thresholds.RED_MIN) return 'RED';
    if (score >= this.config.thresholds.YELLOW_MIN) return 'YELLOW';
    return 'GREEN';
  }

  private toRecommendedAction(tier: RiskTier): string {
    switch (tier) {
      case 'GREEN':
        return 'Monitor — no immediate action required.';
      case 'YELLOW':
        return 'Review — verify recent activity and apply additional scrutiny.';
      case 'RED':
        return 'Block and escalate — halt transactions and refer for manual investigation.';
      default:
        return 'Monitor — no action required.';
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private referenceTime(transactions: Transaction[]): Date {
    if (transactions.length === 0) return new Date();
    const maxTs = Math.max(...transactions.map((t) => new Date(t.timestamp).getTime()));
    return new Date(maxTs);
  }

  private last24Hours(transactions: Transaction[], now: Date): Transaction[] {
    const cutoff = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    return transactions.filter((t) => new Date(t.timestamp) >= cutoff);
  }
}

function round4(n: number): number {
  return Math.round(n * 10_000) / 10_000;
}

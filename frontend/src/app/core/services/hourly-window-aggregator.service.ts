import { Injectable, inject } from '@angular/core';
import { HourlyRiskWindow } from '../../shared/models/hourly-risk-window.model';
import { Transaction } from '../../shared/models/transaction.model';
import { DataLoaderService } from './data-loader.service';
import { RISK_SCORING_CONFIG } from '../../shared/config/risk-scoring.config';

/**
 * Aggregates an account's transactions into hourly risk windows
 * for the 24-hour activity timeline.
 *
 * Windows are anchored to the most recent transaction's timestamp so that
 * historical mock data always renders meaningful charts regardless of
 * when the application is run.
 */
@Injectable({ providedIn: 'root' })
export class HourlyWindowAggregatorService {
  private readonly dataLoader = inject(DataLoaderService);
  private readonly config = RISK_SCORING_CONFIG;

  /**
   * Returns up to `hours` hourly windows ordered oldest-first.
   * Each window covers a one-hour half-open interval [hourStart, hourStart + 1h).
   * Windows with no transactions have transactionCount=0 and windowRiskScore=0.
   */
  getWindowsForAccount(accountId: string, hours = 24): HourlyRiskWindow[] {
    const transactions = this.dataLoader.getTransactionsByAccountId(accountId);

    if (transactions.length === 0) {
      return [];
    }

    const referenceTime = this.findReferenceTime(transactions);
    const windowStart = new Date(referenceTime.getTime() - hours * 60 * 60 * 1000);

    const buckets = this.createBuckets(windowStart, hours);
    this.fillBuckets(buckets, transactions, windowStart, referenceTime);

    return Array.from(buckets.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([hourStart, txs]) => this.buildWindow(hourStart, txs));
  }

  // ── Private helpers ──────────────────────────────────────────────────────

  /** Anchors to the latest transaction timestamp so historical data always renders. */
  private findReferenceTime(transactions: Transaction[]): Date {
    const maxTs = Math.max(...transactions.map((t) => new Date(t.timestamp).getTime()));
    return new Date(maxTs);
  }

  /** Creates 24 ordered empty buckets keyed by hour-start ISO string. */
  private createBuckets(windowStart: Date, hours: number): Map<string, Transaction[]> {
    const buckets = new Map<string, Transaction[]>();
    for (let h = 0; h < hours; h++) {
      const bucketTime = new Date(windowStart.getTime() + h * 60 * 60 * 1000);
      buckets.set(this.truncateToHour(bucketTime), []);
    }
    return buckets;
  }

  /** Distributes transactions into the appropriate hourly bucket. */
  private fillBuckets(
    buckets: Map<string, Transaction[]>,
    transactions: Transaction[],
    windowStart: Date,
    referenceTime: Date
  ): void {
    for (const tx of transactions) {
      const ts = new Date(tx.timestamp);
      if (ts < windowStart || ts > referenceTime) continue;

      const key = this.truncateToHour(ts);
      const bucket = buckets.get(key);
      if (bucket) {
        bucket.push(tx);
      }
    }
  }

  /** Builds the HourlyRiskWindow aggregate for one bucket. */
  private buildWindow(hourStart: string, transactions: Transaction[]): HourlyRiskWindow {
    const count = transactions.length;
    const totalAmount = transactions.reduce((sum, t) => sum + t.amount, 0);
    const averageAmount = count > 0 ? totalAmount / count : 0;

    const windowRiskScore = this.computeWindowRiskScore(transactions);
    const elevatedSuspicion = windowRiskScore >= this.config.elevatedSuspicionThreshold;

    return {
      hourStart,
      transactionCount: count,
      totalAmount: round2(totalAmount),
      averageAmount: round2(averageAmount),
      windowRiskScore: round4(windowRiskScore),
      elevatedSuspicion
    };
  }

  /**
   * Derives a normalised [0.0, 1.0] window risk score from the average pre-computed
   * transaction risk scores. Falls back to 0.0 when no scores are available.
   */
  private computeWindowRiskScore(transactions: Transaction[]): number {
    const scored = transactions.filter((t) => t.riskScore !== undefined);
    if (scored.length === 0) return 0;
    const avg = scored.reduce((sum, t) => sum + (t.riskScore ?? 0), 0) / scored.length;
    return Math.min(1.0, Math.max(0.0, avg / 100));
  }

  /** Truncates a Date to its UTC hour boundary. Returns ISO 8601 string. */
  private truncateToHour(date: Date): string {
    return new Date(
      Date.UTC(
        date.getUTCFullYear(),
        date.getUTCMonth(),
        date.getUTCDate(),
        date.getUTCHours()
      )
    ).toISOString();
  }
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function round4(n: number): number {
  return Math.round(n * 10_000) / 10_000;
}

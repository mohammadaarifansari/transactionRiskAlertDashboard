import { Injectable, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';
import { RiskAssessment } from '../../shared/models/risk-assessment.model';
import { HourlyRiskWindow } from '../../shared/models/hourly-risk-window.model';
import { IDashboardApiService } from './dashboard-api.interface';
import { DataLoaderService } from './data-loader.service';
import { RiskScoringService } from './risk-scoring.service';
import { HourlyWindowAggregatorService } from './hourly-window-aggregator.service';

/**
 * Local implementation of {@link IDashboardApiService}.
 *
 * Delegates to the existing in-memory services that read the bundled
 * static JSON asset. Synchronous results are wrapped in {@code of()} so
 * callers use the same Observable interface regardless of implementation.
 *
 * Use this when running without the Java backend (e.g. during initial
 * development or offline demos).
 */
@Injectable({ providedIn: 'root' })
export class LocalDashboardApiService implements IDashboardApiService {
  private readonly dataLoader = inject(DataLoaderService);
  private readonly riskScoring = inject(RiskScoringService);
  private readonly windowAggregator = inject(HourlyWindowAggregatorService);

  searchAccounts(query: string): Observable<Account[]> {
    return of(this.dataLoader.searchAccounts(query));
  }

  getAccountById(accountId: string): Observable<Account | null> {
    return of(this.dataLoader.getAccountById(accountId));
  }

  getTransactionsByAccountId(accountId: string): Observable<Transaction[]> {
    return of(this.dataLoader.getTransactionsByAccountId(accountId));
  }

  getLatestRiskAssessment(accountId: string): Observable<RiskAssessment> {
    try {
      const assessment = this.riskScoring.getLatestRiskAssessment(accountId);
      return of(assessment);
    } catch (err: unknown) {
      return throwError(() => err);
    }
  }

  getHourlyRiskWindows(accountId: string, hours = 24): Observable<HourlyRiskWindow[]> {
    try {
      const windows = this.windowAggregator.getWindowsForAccount(accountId, hours);
      return of(windows);
    } catch (err: unknown) {
      return throwError(() => err);
    }
  }
}

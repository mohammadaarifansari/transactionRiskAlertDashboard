import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';
import { RiskAssessment } from '../../shared/models/risk-assessment.model';
import { HourlyRiskWindow } from '../../shared/models/hourly-risk-window.model';

/**
 * Contract for all data-access operations used by the dashboard UI.
 *
 * This interface is the stable boundary between the presentation layer
 * and the data source (local JSON or backend REST API). Components and
 * services depend only on this interface — never on a specific implementation.
 *
 * All methods return Observables so callers handle both synchronous and
 * async implementations uniformly.
 */
export interface IDashboardApiService {
  /** Search accounts by account ID or customer name (case-insensitive partial match). */
  searchAccounts(query: string): Observable<Account[]>;

  /** Retrieve a single account by exact account ID. Returns null when not found. */
  getAccountById(accountId: string): Observable<Account | null>;

  /** Retrieve all transactions for the given account ID. */
  getTransactionsByAccountId(accountId: string): Observable<Transaction[]>;

  /** Retrieve the latest risk assessment for the given account ID. */
  getLatestRiskAssessment(accountId: string): Observable<RiskAssessment>;

  /** Retrieve the hourly risk windows for the given account over the specified rolling window. */
  getHourlyRiskWindows(accountId: string, hours?: number): Observable<HourlyRiskWindow[]>;
}

/**
 * Injection token for {@link IDashboardApiService}.
 *
 * Provide in app.config.ts:
 * ```ts
 * { provide: DASHBOARD_API, useClass: HttpDashboardApiService }
 * ```
 */
export const DASHBOARD_API = new InjectionToken<IDashboardApiService>('DASHBOARD_API');

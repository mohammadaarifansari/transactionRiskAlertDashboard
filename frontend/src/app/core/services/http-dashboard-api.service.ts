import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, catchError, throwError } from 'rxjs';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';
import { RiskAssessment } from '../../shared/models/risk-assessment.model';
import { HourlyRiskWindow } from '../../shared/models/hourly-risk-window.model';
import { IDashboardApiService } from './dashboard-api.interface';

/**
 * Raw transaction shape returned by the Java backend.
 * The backend uses `originCountry`/`destinationCountry` while the frontend
 * model uses `merchantCountry`. This interface captures the wire format.
 */
interface BackendTransaction {
  transactionId: string;
  accountId: string;
  timestamp: string;
  amount: number;
  currency: string;
  merchantName?: string;
  merchantCategory: string;
  originCountry?: string;
  destinationCountry?: string;
  channel?: string;
  status?: string;
  riskScore?: number;
  fraudIndicators?: string[];
}

/**
 * HTTP implementation of {@link IDashboardApiService}.
 *
 * Calls the Java backend REST API at {@code /api/accounts/**}.
 * In development the Angular proxy ({@code proxy.conf.json}) forwards
 * these requests to {@code http://localhost:8080}.
 *
 * Response normalisation:
 * - Backend {@code destinationCountry} is mapped to frontend {@code merchantCountry}
 *   so the geo-anomaly signal and display layer remain consistent.
 * - HTTP errors are re-thrown with a user-safe message.
 */
@Injectable({ providedIn: 'root' })
export class HttpDashboardApiService implements IDashboardApiService {
  private readonly http = inject(HttpClient);

  private readonly baseUrl = '/api/accounts';

  searchAccounts(query: string): Observable<Account[]> {
    if (!query.trim()) {
      return new Observable((obs) => {
        obs.next([]);
        obs.complete();
      });
    }
    const params = new HttpParams().set('query', query.trim());
    return this.http
      .get<Account[]>(this.baseUrl, { params })
      .pipe(catchError((err) => this.handleError('searchAccounts', err)));
  }

  getAccountById(accountId: string): Observable<Account | null> {
    return this.http
      .get<Account>(`${this.baseUrl}/${encodeURIComponent(accountId)}`)
      .pipe(
        catchError((err) => {
          if (err?.status === 404) {
            return new Observable<Account | null>((obs) => {
              obs.next(null);
              obs.complete();
            });
          }
          return this.handleError('getAccountById', err);
        })
      );
  }

  getTransactionsByAccountId(accountId: string): Observable<Transaction[]> {
    return this.http
      .get<BackendTransaction[]>(`${this.baseUrl}/${encodeURIComponent(accountId)}/transactions`)
      .pipe(
        map((raw) => raw.map((t) => this.normaliseTransaction(t))),
        catchError((err) => this.handleError('getTransactionsByAccountId', err))
      );
  }

  getLatestRiskAssessment(accountId: string): Observable<RiskAssessment> {
    return this.http
      .get<RiskAssessment>(`${this.baseUrl}/${encodeURIComponent(accountId)}/risk-assessment`)
      .pipe(catchError((err) => this.handleError('getLatestRiskAssessment', err)));
  }

  getHourlyRiskWindows(accountId: string, hours = 24): Observable<HourlyRiskWindow[]> {
    const params = new HttpParams().set('window', `${hours}h`);
    return this.http
      .get<HourlyRiskWindow[]>(
        `${this.baseUrl}/${encodeURIComponent(accountId)}/risk-windows`,
        { params }
      )
      .pipe(catchError((err) => this.handleError('getHourlyRiskWindows', err)));
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /**
   * Maps a backend transaction record to the frontend {@link Transaction} model,
   * converting {@code destinationCountry} → {@code merchantCountry}.
   */
  private normaliseTransaction(raw: BackendTransaction): Transaction {
    return {
      transactionId: raw.transactionId,
      accountId: raw.accountId,
      timestamp: raw.timestamp,
      amount: raw.amount,
      currency: raw.currency ?? 'USD',
      merchantName: raw.merchantName,
      merchantCategory: raw.merchantCategory ?? 'OTHER',
      merchantCountry: raw.destinationCountry,
      channel: raw.channel,
      status: raw.status,
      riskScore: raw.riskScore,
      fraudIndicators: raw.fraudIndicators
    };
  }

  private handleError(operation: string, err: unknown): Observable<never> {
    const status = (err as { status?: number })?.status;
    const message =
      status === 404
        ? 'Resource not found.'
        : status != null
          ? `Server error (${status}). Please try again.`
          : 'Unable to connect to the risk service. Please ensure the backend is running.';
    console.error(`HttpDashboardApiService.${operation} failed:`, err);
    return throwError(() => new Error(message));
  }
}

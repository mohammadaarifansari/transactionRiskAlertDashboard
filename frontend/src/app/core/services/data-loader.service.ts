import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, catchError, throwError } from 'rxjs';
import { Account } from '../../shared/models/account.model';
import { Transaction } from '../../shared/models/transaction.model';

/**
 * Raw shape of a record in the flat transactions JSON array.
 * The JSON file is a flat array of transaction objects where customerName
 * is embedded per-record. Accounts are derived from unique accountId values.
 */
interface RawRecord {
  transactionId?: unknown;
  accountId?: unknown;
  customerName?: unknown;
  timestamp?: unknown;
  amount?: unknown;
  currency?: unknown;
  merchantName?: unknown;
  merchantCategory?: unknown;
  destinationCountry?: unknown;
  originCountry?: unknown;
  channel?: unknown;
  status?: unknown;
  riskScore?: unknown;
  fraudIndicators?: unknown;
}

/**
 * Loads and normalises account and transaction data from the static JSON asset.
 * Malformed records are skipped defensively; loader errors are propagated to callers.
 */
@Injectable({ providedIn: 'root' })
export class DataLoaderService {
  private readonly http = inject(HttpClient);

  private accounts: Account[] = [];
  private transactions: Transaction[] = [];
  private dataLoaded = false;

  /**
   * Fetches the data file and populates the in-memory store.
   * The JSON file is a flat array of transaction records. Accounts are derived
   * from unique accountId/customerName pairs found within the array.
   */
  load(): Observable<void> {
    return this.http.get<unknown[]>('/assets/data/transactions.json').pipe(
      map((raw) => {
        const records = Array.isArray(raw) ? raw : [];
        this.transactions = this.normaliseTransactions(records);
        this.accounts = this.deriveAccounts(this.transactions);
        this.dataLoaded = true;
      }),
      catchError((err: unknown) => {
        const message = err instanceof Error ? err.message : 'Unknown error loading data';
        return throwError(() => new Error(`DataLoaderService: ${message}`));
      })
    );
  }

  isLoaded(): boolean {
    return this.dataLoaded;
  }

  getAccounts(): Account[] {
    return [...this.accounts];
  }

  getAccountById(accountId: string): Account | null {
    return this.accounts.find((a) => a.accountId === accountId) ?? null;
  }

  getTransactionsByAccountId(accountId: string): Transaction[] {
    return this.transactions.filter((t) => t.accountId === accountId);
  }

  searchAccounts(query: string): Account[] {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return this.accounts.filter(
      (a) =>
        a.accountId.toLowerCase().includes(q) ||
        a.customerName.toLowerCase().includes(q)
    );
  }

  // ── Normalisation ───────────────────────────────────────────────────────

  /** Derives unique Account objects from the normalised transaction list. */
  private deriveAccounts(transactions: Transaction[]): Account[] {
    const seen = new Map<string, Account>();
    for (const t of transactions) {
      if (!seen.has(t.accountId)) {
        const raw = t as Transaction & { customerName?: string };
        seen.set(t.accountId, {
          accountId: t.accountId,
          customerName: raw.customerName ?? t.accountId
        });
      }
    }
    return Array.from(seen.values());
  }

  private normaliseTransactions(raw: unknown[]): Transaction[] {
    return raw
      .filter(
        (r): r is RawRecord =>
          r !== null &&
          typeof r === 'object' &&
          typeof (r as RawRecord).transactionId === 'string' &&
          typeof (r as RawRecord).accountId === 'string' &&
          typeof (r as RawRecord).timestamp === 'string' &&
          (r as RawRecord).amount != null
      )
      .map((r) => {
        const amount = Number(r.amount);
        if (!isFinite(amount)) return null;

        const riskRaw = r.riskScore != null ? Number(r.riskScore) : undefined;
        const riskScore =
          riskRaw !== undefined && isFinite(riskRaw)
            ? Math.min(100, Math.max(0, riskRaw))
            : undefined;

        // customerName is stored as an extra field so deriveAccounts can read it
        const tx = {
          transactionId: String(r.transactionId),
          accountId: String(r.accountId),
          customerName: r.customerName != null ? String(r.customerName) : undefined,
          timestamp: String(r.timestamp),
          amount,
          currency: r.currency != null ? String(r.currency) : 'USD',
          merchantName: r.merchantName != null ? String(r.merchantName) : undefined,
          merchantCategory: r.merchantCategory != null ? String(r.merchantCategory) : 'OTHER',
          merchantCountry: r.destinationCountry != null ? String(r.destinationCountry) : undefined,
          channel: r.channel != null ? String(r.channel) : undefined,
          status: r.status != null ? String(r.status) : undefined,
          riskScore,
          fraudIndicators: Array.isArray(r.fraudIndicators)
            ? (r.fraudIndicators as unknown[]).filter((x): x is string => typeof x === 'string')
            : []
        };
        return tx as unknown as Transaction;
      })
      .filter((t): t is Transaction => t !== null);
  }
}

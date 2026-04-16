export interface Transaction {
  readonly transactionId: string;
  readonly accountId: string;
  readonly timestamp: string;
  readonly amount: number;
  readonly currency: string;
  readonly merchantName?: string;
  readonly merchantCategory: string;
  readonly merchantCountry?: string;
  readonly channel?: string;
  readonly status?: string;
  /** Pre-computed risk score in range 0–100. */
  readonly riskScore?: number;
  readonly fraudIndicators?: string[];
}

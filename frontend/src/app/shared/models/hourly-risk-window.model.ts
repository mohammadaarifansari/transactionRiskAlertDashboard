export interface HourlyRiskWindow {
  readonly hourStart: string;
  readonly transactionCount: number;
  readonly totalAmount: number;
  readonly averageAmount?: number;
  readonly windowRiskScore: number;
  readonly elevatedSuspicion: boolean;
}

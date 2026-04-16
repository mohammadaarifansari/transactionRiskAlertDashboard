export interface Account {
  readonly accountId: string;
  readonly customerName: string;
  readonly accountStatus?: string;
  readonly region?: string;
  readonly segment?: string;
  readonly createdAt?: string;
}

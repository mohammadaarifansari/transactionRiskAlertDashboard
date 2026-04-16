export type FraudSignalType = 'VELOCITY' | 'GEO' | 'MCC' | 'HIGH_VALUE';

export interface FraudSignalAssessment {
  readonly signalType: FraudSignalType;
  readonly rawValue: number;
  readonly normalizedScore: number;
  readonly weight: number;
  readonly weightedContribution: number;
  readonly explanation: string;
}

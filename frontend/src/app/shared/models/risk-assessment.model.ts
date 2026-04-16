import { FraudSignalAssessment } from './fraud-signal-assessment.model';

export type RiskTier = 'GREEN' | 'YELLOW' | 'RED';

export interface RiskAssessment {
  readonly accountId: string;
  readonly assessmentTimestamp: string;
  readonly totalRiskScore: number;
  readonly riskTier: RiskTier;
  readonly contributingSignals: FraudSignalAssessment[];
  readonly recommendedAction: string;
}

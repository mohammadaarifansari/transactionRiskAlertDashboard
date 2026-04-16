import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input
} from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { Account } from '../../shared/models/account.model';
import { RiskAssessment } from '../../shared/models/risk-assessment.model';
import { RiskScoringService } from '../../core/services/risk-scoring.service';

@Component({
  selector: 'app-risk-summary',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, DatePipe],
  templateUrl: './risk-summary.component.html',
  styleUrl: './risk-summary.component.scss'
})
export class RiskSummaryComponent {
  private readonly riskScoring = inject(RiskScoringService);

  readonly account = input.required<Account>();

  protected readonly assessment = computed<RiskAssessment>(() =>
    this.riskScoring.getLatestRiskAssessment(this.account().accountId)
  );

  protected readonly tierLabel = computed(() => {
    const tier = this.assessment().riskTier;
    return tier === 'RED' ? 'High Risk' : tier === 'YELLOW' ? 'Elevated Risk' : 'Low Risk';
  });
}

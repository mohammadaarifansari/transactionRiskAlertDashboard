import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
  effect
} from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { Account } from '../../shared/models/account.model';
import { RiskAssessment } from '../../shared/models/risk-assessment.model';
import { DASHBOARD_API } from '../../core/services/dashboard-api.interface';

@Component({
  selector: 'app-risk-summary',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, DatePipe],
  templateUrl: './risk-summary.component.html',
  styleUrl: './risk-summary.component.scss'
})
export class RiskSummaryComponent {
  private readonly api = inject(DASHBOARD_API);

  readonly account = input.required<Account>();

  protected readonly assessment = signal<RiskAssessment | null>(null);
  protected readonly isLoading = signal<boolean>(false);
  protected readonly hasError = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');

  protected readonly tierLabel = computed(() => {
    const tier = this.assessment()?.riskTier;
    return tier === 'RED' ? 'High Risk' : tier === 'YELLOW' ? 'Elevated Risk' : 'Low Risk';
  });

  constructor() {
    effect(
      () => {
        const accountId = this.account().accountId;
        this.loadAssessment(accountId);
      },
      { allowSignalWrites: true }
    );
  }

  private loadAssessment(accountId: string): void {
    this.isLoading.set(true);
    this.hasError.set(false);
    this.errorMessage.set('');

    this.api.getLatestRiskAssessment(accountId).subscribe({
      next: (result) => {
        this.assessment.set(result);
        this.isLoading.set(false);
      },
      error: (err: unknown) => {
        const msg = err instanceof Error ? err.message : 'Unable to load risk assessment.';
        this.hasError.set(true);
        this.errorMessage.set(msg);
        this.isLoading.set(false);
      }
    });
  }
}

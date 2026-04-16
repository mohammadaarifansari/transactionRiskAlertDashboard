import {
  ChangeDetectionStrategy,
  Component,
  computed,
  signal
} from '@angular/core';
import { AccountSearchComponent } from './features/account-search/account-search.component';
import { RiskSummaryComponent } from './features/risk-summary/risk-summary.component';
import { TimelineChartsComponent } from './features/timeline/timeline-charts.component';
import { Account } from './shared/models/account.model';

@Component({
  selector: 'app-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AccountSearchComponent, RiskSummaryComponent, TimelineChartsComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  protected readonly selectedAccount = signal<Account | null>(null);
  protected readonly hasSelection = computed(() => this.selectedAccount() !== null);

  protected onAccountSelected(account: Account): void {
    this.selectedAccount.set(account);
  }

  protected onSearchCleared(): void {
    this.selectedAccount.set(null);
  }
}

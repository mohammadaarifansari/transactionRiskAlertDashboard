import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { HourlyRiskWindow } from '../../shared/models/hourly-risk-window.model';
import { HourlyWindowAggregatorService } from '../../core/services/hourly-window-aggregator.service';
import {
  ChartDataTransformService,
  TrendSeries,
  CHART_BOUNDS
} from './chart-data-transform.service';

/**
 * P5-T2 – Amount trend and risk trend charts for the 24-hour transaction timeline.
 *
 * Connects to HourlyWindowAggregatorService for data and ChartDataTransformService
 * for SVG path computation. Renders two SVG polyline charts: transaction amount
 * over time and risk score over time, with elevated-suspicion highlight markers.
 *
 * Accessibility:
 * - Each chart SVG carries role="img" with a descriptive aria-labelledby.
 * - A companion data table follows each chart for screen reader users.
 * - Loading, empty, and error states use role="status" / role="alert" with aria-live.
 */
@Component({
  selector: 'app-timeline-charts',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, DatePipe],
  templateUrl: './timeline-charts.component.html',
  styleUrl: './timeline-charts.component.scss'
})
export class TimelineChartsComponent {
  private readonly windowAggregator = inject(HourlyWindowAggregatorService);
  private readonly chartTransform = inject(ChartDataTransformService);

  /** The account whose 24-hour timeline to display. */
  readonly accountId = input.required<string>();

  protected readonly isLoading = signal<boolean>(false);
  protected readonly hasError = signal<boolean>(false);
  protected readonly errorMessage = signal<string>('');
  protected readonly windows = signal<HourlyRiskWindow[]>([]);

  protected readonly amountTrend = computed<TrendSeries>(() =>
    this.chartTransform.toAmountTrend(this.windows())
  );

  protected readonly riskTrend = computed<TrendSeries>(() =>
    this.chartTransform.toRiskScoreTrend(this.windows())
  );

  protected readonly hasData = computed<boolean>(() => this.windows().length > 0);
  protected readonly elevatedWindowCount = computed<number>(
    () => this.windows().filter((w) => w.elevatedSuspicion).length
  );

  protected readonly chartBounds = CHART_BOUNDS;

  constructor() {
    effect(
      () => {
        const id = this.accountId();
        this.loadWindows(id);
      },
      { allowSignalWrites: true }
    );
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  private loadWindows(accountId: string): void {
    this.isLoading.set(true);
    this.hasError.set(false);
    this.errorMessage.set('');

    try {
      const result = this.windowAggregator.getWindowsForAccount(accountId, 24);
      this.windows.set(result);
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : 'Unable to load timeline data. Please try again.';
      this.hasError.set(true);
      this.errorMessage.set(msg);
      this.windows.set([]);
    } finally {
      this.isLoading.set(false);
    }
  }
}

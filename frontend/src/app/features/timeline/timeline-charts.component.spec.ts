import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TimelineChartsComponent } from './timeline-charts.component';
import { HourlyWindowAggregatorService } from '../../core/services/hourly-window-aggregator.service';
import { ChartDataTransformService, TrendSeries } from './chart-data-transform.service';
import { HourlyRiskWindow } from '../../shared/models/hourly-risk-window.model';
import { By } from '@angular/platform-browser';

// ── Helpers ──────────────────────────────────────────────────────────────────

function buildMockWindows(count: number, elevated = false): HourlyRiskWindow[] {
  return Array.from({ length: count }, (_, i) => ({
    hourStart: new Date(Date.UTC(2026, 3, 11, i)).toISOString(),
    transactionCount: i + 1,
    totalAmount: (i + 1) * 500,
    averageAmount: 500,
    windowRiskScore: elevated ? 0.8 : 0.2,
    elevatedSuspicion: elevated
  }));
}

function emptyTrend(): TrendSeries {
  return { points: [], polylinePoints: '', maxValue: 0, isEmpty: true };
}

function buildTrend(windows: HourlyRiskWindow[], elevated = false): TrendSeries {
  const points = windows.map((w, i) => ({
    svgX: 20 + i * 20,
    svgY: 50,
    label: `${String(i).padStart(2, '0')}:00`,
    value: w.totalAmount,
    elevated: w.elevatedSuspicion
  }));
  return {
    points,
    polylinePoints: points.map((p) => `${p.svgX},${p.svgY}`).join(' '),
    maxValue: Math.max(...windows.map((w) => w.totalAmount)),
    isEmpty: false
  };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('TimelineChartsComponent', () => {
  let fixture: ComponentFixture<TimelineChartsComponent>;
  let windowServiceSpy: jasmine.SpyObj<HourlyWindowAggregatorService>;
  let transformServiceSpy: jasmine.SpyObj<ChartDataTransformService>;

  function createComponent(accountId = 'ACC-001'): void {
    fixture = TestBed.createComponent(TimelineChartsComponent);
    fixture.componentRef.setInput('accountId', accountId);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    windowServiceSpy = jasmine.createSpyObj<HourlyWindowAggregatorService>(
      'HourlyWindowAggregatorService',
      ['getWindowsForAccount']
    );
    transformServiceSpy = jasmine.createSpyObj<ChartDataTransformService>(
      'ChartDataTransformService',
      ['toAmountTrend', 'toRiskScoreTrend']
    );

    // Default stubs return empty data
    windowServiceSpy.getWindowsForAccount.and.returnValue([]);
    transformServiceSpy.toAmountTrend.and.returnValue(emptyTrend());
    transformServiceSpy.toRiskScoreTrend.and.returnValue(emptyTrend());

    await TestBed.configureTestingModule({
      imports: [TimelineChartsComponent],
      providers: [
        { provide: HourlyWindowAggregatorService, useValue: windowServiceSpy },
        { provide: ChartDataTransformService, useValue: transformServiceSpy }
      ]
    }).compileComponents();
  });

  // ── Creation ─────────────────────────────────────────────────────────────

  it('creates the component', () => {
    createComponent();
    expect(fixture.componentInstance).toBeTruthy();
  });

  // ── Empty state ───────────────────────────────────────────────────────────

  it('shows the empty state when no windows are returned', () => {
    windowServiceSpy.getWindowsForAccount.and.returnValue([]);
    createComponent();

    const empty = fixture.debugElement.query(By.css('.timeline-state--empty'));
    expect(empty).toBeTruthy();
  });

  it('does not show charts when there are no windows', () => {
    windowServiceSpy.getWindowsForAccount.and.returnValue([]);
    createComponent();

    const grid = fixture.debugElement.query(By.css('.charts-grid'));
    expect(grid).toBeNull();
  });

  it('empty state has role="status" for accessibility', () => {
    windowServiceSpy.getWindowsForAccount.and.returnValue([]);
    createComponent();

    const el: HTMLElement = fixture.debugElement.query(By.css('.timeline-state--empty'))
      ?.nativeElement;
    expect(el?.getAttribute('role')).toBe('status');
  });

  // ── Error state ───────────────────────────────────────────────────────────

  it('shows error state when the aggregator throws', () => {
    windowServiceSpy.getWindowsForAccount.and.throwError('Load failed');
    createComponent();

    const errorEl = fixture.debugElement.query(By.css('.timeline-state--error'));
    expect(errorEl).toBeTruthy();
  });

  it('error state has role="alert" for accessibility', () => {
    windowServiceSpy.getWindowsForAccount.and.throwError('Load failed');
    createComponent();

    const el: HTMLElement = fixture.debugElement.query(By.css('.timeline-state--error'))
      ?.nativeElement;
    expect(el?.getAttribute('role')).toBe('alert');
  });

  it('displays the error message text', () => {
    windowServiceSpy.getWindowsForAccount.and.throwError(new Error('Data unavailable'));
    createComponent();

    const msg: HTMLElement = fixture.debugElement.query(By.css('.timeline-state__message'))
      ?.nativeElement;
    expect(msg?.textContent).toContain('Data unavailable');
  });

  it('does not show charts when there is an error', () => {
    windowServiceSpy.getWindowsForAccount.and.throwError('Error');
    createComponent();

    const grid = fixture.debugElement.query(By.css('.charts-grid'));
    expect(grid).toBeNull();
  });

  // ── Data rendering ────────────────────────────────────────────────────────

  it('shows charts grid when windows are available', () => {
    const windows = buildMockWindows(8);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows));
    transformServiceSpy.toRiskScoreTrend.and.returnValue(buildTrend(windows));

    createComponent();

    const grid = fixture.debugElement.query(By.css('.charts-grid'));
    expect(grid).toBeTruthy();
  });

  it('calls getWindowsForAccount with the provided accountId', () => {
    createComponent('ACC-042');

    expect(windowServiceSpy.getWindowsForAccount).toHaveBeenCalledWith('ACC-042', 24);
  });

  it('displays two chart panels when data is available', () => {
    const windows = buildMockWindows(4);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows));
    transformServiceSpy.toRiskScoreTrend.and.returnValue(buildTrend(windows));

    createComponent();

    const panels = fixture.debugElement.queryAll(By.css('.chart-panel'));
    expect(panels.length).toBe(2);
  });

  it('renders SVG elements for both charts', () => {
    const windows = buildMockWindows(4);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows));
    transformServiceSpy.toRiskScoreTrend.and.returnValue(buildTrend(windows));

    createComponent();

    const svgs = fixture.debugElement.queryAll(By.css('svg.trend-chart'));
    expect(svgs.length).toBe(2);
  });

  it('each chart SVG has role="img" for accessibility', () => {
    const windows = buildMockWindows(4);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows));
    transformServiceSpy.toRiskScoreTrend.and.returnValue(buildTrend(windows));

    createComponent();

    const svgs: HTMLElement[] = fixture.debugElement
      .queryAll(By.css('svg.trend-chart'))
      .map((d) => d.nativeElement);

    svgs.forEach((svg) => expect(svg.getAttribute('role')).toBe('img'));
  });

  it('each chart SVG has an aria-labelledby pointing to a <title> element', () => {
    const windows = buildMockWindows(4);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows));
    transformServiceSpy.toRiskScoreTrend.and.returnValue(buildTrend(windows));

    createComponent();

    const svgs: HTMLElement[] = fixture.debugElement
      .queryAll(By.css('svg.trend-chart'))
      .map((d) => d.nativeElement);

    svgs.forEach((svg) => {
      const labelId = svg.getAttribute('aria-labelledby');
      expect(labelId).toBeTruthy();
      // The referenced title element must exist in the document
      const title = fixture.nativeElement.querySelector(`#${labelId}`);
      expect(title).toBeTruthy();
    });
  });

  // ── Elevated suspicion markers ────────────────────────────────────────────

  it('renders elevated suspicion dot markers for flagged windows', () => {
    const windows = buildMockWindows(3, true);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows, false));
    const riskTrend = buildTrend(windows, true);
    transformServiceSpy.toRiskScoreTrend.and.returnValue(riskTrend);

    createComponent();

    const elevatedDots = fixture.debugElement.queryAll(
      By.css('.trend-chart__dot--elevated')
    );
    expect(elevatedDots.length).toBe(3);
  });

  it('does not render elevated dots when no windows are elevated', () => {
    const windows = buildMockWindows(3, false);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows));
    transformServiceSpy.toRiskScoreTrend.and.returnValue(buildTrend(windows, false));

    createComponent();

    const elevatedDots = fixture.debugElement.queryAll(
      By.css('.trend-chart__dot--elevated')
    );
    expect(elevatedDots.length).toBe(0);
  });

  // ── Reloads when accountId changes ────────────────────────────────────────

  it('reloads data when accountId input changes', () => {
    const windows1 = buildMockWindows(3);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows1);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows1));
    transformServiceSpy.toRiskScoreTrend.and.returnValue(buildTrend(windows1));

    createComponent('ACC-001');
    expect(windowServiceSpy.getWindowsForAccount).toHaveBeenCalledWith('ACC-001', 24);

    fixture.componentRef.setInput('accountId', 'ACC-002');
    fixture.detectChanges();

    expect(windowServiceSpy.getWindowsForAccount).toHaveBeenCalledWith('ACC-002', 24);
  });

  // ── Screen reader tables ──────────────────────────────────────────────────

  it('renders accessible data tables with sr-only class', () => {
    const windows = buildMockWindows(4);
    windowServiceSpy.getWindowsForAccount.and.returnValue(windows);
    transformServiceSpy.toAmountTrend.and.returnValue(buildTrend(windows));
    transformServiceSpy.toRiskScoreTrend.and.returnValue(buildTrend(windows));

    createComponent();

    const tables = fixture.debugElement.queryAll(By.css('table.sr-only'));
    expect(tables.length).toBe(2);
  });

  // ── Section labelling ─────────────────────────────────────────────────────

  it('wraps content in a section with aria-labelledby', () => {
    createComponent();

    const section: HTMLElement = fixture.debugElement.query(
      By.css('section[aria-labelledby]')
    )?.nativeElement;

    expect(section).toBeTruthy();
    const labelId = section.getAttribute('aria-labelledby');
    const heading = fixture.nativeElement.querySelector(`#${labelId}`);
    expect(heading?.textContent).toContain('24-Hour');
  });
});

import { TestBed } from '@angular/core/testing';
import {
  ChartDataTransformService,
  ChartPoint,
  TrendSeries,
  CHART_BOUNDS
} from './chart-data-transform.service';
import { HourlyRiskWindow } from '../../shared/models/hourly-risk-window.model';

// ── Helpers ──────────────────────────────────────────────────────────────────

function buildWindows(
  partials: Array<{
    hourStart: string;
    totalAmount: number;
    windowRiskScore: number;
    elevatedSuspicion: boolean;
    transactionCount?: number;
  }>
): HourlyRiskWindow[] {
  return partials.map((p) => ({
    hourStart: p.hourStart,
    transactionCount: p.transactionCount ?? 1,
    totalAmount: p.totalAmount,
    averageAmount: p.totalAmount,
    windowRiskScore: p.windowRiskScore,
    elevatedSuspicion: p.elevatedSuspicion
  }));
}

const BOTTOM_Y = CHART_BOUNDS.viewHeight - CHART_BOUNDS.paddingY; // 110
const TOP_Y = CHART_BOUNDS.paddingY; // 10

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ChartDataTransformService', () => {
  let service: ChartDataTransformService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChartDataTransformService);
  });

  // ── toAmountTrend ────────────────────────────────────────────────────────

  describe('toAmountTrend', () => {
    it('returns isEmpty=true and empty points for an empty windows array', () => {
      const result = service.toAmountTrend([]);

      expect(result.isEmpty).toBeTrue();
      expect(result.points).toEqual([]);
      expect(result.polylinePoints).toBe('');
      expect(result.maxValue).toBe(0);
    });

    it('returns correct point count matching windows array length', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 100, windowRiskScore: 0.2, elevatedSuspicion: false },
        { hourStart: '2026-04-11T07:00:00.000Z', totalAmount: 200, windowRiskScore: 0.3, elevatedSuspicion: false },
        { hourStart: '2026-04-11T08:00:00.000Z', totalAmount: 150, windowRiskScore: 0.4, elevatedSuspicion: false }
      ]);

      const result = service.toAmountTrend(windows);

      expect(result.points.length).toBe(3);
      expect(result.isEmpty).toBeFalse();
    });

    it('extracts correct hour labels from ISO timestamps', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 100, windowRiskScore: 0.2, elevatedSuspicion: false },
        { hourStart: '2026-04-11T13:00:00.000Z', totalAmount: 200, windowRiskScore: 0.3, elevatedSuspicion: false },
        { hourStart: '2026-04-11T23:00:00.000Z', totalAmount: 150, windowRiskScore: 0.5, elevatedSuspicion: false }
      ]);

      const result = service.toAmountTrend(windows);

      expect(result.points[0].label).toBe('06:00');
      expect(result.points[1].label).toBe('13:00');
      expect(result.points[2].label).toBe('23:00');
    });

    it('sets maxValue to the highest totalAmount', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 500, windowRiskScore: 0.2, elevatedSuspicion: false },
        { hourStart: '2026-04-11T07:00:00.000Z', totalAmount: 9800, windowRiskScore: 0.9, elevatedSuspicion: true },
        { hourStart: '2026-04-11T08:00:00.000Z', totalAmount: 200, windowRiskScore: 0.1, elevatedSuspicion: false }
      ]);

      const result = service.toAmountTrend(windows);

      expect(result.maxValue).toBe(9800);
    });

    it('places the maximum value point at the chart top (minY)', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 1000, windowRiskScore: 0.5, elevatedSuspicion: false }
      ]);

      const result = service.toAmountTrend(windows);

      expect(result.points[0].svgY).toBe(TOP_Y);
    });

    it('places all-zero amounts as flat line at chart bottom (maxY)', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 0, windowRiskScore: 0, elevatedSuspicion: false },
        { hourStart: '2026-04-11T07:00:00.000Z', totalAmount: 0, windowRiskScore: 0, elevatedSuspicion: false }
      ]);

      const result = service.toAmountTrend(windows);

      result.points.forEach((p) => expect(p.svgY).toBe(BOTTOM_Y));
    });

    it('produces a stable polylinePoints string for the same input', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 100, windowRiskScore: 0.2, elevatedSuspicion: false },
        { hourStart: '2026-04-11T07:00:00.000Z', totalAmount: 200, windowRiskScore: 0.3, elevatedSuspicion: false }
      ]);

      const first = service.toAmountTrend(windows);
      const second = service.toAmountTrend(windows);

      expect(first.polylinePoints).toBe(second.polylinePoints);
    });

    it('first point has X at paddingX and last point has X at viewWidth minus paddingX', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 100, windowRiskScore: 0.2, elevatedSuspicion: false },
        { hourStart: '2026-04-11T07:00:00.000Z', totalAmount: 200, windowRiskScore: 0.3, elevatedSuspicion: false }
      ]);

      const result = service.toAmountTrend(windows);

      expect(result.points[0].svgX).toBe(CHART_BOUNDS.paddingX);
      expect(result.points[1].svgX).toBe(CHART_BOUNDS.viewWidth - CHART_BOUNDS.paddingX);
    });

    it('does not mark amount points as elevated', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 9000, windowRiskScore: 0.9, elevatedSuspicion: true }
      ]);

      const result = service.toAmountTrend(windows);

      // Amount trend does not use elevatedSuspicion flag
      expect(result.points[0].elevated).toBeFalse();
    });

    it('handles a single-window input without errors', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T10:00:00.000Z', totalAmount: 500, windowRiskScore: 0.3, elevatedSuspicion: false }
      ]);

      const result = service.toAmountTrend(windows);

      expect(result.points.length).toBe(1);
      expect(result.isEmpty).toBeFalse();
      expect(result.polylinePoints).toContain(',');
    });
  });

  // ── toRiskScoreTrend ─────────────────────────────────────────────────────

  describe('toRiskScoreTrend', () => {
    it('returns isEmpty=true for an empty array', () => {
      const result = service.toRiskScoreTrend([]);

      expect(result.isEmpty).toBeTrue();
    });

    it('marks elevated suspicion windows correctly', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 100, windowRiskScore: 0.3, elevatedSuspicion: false },
        { hourStart: '2026-04-11T07:00:00.000Z', totalAmount: 200, windowRiskScore: 0.7, elevatedSuspicion: true },
        { hourStart: '2026-04-11T08:00:00.000Z', totalAmount: 900, windowRiskScore: 0.95, elevatedSuspicion: true }
      ]);

      const result = service.toRiskScoreTrend(windows);

      expect(result.points[0].elevated).toBeFalse();
      expect(result.points[1].elevated).toBeTrue();
      expect(result.points[2].elevated).toBeTrue();
    });

    it('places the maximum risk score point at chart top', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 100, windowRiskScore: 0.0, elevatedSuspicion: false },
        { hourStart: '2026-04-11T07:00:00.000Z', totalAmount: 200, windowRiskScore: 1.0, elevatedSuspicion: true }
      ]);

      const result = service.toRiskScoreTrend(windows);

      // Point with score 1.0 is the max, should be at TOP_Y
      expect(result.points[1].svgY).toBe(TOP_Y);
    });

    it('stores the raw windowRiskScore as the point value', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T08:00:00.000Z', totalAmount: 500, windowRiskScore: 0.72, elevatedSuspicion: true }
      ]);

      const result = service.toRiskScoreTrend(windows);

      expect(result.points[0].value).toBeCloseTo(0.72, 5);
    });

    it('handles 24 windows without error and returns 24 points', () => {
      const windows: HourlyRiskWindow[] = Array.from({ length: 24 }, (_, i) => ({
        hourStart: new Date(Date.UTC(2026, 3, 11, i)).toISOString(),
        transactionCount: i % 3,
        totalAmount: i * 100,
        averageAmount: i * 100,
        windowRiskScore: i / 24,
        elevatedSuspicion: i / 24 >= 0.5
      }));

      const result = service.toRiskScoreTrend(windows);

      expect(result.points.length).toBe(24);
      expect(result.isEmpty).toBeFalse();
    });

    it('produces a polylinePoints string with space-separated coordinate pairs', () => {
      const windows = buildWindows([
        { hourStart: '2026-04-11T06:00:00.000Z', totalAmount: 100, windowRiskScore: 0.2, elevatedSuspicion: false },
        { hourStart: '2026-04-11T07:00:00.000Z', totalAmount: 200, windowRiskScore: 0.8, elevatedSuspicion: true }
      ]);

      const result = service.toRiskScoreTrend(windows);

      // Should be two "x,y" pairs separated by a space
      const pairs = result.polylinePoints.split(' ');
      expect(pairs.length).toBe(2);
      pairs.forEach((pair) => expect(pair).toMatch(/^\d+(\.\d+)?,\d+(\.\d+)?$/));
    });

    it('orders points in the same order as the input windows array', () => {
      const hourStarts = [
        '2026-04-11T06:00:00.000Z',
        '2026-04-11T07:00:00.000Z',
        '2026-04-11T08:00:00.000Z'
      ];
      const windows = buildWindows(
        hourStarts.map((h, i) => ({
          hourStart: h,
          totalAmount: 100,
          windowRiskScore: i * 0.2,
          elevatedSuspicion: false
        }))
      );

      const result = service.toRiskScoreTrend(windows);

      expect(result.points[0].label).toBe('06:00');
      expect(result.points[1].label).toBe('07:00');
      expect(result.points[2].label).toBe('08:00');
    });
  });
});

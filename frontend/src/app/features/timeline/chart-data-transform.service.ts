import { Injectable } from '@angular/core';
import { HourlyRiskWindow } from '../../shared/models/hourly-risk-window.model';

/** A single plotted point in an SVG polyline chart. */
export interface ChartPoint {
  /** X coordinate within the SVG viewBox. */
  readonly svgX: number;
  /** Y coordinate within the SVG viewBox (0 = top). */
  readonly svgY: number;
  /** Human-readable hour label, e.g. "06:00". */
  readonly label: string;
  /** Raw data value at this point. */
  readonly value: number;
  /** True when the hourly window is flagged as elevated suspicion. */
  readonly elevated: boolean;
}

/** Derived chart data ready for SVG rendering. */
export interface TrendSeries {
  readonly points: ChartPoint[];
  /** Pre-computed SVG `points` attribute string, e.g. "20,90 60,45 ...". */
  readonly polylinePoints: string;
  readonly maxValue: number;
  readonly isEmpty: boolean;
}

/**
 * SVG chart viewport constants.
 * viewBox is "0 0 500 120" with 20px horizontal padding and 10px vertical padding.
 */
export const CHART_BOUNDS = {
  viewWidth: 500,
  viewHeight: 120,
  paddingX: 20,
  paddingY: 10
} as const;

/**
 * Transforms HourlyRiskWindow arrays into SVG-renderable TrendSeries objects.
 * All computations are pure and deterministic for the same input.
 */
@Injectable({ providedIn: 'root' })
export class ChartDataTransformService {

  /** Builds an amount trend series from hourly window total amounts. */
  toAmountTrend(windows: HourlyRiskWindow[]): TrendSeries {
    return this.buildSeries(
      windows,
      (w) => w.totalAmount,
      () => false
    );
  }

  /** Builds a risk score trend series from hourly window risk scores. */
  toRiskScoreTrend(windows: HourlyRiskWindow[]): TrendSeries {
    return this.buildSeries(
      windows,
      (w) => w.windowRiskScore,
      (w) => w.elevatedSuspicion
    );
  }

  // ── Private ────────────────────────────────────────────────────────────────

  private buildSeries(
    windows: HourlyRiskWindow[],
    getValue: (w: HourlyRiskWindow) => number,
    isElevated: (w: HourlyRiskWindow) => boolean
  ): TrendSeries {
    if (windows.length === 0) {
      return { points: [], polylinePoints: '', maxValue: 0, isEmpty: true };
    }

    const values = windows.map(getValue);
    const maxValue = Math.max(...values);

    const points: ChartPoint[] = windows.map((w, i) => {
      const svgX = this.toSvgX(i, windows.length);
      const svgY = this.toSvgY(getValue(w), maxValue);
      return {
        svgX,
        svgY,
        label: this.formatHourLabel(w.hourStart),
        value: getValue(w),
        elevated: isElevated(w)
      };
    });

    const polylinePoints = points
      .map((p) => `${p.svgX.toFixed(1)},${p.svgY.toFixed(1)}`)
      .join(' ');

    return { points, polylinePoints, maxValue, isEmpty: false };
  }

  private toSvgX(index: number, total: number): number {
    const drawableWidth = CHART_BOUNDS.viewWidth - 2 * CHART_BOUNDS.paddingX;
    if (total <= 1) return CHART_BOUNDS.paddingX + drawableWidth / 2;
    return CHART_BOUNDS.paddingX + (index / (total - 1)) * drawableWidth;
  }

  private toSvgY(value: number, maxValue: number): number {
    const drawableHeight = CHART_BOUNDS.viewHeight - 2 * CHART_BOUNDS.paddingY;
    const bottom = CHART_BOUNDS.viewHeight - CHART_BOUNDS.paddingY;
    if (maxValue === 0) return bottom;
    // Invert: high value → low SVG Y (toward top)
    return bottom - (value / maxValue) * drawableHeight;
  }

  /** Formats an ISO 8601 timestamp to a two-digit UTC hour label like "06:00". */
  private formatHourLabel(isoString: string): string {
    const d = new Date(isoString);
    const h = d.getUTCHours().toString().padStart(2, '0');
    return `${h}:00`;
  }
}

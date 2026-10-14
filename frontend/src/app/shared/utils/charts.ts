import type { EChartsCoreOption } from 'echarts/core';

import { formatMinor } from './money';

/**
 * Pure ECharts option builders (no DOM, no echarts runtime import): chart
 * components resolve the token palette from CSS and pass it in, so unit
 * tests pin the structure while the theme stays in styles.scss.
 */

export interface SpendSeriesPoint {
  period: string;
  ordered: number;
  invoiced: number;
  paid: number;
}

export interface SpendTheme {
  ordered: string;
  invoiced: string;
  paid: string;
  grid: string;
  text: string;
}

export interface DonutSliceInput {
  label: string;
  value: number;
}

/** Rounds up to a 1/2/2.5/5 × 10^n ceiling so axis tops read cleanly. */
export function niceCeil(value: number): number {
  if (!Number.isFinite(value) || value <= 0) {
    return 1;
  }
  const power = 10 ** Math.floor(Math.log10(value));
  for (const step of [1, 2, 2.5, 5, 10]) {
    if (value <= step * power) {
      return step * power;
    }
  }
  return 10 * power;
}

/** Compact axis label for minor-unit money: 1M, 250k, 950, 0. */
export function formatAxisMinor(minor: number | null | undefined): string {
  if (typeof minor !== 'number' || !Number.isFinite(minor) || minor <= 0) {
    return '0';
  }
  if (minor >= 1_000_000) {
    const rounded = Math.round(minor / 100_000) / 10;
    return `${rounded}M`;
  }
  if (minor >= 1_000) {
    return `${Math.round(minor / 1_000)}k`;
  }
  return `${Math.round(minor)}`;
}

const SPEND_SERIES = [
  { key: 'ordered' },
  { key: 'invoiced' },
  { key: 'paid' },
] as const;

export interface SpendLabels {
  ordered: string;
  invoiced: string;
  paid: string;
}

const EN_SPEND_LABELS: SpendLabels = { ordered: 'Ordered', invoiced: 'Invoiced', paid: 'Paid' };

/** Grouped ordered/invoiced/paid bars with axis tooltip and series focus. */
export function buildSpendOption(
  points: SpendSeriesPoint[],
  theme: SpendTheme,
  currency: string,
  animate: boolean,
  labels: SpendLabels = EN_SPEND_LABELS,
): EChartsCoreOption {
  const peak = points.reduce(
    (max, point) => Math.max(max, point.ordered, point.invoiced, point.paid),
    0,
  );
  const top = niceCeil(peak);
  const colors = { ordered: theme.ordered, invoiced: theme.invoiced, paid: theme.paid };
  return {
    animation: animate,
    animationDuration: 600,
    animationDurationUpdate: 400,
    grid: { left: 8, right: 12, top: 24, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      data: points.map((point) => point.period),
      axisLine: { lineStyle: { color: theme.grid } },
      axisTick: { show: false },
      axisLabel: { color: theme.text, fontSize: 11, interval: Math.max(0, Math.ceil(points.length / 8) - 1) },
    },
    yAxis: {
      type: 'value',
      max: top,
      interval: top / 4,
      splitLine: { lineStyle: { color: theme.grid } },
      axisLabel: { color: theme.text, fontSize: 11, formatter: (value: number) => formatAxisMinor(value) },
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown) => {
        const rows = Array.isArray(params) ? params : [params];
        const first = (rows[0] ?? {}) as { name?: string };
        const lines = rows.map((row) => {
          const cell = row as { marker?: string; seriesName?: string; value?: number };
          return `${cell.marker ?? ''} ${cell.seriesName}: ${formatMinor(Number(cell.value), currency)}`;
        });
        return [`<strong>${first.name ?? ''}</strong>`, ...lines].join('<br/>');
      },
    },
    series: SPEND_SERIES.map((column) => ({
      name: labels[column.key],
      type: 'bar',
      data: points.map((point) => point[column.key]),
      itemStyle: { color: colors[column.key], borderRadius: [3, 3, 0, 0] },
      barMaxWidth: 26,
      emphasis: { focus: 'series' },
    })),
  };
}

/** Allocation donut with center total and item tooltips. */
export function buildDonutOption(
  slices: DonutSliceInput[],
  palette: string[],
  surface: string,
  currency: string,
  animate: boolean,
): EChartsCoreOption {
  return {
    animation: animate,
    animationDuration: 600,
    animationDurationUpdate: 400,
    // No ECharts title: the center total renders as an HTML overlay in
    // DonutChartComponent. The canvas title box centers on the container
    // while the ring centers on the half-min-dimension, so long totals
    // drifted over the left arc; CSS grid centering cannot drift.
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const cell = params as { marker?: string; name?: string; value?: number };
        return `${cell.marker ?? ''} ${cell.name}: ${formatMinor(Number(cell.value), currency)}`;
      },
    },
    series: [
      {
        type: 'pie',
        radius: ['64%', '78%'],
        label: { show: false },
        emphasis: { scale: true, scaleSize: 4 },
        data: slices.map((slice, index) => ({
          name: slice.label,
          value: Math.max(0, slice.value),
          itemStyle: { color: palette[index % palette.length], borderColor: surface, borderWidth: 2 },
        })),
      },
    ],
  };
}

export interface DonutArc {
  /** Start offset as a fraction of the full ring. */
  start: number;
  /** Arc length as a fraction of the full ring. */
  length: number;
}

/** Start/length fractions per value, in order; zero and negative values take no arc. */
export function donutArcs(values: number[]): DonutArc[] {
  const total = values.reduce(
    (acc, value) => acc + (typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : 0),
    0,
  );
  if (total <= 0) {
    return values.map(() => ({ start: 0, length: 0 }));
  }
  let cursor = 0;
  return values.map((value) => {
    const length = typeof value === 'number' && Number.isFinite(value) && value > 0 ? value / total : 0;
    const arc = { start: cursor, length };
    cursor += length;
    return arc;
  });
}

/**
 * Donut segment under a gesture offset (dx, dy) from the ring center, or
 * null. Matches the ECharts pie defaults the donut is drawn with: data
 * order from the top, clockwise.
 */
export function segmentAt(values: number[], dx: number, dy: number): number | null {
  if (!Number.isFinite(dx) || !Number.isFinite(dy) || (dx === 0 && dy === 0)) {
    return null;
  }
  const turn = ((Math.atan2(dx, -dy) / (2 * Math.PI)) % 1 + 1) % 1;
  const arcs = donutArcs(values);
  const hit = arcs.findIndex((arc) => arc.length > 0 && turn >= arc.start && turn < arc.start + arc.length);
  return hit >= 0 ? hit : null;
}

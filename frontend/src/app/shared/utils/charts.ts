/**
 * Pure SVG-chart math (no DOM): axis rounding, tick generation and donut
 * fractions. Covered by charts.spec.ts — components only bind results.
 */

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

/** Evenly spaced ticks from 0 to top inclusive (count intervals). */
export function axisTicks(top: number, intervals = 4): number[] {
  if (!Number.isFinite(top) || top <= 0 || intervals < 1) {
    return [0];
  }
  const ticks: number[] = [];
  for (let i = 0; i <= intervals; i += 1) {
    ticks.push((top * i) / intervals);
  }
  return ticks;
}

/** Fraction of value against top, clamped to [0, 1]; non-numbers read 0. */
export function clamp01(value: number | null | undefined, top: number): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || top <= 0) {
    return 0;
  }
  return Math.min(1, Math.max(0, value / top));
}

/** Compact axis label for minor-unit money: 1.0M, 250k, 950, 0. */
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

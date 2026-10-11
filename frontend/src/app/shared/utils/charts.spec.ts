import { describe, expect, it } from 'vitest';

import { axisTicks, clamp01, donutArcs, formatAxisMinor, niceCeil } from './charts';

describe('charts', () => {
  it('rounds axis tops to clean steps', () => {
    expect(niceCeil(0)).toBe(1);
    expect(niceCeil(1_020_000)).toBe(2_000_000);
    expect(niceCeil(5_000_000)).toBe(5_000_000);
    expect(niceCeil(690_000)).toBe(1_000_000);
    expect(niceCeil(NaN)).toBe(1);
  });

  it('builds even ticks from zero to top', () => {
    expect(axisTicks(1_000_000, 4)).toEqual([0, 250_000, 500_000, 750_000, 1_000_000]);
    expect(axisTicks(0)).toEqual([0]);
  });

  it('clamps bar fractions', () => {
    expect(clamp01(500, 1000)).toBe(0.5);
    expect(clamp01(2000, 1000)).toBe(1);
    expect(clamp01(-5, 1000)).toBe(0);
    expect(clamp01(null, 1000)).toBe(0);
    expect(clamp01(5, 0)).toBe(0);
  });

  it('compacts axis money labels', () => {
    expect(formatAxisMinor(0)).toBe('0');
    expect(formatAxisMinor(null)).toBe('0');
    expect(formatAxisMinor(950)).toBe('950');
    expect(formatAxisMinor(250_000)).toBe('250k');
    expect(formatAxisMinor(1_020_000)).toBe('1M');
    expect(formatAxisMinor(2_660_000)).toBe('2.7M');
  });

  it('lays donut arcs end to end', () => {
    const arcs = donutArcs([50, 30, 20]);
    expect(arcs[0]).toEqual({ start: 0, length: 0.5 });
    expect(arcs[1].start).toBeCloseTo(0.5);
    expect(arcs[2].start + arcs[2].length).toBeCloseTo(1);
    expect(donutArcs([0, -1, NaN])).toEqual([
      { start: 0, length: 0 },
      { start: 0, length: 0 },
      { start: 0, length: 0 },
    ]);
  });
});

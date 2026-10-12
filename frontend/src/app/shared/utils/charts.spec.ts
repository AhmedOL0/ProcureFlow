import { describe, expect, it } from 'vitest';

import {
  buildDonutOption,
  buildSpendOption,
  formatAxisMinor,
  niceCeil,
  segmentAt,
} from './charts';

const THEME = {
  ordered: 'navy',
  invoiced: 'viridian',
  paid: 'copper',
  grid: 'grid',
  text: 'text',
};

describe('charts', () => {
  it('rounds axis tops to clean steps', () => {
    expect(niceCeil(0)).toBe(1);
    expect(niceCeil(1_020_000)).toBe(2_000_000);
    expect(niceCeil(5_000_000)).toBe(5_000_000);
    expect(niceCeil(690_000)).toBe(1_000_000);
    expect(niceCeil(NaN)).toBe(1);
  });

  it('compacts axis money labels', () => {
    expect(formatAxisMinor(0)).toBe('0');
    expect(formatAxisMinor(null)).toBe('0');
    expect(formatAxisMinor(950)).toBe('950');
    expect(formatAxisMinor(250_000)).toBe('250k');
    expect(formatAxisMinor(1_020_000)).toBe('1M');
    expect(formatAxisMinor(2_660_000)).toBe('2.7M');
  });

  it('builds grouped spend series with themed colors', () => {
    const option = buildSpendOption(
      [{ period: '2026-05', ordered: 1_020_000, invoiced: 1_020_000, paid: 500_000 }],
      THEME,
      'MAD',
      true,
    );
    expect(option.animation).toBe(true);
    const series = option.series as { name: string; data: number[]; itemStyle: { color: string } }[];
    expect(series.map((row) => row.name)).toEqual(['Ordered', 'Invoiced', 'Paid']);
    expect(series[0].data).toEqual([1_020_000]);
    expect(series[0].itemStyle.color).toBe('navy');
    expect(series[2].itemStyle.color).toBe('copper');
    const yAxis = option.yAxis as { max: number; interval: number };
    expect(yAxis.max).toBe(2_000_000);
    expect(yAxis.interval).toBe(500_000);
    const xAxis = option.xAxis as { data: string[] };
    expect(xAxis.data).toEqual(['2026-05']);
  });

  it('disables animation for reduced motion', () => {
    const option = buildSpendOption([], THEME, 'MAD', false);
    expect(option.animation).toBe(false);
  });

  it('builds donut data with palette cycling and center total', () => {
    const option = buildDonutOption(
      [
        { label: 'Hardware', value: 4_245_000 },
        { label: 'Office', value: 1_890_000 },
      ],
      ['navy', 'viridian'],
      'card',
      'MAD',
      true,
    );
    const series = option.series as {
      data: { name: string; value: number; itemStyle: { color: string; borderColor: string } }[];
    }[];
    expect(series).toHaveLength(1);
    expect(series[0].data.map((row) => row.name)).toEqual(['Hardware', 'Office']);
    expect(series[0].data[0].itemStyle).toEqual({ color: 'navy', borderColor: 'card', borderWidth: 2 });
    expect(series[0].data[1].itemStyle.color).toBe('viridian');
    // The center total renders as HTML in DonutChartComponent, never as
    // an ECharts title (the canvas title box drifted over the ring).
    expect(option.title).toBeUndefined();
  });

  it('keeps the ring clear for the HTML center total', () => {
    const option = buildDonutOption([{ label: 'Hardware', value: 4_245_000 }], ['navy'], 'card', 'MAD', true);
    const series = option.series as { radius: [string, string] }[];
    // Wide hole so long MAD totals never touch the ring.
    expect(series[0].radius).toEqual(['64%', '78%']);
  });

  it('maps ring gestures to segments from the top, clockwise', () => {
    const values = [50, 30, 20];
    expect(segmentAt(values, 0, -1)).toBe(0);
    expect(segmentAt(values, 1, 0)).toBe(0);
    expect(segmentAt(values, 0, 1)).toBe(1);
    expect(segmentAt(values, -1, 0)).toBe(1);
    expect(segmentAt(values, 0, 0)).toBeNull();
    expect(segmentAt([100], 0.3, 0.9)).toBe(0);
  });
});

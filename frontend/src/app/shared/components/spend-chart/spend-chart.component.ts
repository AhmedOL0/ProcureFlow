import { Component, computed, input } from '@angular/core';

import { formatMinor } from '../../utils/money';
import { axisTicks, clamp01, formatAxisMinor, niceCeil } from '../../utils/charts';

export interface SpendPoint {
  period: string;
  ordered: number;
  invoiced: number;
  paid: number;
}

interface PlacedBar {
  x: number;
  y: number;
  width: number;
  height: number;
  css: string;
  title: string;
}

interface GridLine {
  y: number;
  label: string;
}

/**
 * Grouped spend bars (ordered / invoiced / paid per month) as dependency-free
 * SVG: value gridlines, readable month labels, native tooltips, and an
 * sr-only data table so screen readers get the same figures. The graphic
 * itself is aria-hidden — the table and legend carry the meaning.
 */
@Component({
  selector: 'app-spend-chart',
  templateUrl: './spend-chart.component.html',
  styleUrl: './spend-chart.component.scss',
})
export class SpendChartComponent {
  readonly months = input.required<SpendPoint[]>();
  readonly currency = input('MAD');

  protected readonly formatMinor = formatMinor;

  private static readonly WIDTH = 720;
  private static readonly HEIGHT = 300;
  private static readonly PAD_LEFT = 56;
  private static readonly PAD_TOP = 12;
  private static readonly PAD_BOTTOM = 30;

  protected readonly width = SpendChartComponent.WIDTH;
  protected readonly height = SpendChartComponent.HEIGHT;

  private readonly top = computed(() => {
    const peak = this.months().reduce(
      (max, month) => Math.max(max, month.ordered, month.invoiced, month.paid),
      0,
    );
    return niceCeil(peak);
  });

  private readonly plotWidth = computed(
    () => SpendChartComponent.WIDTH - SpendChartComponent.PAD_LEFT - 12,
  );
  private readonly plotHeight = computed(
    () => SpendChartComponent.HEIGHT - SpendChartComponent.PAD_TOP - SpendChartComponent.PAD_BOTTOM,
  );

  protected readonly grid = computed<GridLine[]>(() => {
    const top = this.top();
    const base = SpendChartComponent.HEIGHT - SpendChartComponent.PAD_BOTTOM;
    return axisTicks(top).map((tick) => ({
      y: base - (tick / top) * this.plotHeight(),
      label: formatAxisMinor(tick),
    }));
  });

  protected readonly bars = computed<PlacedBar[]>(() => {
    const rows = this.months();
    const top = this.top();
    const base = SpendChartComponent.HEIGHT - SpendChartComponent.PAD_BOTTOM;
    const groupWidth = rows.length > 0 ? this.plotWidth() / rows.length : 0;
    const barWidth = Math.min(24, groupWidth / 4.2);
    const gap = 4;
    const series = [
      { key: 'ordered', css: 'spend-bar--ordered', label: 'Ordered' },
      { key: 'invoiced', css: 'spend-bar--invoiced', label: 'Invoiced' },
      { key: 'paid', css: 'spend-bar--paid', label: 'Paid' },
    ] as const;
    const span = barWidth * series.length + gap * (series.length - 1);
    const currency = this.currency();
    return rows.flatMap((row, index) => {
      const groupX = SpendChartComponent.PAD_LEFT + groupWidth * index + (groupWidth - span) / 2;
      return series.map((column, position) => {
        const value = row[column.key] as number;
        const fraction = clamp01(value, top);
        const height = Math.max(fraction > 0 ? 2 : 0, fraction * this.plotHeight());
        return {
          x: groupX + position * (barWidth + gap),
          y: base - height,
          width: barWidth,
          height,
          css: column.css,
          title: `${column.label} ${row.period}: ${formatMinor(value, currency)}`,
        };
      });
    });
  });

  protected readonly monthLabels = computed(() => {
    const rows = this.months();
    const step = Math.max(1, Math.ceil(rows.length / 8));
    const groupWidth = rows.length > 0 ? this.plotWidth() / rows.length : 0;
    return rows
      .map((row, index) => ({ row, index }))
      .filter(({ index }) => index % step === 0)
      .map(({ row, index }) => ({
        x: SpendChartComponent.PAD_LEFT + groupWidth * index + groupWidth / 2,
        y: SpendChartComponent.HEIGHT - SpendChartComponent.PAD_BOTTOM + 20,
        text: row.period,
      }));
  });

  protected readonly totals = computed(() => {
    const rows = this.months();
    const sum = (pick: (row: SpendPoint) => number): number =>
      rows.reduce((acc, row) => acc + (pick(row) ?? 0), 0);
    return {
      ordered: sum((row) => row.ordered),
      invoiced: sum((row) => row.invoiced),
      paid: sum((row) => row.paid),
    };
  });
}

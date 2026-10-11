import { Component, computed, input } from '@angular/core';

import { formatMinor } from '../../utils/money';
import { donutArcs } from '../../utils/charts';

export interface DonutSlice {
  label: string;
  value: number;
}

interface PlacedArc {
  dash: string;
  offset: number;
  css: string;
}

interface LegendRow extends DonutSlice {
  css: string;
  share: string;
}

/**
 * Category allocation donut: ring segments sized by share, total in the
 * middle, and a visible legend carrying every value — the legend is the
 * accessible record, so the graphic itself is aria-hidden.
 */
@Component({
  selector: 'app-donut-chart',
  templateUrl: './donut-chart.component.html',
  styleUrl: './donut-chart.component.scss',
})
export class DonutChartComponent {
  readonly slices = input.required<DonutSlice[]>();
  readonly currency = input('MAD');

  protected readonly formatMinor = formatMinor;

  private static readonly RADIUS = 70;
  private static readonly CIRCUMFERENCE = 2 * Math.PI * DonutChartComponent.RADIUS;

  protected readonly total = computed(() =>
    this.slices().reduce((acc, slice) => acc + (slice.value > 0 ? slice.value : 0), 0),
  );

  protected readonly arcs = computed<PlacedArc[]>(() => {
    const ring = DonutChartComponent.CIRCUMFERENCE;
    return donutArcs(this.slices().map((slice) => slice.value)).map((arc, index) => ({
      dash: `${arc.length * ring} ${ring}`,
      offset: -arc.start * ring,
      css: `donut-segment--${index % 6}`,
    }));
  });

  protected readonly legend = computed<LegendRow[]>(() => {
    const total = this.total();
    return this.slices().map((slice, index) => ({
      ...slice,
      css: `donut-segment--${index % 6}`,
      share: total > 0 && slice.value > 0 ? `${((slice.value / total) * 100).toFixed(1)}%` : '—',
    }));
  });
}

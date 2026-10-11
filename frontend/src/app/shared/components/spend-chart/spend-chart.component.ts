import {
  AfterViewInit,
  Component,
  effect,
  ElementRef,
  inject,
  input,
  OnDestroy,
  output,
  PLATFORM_ID,
  viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { init as initChart, use, type ECharts } from 'echarts/core';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

import { formatMinor } from '../../utils/money';
import { buildSpendOption, type SpendSeriesPoint } from '../../utils/charts';

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

export interface SpendPoint {
  period: string;
  ordered: number;
  invoiced: number;
  paid: number;
}

/**
 * Grouped spend bars (ordered / invoiced / paid per month) on ECharts:
 * entrance + update animations, axis tooltips with exact figures, series
 * focus on hover, and click-to-drill per month. The drill listens at the
 * zrender level and resolves the hit with convertFromPixel: the
 * ECharts-level 'click' forwarding proved unreliable in diagnosis (events
 * observable at zrender never reached chart.on handlers). The canvas is
 * aria-hidden — the legend names the series and the sr-only table carries
 * every figure, so assistive tech loses nothing.
 */
@Component({
  selector: 'app-spend-chart',
  templateUrl: './spend-chart.component.html',
  styleUrl: './spend-chart.component.scss',
})
export class SpendChartComponent implements AfterViewInit, OnDestroy {
  readonly months = input.required<SpendPoint[]>();
  readonly currency = input('MAD');
  readonly monthSelected = output<string>();

  protected readonly formatMinor = formatMinor;

  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private chart: ECharts | null = null;
  private observer: ResizeObserver | null = null;

  constructor() {
    effect(() => {
      this.months();
      this.currency();
      this.render();
    });
  }

  ngAfterViewInit(): void {
    if (!this.browser) {
      return;
    }
    this.chart = initChart(this.canvas().nativeElement);
    // Drill-down listens at the zrender level and resolves the hit with
    // convertFromPixel: it fires for every canvas gesture, while the
    // ECharts-level 'click' forwarding proved unreliable in diagnosis.
    this.chart.getZr().on('click', (event) => {
      const point = pixelOf(event);
      if (!point || !this.chart) {
        return;
      }
      const index = monthIndexAt(this.chart, point);
      const hit = index === null ? undefined : this.months()[index];
      if (hit) {
        this.monthSelected.emit(hit.period);
      }
    });
    this.render();
    this.observer = new ResizeObserver(() => this.chart?.resize());
    this.observer.observe(this.canvas().nativeElement);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    this.observer = null;
    this.chart?.dispose();
    this.chart = null;
  }

  private render(): void {
    if (!this.chart) {
      return;
    }
    const theme = readSpendTheme();
    this.chart.setOption(
      buildSpendOption(
        this.months().map((month) => ({
          period: month.period,
          ordered: month.ordered,
          invoiced: month.invoiced,
          paid: month.paid,
        }) satisfies SpendSeriesPoint),
        theme,
        this.currency(),
        !prefersReducedMotion(),
      ),
    );
  }
}

/** Token palette resolved from CSS so fills track styles.scss, never copies. */
function readSpendTheme(): { ordered: string; invoiced: string; paid: string; grid: string; text: string } {
  const css = getComputedStyle(document.documentElement);
  const pick = (name: string): string => css.getPropertyValue(name).trim();
  return {
    ordered: pick('--pf-navy'),
    invoiced: pick('--pf-blue'),
    paid: pick('--pf-copper'),
    grid: pick('--pf-border'),
    text: pick('--pf-slate'),
  };
}

function prefersReducedMotion(): boolean {
  return typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function pixelOf(event: unknown): [number, number] | null {
  const point = event as { offsetX?: unknown; offsetY?: unknown };
  if (typeof point.offsetX !== 'number' || typeof point.offsetY !== 'number') {
    return null;
  }
  return [point.offsetX, point.offsetY];
}

/** Month-group index under a canvas gesture, or null outside the groups. */
function monthIndexAt(chart: ECharts, point: [number, number]): number | null {
  let resolved: unknown;
  try {
    resolved = chart.convertFromPixel({ seriesIndex: 0 }, point);
  } catch {
    return null;
  }
  const index = Array.isArray(resolved) ? resolved[0] : null;
  if (typeof index !== 'number' || !Number.isFinite(index)) {
    return null;
  }
  return Math.round(index);
}

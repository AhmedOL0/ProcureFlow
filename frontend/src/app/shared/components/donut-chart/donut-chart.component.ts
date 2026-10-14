import {
  AfterViewInit,
  Component,
  computed,
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
import { PieChart } from 'echarts/charts';
import { TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

import { formatMinor } from '../../utils/money';
import { buildDonutOption, segmentAt } from '../../utils/charts';
import { ThemeService } from '../../../core/theme/theme.service';

use([PieChart, TooltipComponent, CanvasRenderer]);

export interface DonutSlice {
  label: string;
  value: number;
}

interface LegendRow extends DonutSlice {
  css: string;
  share: string;
}

/**
 * Category allocation donut on ECharts: animated segments, item tooltips,
 * click-to-filter per category. The drill listens at the zrender level and
 * resolves the hit geometrically (see spend-chart): the ECharts-level
 * 'click' forwarding proved unreliable in diagnosis, and pies do not
 * support convertFromPixel, hence the angle math. The visible legend
 * carries every value, so the aria-hidden canvas costs assistive tech
 * nothing.
 */
@Component({
  selector: 'app-donut-chart',
  templateUrl: './donut-chart.component.html',
  styleUrl: './donut-chart.component.scss',
})
export class DonutChartComponent implements AfterViewInit, OnDestroy {
  readonly slices = input.required<DonutSlice[]>();
  readonly currency = input('MAD');
  readonly categorySelected = output<string>();

  protected readonly formatMinor = formatMinor;

  protected readonly total = computed(() =>
    this.slices().reduce((acc, slice) => acc + (slice.value > 0 ? slice.value : 0), 0),
  );

  protected readonly legend = computed<LegendRow[]>(() => {
    const total = this.total();
    return this.slices().map((slice, index) => ({
      ...slice,
      css: `donut-segment--${index % 6}`,
      share: total > 0 && slice.value > 0 ? `${((slice.value / total) * 100).toFixed(1)}%` : 'N/A',
    }));
  });

  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly themes = inject(ThemeService);
  private chart: ECharts | null = null;
  private observer: ResizeObserver | null = null;

  constructor() {
    effect(() => {
      this.slices();
      this.currency();
      // Theme flip re-reads the token palette from CSS in render().
      this.themes.theme();
      this.render();
    });
  }

  ngAfterViewInit(): void {
    if (!this.browser) {
      return;
    }
    this.chart = initChart(this.canvas().nativeElement);
    // Same zrender-level drill as the spend chart (see there): resolve the
    // hit with convertFromPixel and ignore gestures off the ring.
    this.chart.getZr().on('click', (event) => {
      const point = pixelOf(event);
      if (!point || !this.chart) {
        return;
      }
      const rows = this.slices();
      const index = sliceIndexAt(this.chart, rows.map((row) => row.value), point);
      const hit = index !== null ? rows[index] : undefined;
      if (hit) {
        this.categorySelected.emit(hit.label);
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
    const css = getComputedStyle(document.documentElement);
    const pick = (name: string): string => css.getPropertyValue(name).trim();
    this.chart.setOption(
      buildDonutOption(
        this.slices(),
        [
          pick('--pf-navy'),
          pick('--pf-blue'),
          pick('--pf-teal'),
          pick('--pf-copper'),
          pick('--pf-slate'),
          pick('--pf-info-text'),
        ],
        pick('--pf-card'),
        this.currency(),
        !prefersReducedMotion(),
      ),
    );
  }
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

/**
 * Donut segment under a canvas gesture, or null off the ring. The ring
 * occupies 64–78% of the half-min-dimension around the canvas center
 * (mirroring the option radii, with a small tolerance for fat fingers).
 * ECharts pies do not support convertFromPixel, so the angle maps through
 * the same cumulative fractions the ring is drawn from: data order from
 * the top, clockwise (ECharts pie defaults).
 */
function sliceIndexAt(chart: ECharts, values: number[], point: [number, number]): number | null {
  const halfMin = Math.min(chart.getWidth(), chart.getHeight()) / 2;
  if (halfMin <= 0) {
    return null;
  }
  const dx = point[0] - chart.getWidth() / 2;
  const dy = point[1] - chart.getHeight() / 2;
  const distance = Math.hypot(dx, dy);
  if (distance < halfMin * 0.61 || distance > halfMin * 0.81) {
    return null;
  }
  return segmentAt(values, dx, dy);
}

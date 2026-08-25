import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { ArcElement, Chart, DoughnutController, Legend, Tooltip } from 'chart.js';
import { ThemeService } from '../../core/theme/theme.service';

Chart.register(ArcElement, DoughnutController, Legend, Tooltip);

export interface DonutChartSlice {
  label: string;
  value: number;
}

const CATEGORICAL_TOKENS = [
  '--vxt-cat-1',
  '--vxt-cat-2',
  '--vxt-cat-3',
  '--vxt-cat-4',
  '--vxt-cat-5',
  '--vxt-cat-6',
  '--vxt-cat-7',
  '--vxt-cat-8',
];

/** Thin canvas-based donut chart. Slice colors cycle the `--vxt-cat-*` tokens only. */
@Component({
  selector: 'vxt-donut-chart',
  templateUrl: './donut-chart.component.html',
  styleUrl: './donut-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DonutChartComponent implements AfterViewInit, OnDestroy {
  readonly slices = input.required<DonutChartSlice[]>();

  private readonly theme = inject(ThemeService);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart: Chart | null = null;

  constructor() {
    effect(() => {
      const slices = this.slices();
      this.theme.isDark();
      if (!this.chart) {
        return;
      }
      const colors = this.resolveColors(slices.length);
      this.chart.data.labels = slices.map((slice) => slice.label);
      this.chart.data.datasets[0].data = slices.map((slice) => slice.value);
      this.chart.data.datasets[0].backgroundColor = colors;
      const legend = this.chart.options.plugins?.legend;
      if (legend && legend.labels) {
        legend.labels.color = this.resolveTextColor();
      }
      this.chart.update();
    });
  }

  ngAfterViewInit(): void {
    const canvas = this.canvasRef().nativeElement;
    if (!canvas.getContext('2d')) {
      // No 2D context available (e.g. a test environment without a canvas
      // backend). Render nothing instead of letting Chart.js throw.
      return;
    }
    const slices = this.slices();
    this.chart = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: slices.map((slice) => slice.label),
        datasets: [
          {
            data: slices.map((slice) => slice.value),
            backgroundColor: this.resolveColors(slices.length),
            borderWidth: 0,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '68%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: this.resolveTextColor(), boxWidth: 10, boxHeight: 10, padding: 12 },
          },
        },
      },
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private resolveColors(count: number): string[] {
    const style = getComputedStyle(this.canvasRef().nativeElement);
    return Array.from(
      { length: count },
      (_, index) => style.getPropertyValue(CATEGORICAL_TOKENS[index % CATEGORICAL_TOKENS.length]).trim() || '#2a78d6',
    );
  }

  private resolveTextColor(): string {
    const value = getComputedStyle(this.canvasRef().nativeElement).getPropertyValue('--vxt-text-secondary').trim();
    return value || '#52514e';
  }
}

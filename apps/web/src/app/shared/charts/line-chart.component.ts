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
import {
  CategoryScale,
  Chart,
  Filler,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js';
import { ThemeService } from '../../core/theme/theme.service';

Chart.register(CategoryScale, LinearScale, LineController, LineElement, PointElement, Filler, Tooltip);

export interface LineChartPoint {
  label: string;
  value: number;
}

/** Thin canvas-based line chart. Series color comes from a `--vxt-cat-*` token only. */
@Component({
  selector: 'vxt-line-chart',
  templateUrl: './line-chart.component.html',
  styleUrl: './line-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineChartComponent implements AfterViewInit, OnDestroy {
  readonly points = input.required<LineChartPoint[]>();
  readonly colorToken = input('--vxt-cat-1');

  private readonly theme = inject(ThemeService);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart: Chart | null = null;

  constructor() {
    effect(() => {
      const points = this.points();
      this.theme.isDark();
      if (!this.chart) {
        return;
      }
      const color = this.resolveColor();
      this.chart.data.labels = points.map((point) => point.label);
      this.chart.data.datasets[0].data = points.map((point) => point.value);
      this.chart.data.datasets[0].borderColor = color;
      this.chart.data.datasets[0].backgroundColor = `${color}26`;
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
    const color = this.resolveColor();
    this.chart = new Chart(canvas, {
      type: 'line',
      data: {
        labels: this.points().map((point) => point.label),
        datasets: [
          {
            data: this.points().map((point) => point.value),
            borderColor: color,
            backgroundColor: `${color}26`,
            borderWidth: 2,
            fill: true,
            tension: 0.35,
            pointRadius: 0,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { grid: { display: false } },
          y: { beginAtZero: true, grid: { color: this.resolveColor('--vxt-gridline') } },
        },
      },
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private resolveColor(token = this.colorToken()): string {
    const value = getComputedStyle(this.canvasRef().nativeElement).getPropertyValue(token).trim();
    return value || '#2a78d6';
  }
}

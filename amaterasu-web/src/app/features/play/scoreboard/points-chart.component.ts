import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

export type SeriesTone = 'you' | 'o1' | 'o2' | 'o3';

export interface ChartSeries {
  name: string;
  tone: SeriesTone;
  /** Running totals; each holds until the next one. */
  points: { at: number; total: number }[];
}

const W = 820;
const LEFT = 44;
const RIGHT = 720;
const TOP = 16;
const BOTTOM = 236;
const SWATCH: Record<SeriesTone, string> = { you: 'var(--accent)', o1: 'var(--text-2)', o2: 'var(--text-3)', o3: 'var(--border-2)' };

function niceStep(max: number): number {
  const rough = Math.max(1, max) / 4;
  const power = 10 ** Math.floor(Math.log10(rough));
  return [1, 2, 2.5, 5, 10].map(f => f * power).find(step => step >= rough) ?? rough;
}

/** Totals at a moment: the last point at or before it. */
function totalAt(series: ChartSeries, at: number): number {
  let total = 0;
  for (const point of series.points) {
    if (point.at > at) break;
    total = point.total;
  }
  return total;
}

/** Points over time as step lines, with a hover readout. Drawn in SVG so it follows the theme tokens. */
@Component({
  selector: 'app-points-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      <svg viewBox="0 0 820 270" role="img" [attr.aria-label]="label()" (mousemove)="hover($event)" (mouseleave)="hoverAt.set(null)">
        @for (tick of yTicks(); track tick.value) {
          <line class="gridl" [attr.x1]="left" [attr.x2]="right" [attr.y1]="tick.y" [attr.y2]="tick.y" />
          <text class="axis" x="36" [attr.y]="tick.y + 4" text-anchor="end">{{ tick.label }}</text>
        }
        @for (tick of xTicks(); track tick.x) {
          <text class="axis" [attr.x]="tick.x" y="258" text-anchor="middle">{{ tick.label }}</text>
        }
        @if (hoverX() !== null) {
          <line class="xh" [attr.x1]="hoverX()" [attr.x2]="hoverX()" y1="16" y2="236" />
        }
        @for (line of lines(); track line.name) {
          <polyline class="ln" [class]="'ln ' + line.tone" [attr.points]="line.path" />
          <text class="endl" [class]="'endl ' + line.tone" [attr.x]="right + 10" [attr.y]="line.labelY">{{ line.name }}</text>
        }
        @for (mark of marks(); track $index) {
          <circle class="mk" [attr.cx]="mark.x" [attr.cy]="mark.y" r="4.5" />
        }
      </svg>
      @if (tip(); as t) {
        <div class="tip" [style.left.%]="t.left">
          <div class="tip-time">{{ t.time }}</div>
          @for (row of t.rows; track row.name) {
            <div class="row between tip-row"><span class="row sw-row"><span class="sw" [style.background]="row.swatch"></span>{{ row.name }}</span><span class="num">{{ row.value }}</span></div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .wrap { position: relative; }
    svg { width: 100%; height: auto; display: block; cursor: crosshair; }
    .ln { fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
    .ln.you { stroke: var(--accent); stroke-width: 2.5; }
    .ln.o1 { stroke: var(--text-2); }
    .ln.o2 { stroke: var(--text-3); }
    .ln.o3 { stroke: var(--border-2); }
    .gridl { stroke: var(--border); stroke-width: 1; }
    .axis { fill: var(--text-3); font-size: 11px; font-family: var(--font-sans); }
    .endl { fill: var(--text-2); font-size: 11.5px; font-weight: 600; font-family: var(--font-sans); }
    .endl.you { fill: var(--text); }
    .mk { fill: var(--accent); stroke: var(--surface); stroke-width: 2; }
    .xh { stroke: var(--text-3); stroke-width: 1; stroke-dasharray: 3 3; }
    .tip { position: absolute; top: 8px; transform: translateX(-50%); background: var(--surface-3); border: 1px solid var(--border-2);
      border-radius: 8px; padding: 8px 10px; box-shadow: var(--shadow); font-size: 12.5px; min-width: 150px; pointer-events: none; }
    .tip-time { font-weight: 600; margin-bottom: 4px; }
    .tip-row { gap: 14px; flex-wrap: nowrap; }
    .sw-row { gap: 6px; flex-wrap: nowrap; }
    .sw { display: inline-block; width: 14px; height: 3px; border-radius: 2px; }
  `,
})
export class PointsChartComponent {
  readonly series = input.required<ChartSeries[]>();
  readonly label = input('Points over time');

  protected readonly left = LEFT;
  protected readonly right = RIGHT;
  protected readonly hoverAt = signal<number | null>(null);

  private readonly range = computed(() => {
    const times = this.series().flatMap(series => series.points.map(point => point.at));
    const totals = this.series().flatMap(series => series.points.map(point => point.total));
    const start = times.length ? Math.min(...times) : Date.now() - 3600_000;
    const end = Math.max(Date.now(), ...(times.length ? times : [start + 1]));
    const step = niceStep(Math.max(1, ...totals));
    const max = Math.max(step, Math.ceil(Math.max(0, ...totals) / step) * step);
    const min = Math.min(0, Math.floor(Math.min(0, ...totals) / step) * step);
    return { start, end: end > start ? end : start + 1, min, max, step };
  });

  private x(at: number): number {
    const { start, end } = this.range();
    return LEFT + ((at - start) / (end - start)) * (RIGHT - LEFT);
  }

  private y(total: number): number {
    const { min, max } = this.range();
    return BOTTOM - ((total - min) / (max - min || 1)) * (BOTTOM - TOP);
  }

  protected readonly yTicks = computed(() => {
    const { min, max, step } = this.range();
    const ticks = [];
    for (let value = min; value <= max + step / 2; value += step) {
      ticks.push({ value, y: +this.y(value).toFixed(1), label: value.toLocaleString('en-US') });
    }
    return ticks;
  });

  protected readonly xTicks = computed(() => {
    const { start, end } = this.range();
    const multiDay = end - start > 36 * 3600_000;
    return Array.from({ length: 7 }, (_, i) => {
      const at = start + ((end - start) * i) / 6;
      const date = new Date(at);
      const label = multiDay
        ? date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
        : date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
      return { x: +this.x(at).toFixed(1), label };
    });
  });

  protected readonly lines = computed(() => {
    const { end } = this.range();
    const lines = this.series().map(series => {
      const coords: string[] = [];
      let last = 0;
      series.points.forEach((point, i) => {
        const x = this.x(point.at).toFixed(1);
        if (i > 0) coords.push(`${x},${this.y(last).toFixed(1)}`);
        coords.push(`${x},${this.y(point.total).toFixed(1)}`);
        last = point.total;
      });
      coords.push(`${this.x(end).toFixed(1)},${this.y(last).toFixed(1)}`);
      return { name: series.name, tone: series.tone, path: coords.join(' '), endY: this.y(last), labelY: 0 };
    });
    let previous = -100;
    [...lines].sort((a, b) => a.endY - b.endY).forEach(line => {
      line.labelY = Math.max(line.endY + 4, previous + 14);
      previous = line.labelY;
    });
    return lines;
  });

  protected readonly marks = computed(() => {
    const you = this.series().find(series => series.tone === 'you');
    if (!you || you.points.length > 40) return [];
    return you.points.map(point => ({ x: +this.x(point.at).toFixed(1), y: +this.y(point.total).toFixed(1) }));
  });

  protected readonly hoverX = computed(() => {
    const at = this.hoverAt();
    return at === null ? null : +this.x(at).toFixed(1);
  });

  protected readonly tip = computed(() => {
    const at = this.hoverAt();
    if (at === null) return null;
    const rows = this.series()
      .map(series => ({ name: series.name, swatch: SWATCH[series.tone], total: totalAt(series, at) }))
      .sort((a, b) => b.total - a.total)
      .map(row => ({ ...row, value: row.total.toLocaleString('en-US') }));
    const left = Math.min(82, Math.max(12, (this.x(at) / W) * 100));
    const time = new Date(at).toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
    return { left, time, rows };
  });

  protected hover(event: MouseEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const box = svg.getBoundingClientRect();
    const x = ((event.clientX - box.left) / box.width) * W;
    if (x < LEFT || x > RIGHT) {
      this.hoverAt.set(null);
      return;
    }
    const { start, end } = this.range();
    this.hoverAt.set(start + ((x - LEFT) / (RIGHT - LEFT)) * (end - start));
  }
}

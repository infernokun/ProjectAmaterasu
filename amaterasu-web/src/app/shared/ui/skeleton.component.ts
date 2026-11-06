import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Loading placeholder. `variant="lines"` draws text lines, `"card"` a card with a few lines,
 * `"rows"` table rows. `count` repeats it.
 */
@Component({
  selector: 'app-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-busy': 'true', role: 'status' },
  template: `
    <span class="visually-hidden">{{ label() }}</span>
    @for (i of items(); track i) {
      @switch (variant()) {
        @case ('card') {
          <div class="card stack" aria-hidden="true">
            <div class="row between"><span class="bone" style="width: 40%"></span><span class="bone pill"></span></div>
            <span class="bone" style="width: 70%"></span>
            <span class="bone" style="width: 55%"></span>
          </div>
        }
        @case ('rows') {
          <div class="table-row" aria-hidden="true">
            <span class="bone" style="width: 24%"></span>
            <span class="bone" style="width: 36%"></span>
            <span class="bone" style="width: 16%"></span>
          </div>
        }
        @default {
          <span class="bone line" [style.width.%]="i % 3 === 2 ? 60 : 100" aria-hidden="true"></span>
        }
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 12px; }
    .bone { display: block; height: 12px; border-radius: 6px; background: var(--surface-3); animation: amapulse 1.6s ease-in-out infinite; }
    .bone.pill { width: 72px; height: 22px; border-radius: 999px; }
    .table-row { display: flex; gap: 24px; padding: 14px; border-bottom: 1px solid var(--border); }
    @media (prefers-reduced-motion: reduce) { .bone { animation: none; } }
  `,
})
export class SkeletonComponent {
  readonly variant = input<'lines' | 'card' | 'rows'>('lines');
  readonly count = input(3);
  readonly label = input('Loading');

  protected readonly items = computed(() => Array.from({ length: this.count() }, (_, i) => i));
}

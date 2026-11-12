import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ICONS, IconName } from './icons';

/** A design system icon. Decorative by default; pass `label` when the icon carries meaning on its own. */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (name() === 'brand') {
      <!-- The mark is filled; its ring lets the tile colour through around the sun -->
      <svg class="i brand-glyph" [class.sm]="size() === 'sm'" [class.lg]="size() === 'lg'" viewBox="0 0 64 64"
           [attr.aria-hidden]="label() ? null : 'true'" [attr.role]="label() ? 'img' : null" [attr.aria-label]="label() || null" focusable="false">
        @for (d of paths(); track $index) {
          <path [attr.d]="d" />
        }
        <circle class="hole" cx="32" cy="32" r="6.2" />
        <circle cx="32" cy="32" r="3.4" />
      </svg>
    } @else {
      <svg class="i" [class.sm]="size() === 'sm'" [class.lg]="size() === 'lg'" [class.spin]="spin()"
           viewBox="0 0 24 24" [attr.aria-hidden]="label() ? null : 'true'" [attr.role]="label() ? 'img' : null"
           [attr.aria-label]="label() || null" focusable="false">
        @for (d of paths(); track $index) {
          <path [attr.d]="d" />
        }
      </svg>
    }
  `,
  styles: `
    :host { display: inline-flex; flex: none; line-height: 0; }
    .brand-glyph { fill: currentColor; stroke: none; }
    .brand-glyph .hole { fill: var(--brand-hole, var(--accent, #F26B3A)); }
  `,
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly spin = input(false);
  readonly label = input<string>();

  protected readonly paths = computed(() => ICONS[this.name()]);
}

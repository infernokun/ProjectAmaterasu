import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ICONS, IconName } from './icons';

/** A design system icon. Decorative by default; pass `label` when the icon carries meaning on its own. */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="i" [class.sm]="size() === 'sm'" [class.lg]="size() === 'lg'" [class.spin]="spin()"
         viewBox="0 0 24 24" [attr.aria-hidden]="label() ? null : 'true'" [attr.role]="label() ? 'img' : null"
         [attr.aria-label]="label() || null" focusable="false">
      @for (d of paths(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: `:host { display: inline-flex; flex: none; line-height: 0; }`,
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly spin = input(false);
  readonly label = input<string>();

  protected readonly paths = computed(() => ICONS[this.name()]);
}

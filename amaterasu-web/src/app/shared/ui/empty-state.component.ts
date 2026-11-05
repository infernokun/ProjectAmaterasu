import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from './icon.component';
import { IconName } from './icons';

/** Shown when a list or page has nothing in it. Project a button to offer the next step. */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="empty">
      @if (icon()) {
        <span class="feed-ico"><app-icon [name]="icon()!" /></span>
      }
      <strong class="title">{{ title() }}</strong>
      @if (message()) {
        <p class="small">{{ message() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    :host { display: block; }
    .feed-ico { width: 40px; height: 40px; color: var(--text-2); }
    .title { color: var(--text); font-weight: 600; }
    p { margin: 0; max-width: 420px; }
  `,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly message = input<string>();
  readonly icon = input<IconName>();
}

import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Page title block. Project buttons or links into it to place them on the right. */
@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-head">
      <div>
        @if (eyebrow()) {
          <div class="small muted">{{ eyebrow() }}</div>
        }
        <h1 class="h1">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="sub">{{ subtitle() }}</p>
        }
        <ng-content select="[subtitle]" />
      </div>
      <div class="row">
        <ng-content />
      </div>
    </header>
  `,
  styles: `:host { display: block; }`,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly eyebrow = input<string>();
  readonly subtitle = input<string>();
}

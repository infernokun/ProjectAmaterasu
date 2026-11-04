import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { IconComponent } from './icon.component';

/** A machine value (id, address, flag) in the mono face, optionally with a copy button. */
@Component({
  selector: 'app-mono-id',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="mono value" [title]="value()">{{ value() }}</span>
    @if (copyable()) {
      <button type="button" class="copy" (click)="copy()" [attr.aria-label]="copied() ? 'Copied' : 'Copy ' + (label() || value())">
        <app-icon [name]="copied() ? 'check' : 'copy'" size="sm" />
      </button>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 4px; min-width: 0; max-width: 100%; }
    .value { font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .copy { display: inline-grid; place-items: center; width: 26px; height: 26px; border: 0; border-radius: 6px; background: transparent; color: var(--text-3); cursor: pointer; flex: none; }
    .copy:hover { background: var(--surface-2); color: var(--text); }
    .copy:focus-visible { outline: 2px solid var(--focus); outline-offset: 1px; }
  `,
})
export class MonoIdComponent {
  private readonly clipboard = inject(Clipboard);

  readonly value = input.required<string>();
  readonly copyable = input(false);
  /** What the value is, for the copy button's accessible name. */
  readonly label = input<string>();

  protected readonly copied = signal(false);
  private timer?: ReturnType<typeof setTimeout>;

  protected copy(): void {
    if (this.clipboard.copy(this.value())) {
      this.copied.set(true);
      clearTimeout(this.timer);
      this.timer = setTimeout(() => this.copied.set(false), 1500);
    }
  }
}

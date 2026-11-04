import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MonoIdComponent } from './mono-id.component';

export interface KeyValueItem {
  label: string;
  value: string | number | null | undefined;
  /** Set for ids, addresses, ports and other machine values. */
  mono?: boolean;
  /** Adds a copy button (implies mono). */
  copy?: boolean;
}

/** A definition list of labels and values. Empty values show as a dash. */
@Component({
  selector: 'app-key-value-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MonoIdComponent],
  template: `
    <dl class="kv">
      @for (item of items(); track item.label) {
        <div class="kv-row">
          <dt>{{ item.label }}</dt>
          <dd>
            @if (item.value === null || item.value === undefined || item.value === '') {
              <span class="muted">-</span>
            } @else if (item.copy) {
              <app-mono-id [value]="'' + item.value" [copyable]="true" />
            } @else if (item.mono) {
              <span class="mono">{{ item.value }}</span>
            } @else {
              {{ item.value }}
            }
          </dd>
        </div>
      }
    </dl>
  `,
  styles: `
    :host { display: block; }
    .kv { margin: 0; display: grid; gap: 0; }
    .kv-row { display: grid; grid-template-columns: minmax(110px, 32%) minmax(0, 1fr); gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--border); align-items: center; }
    .kv-row:last-child { border-bottom: 0; }
    dt { color: var(--text-3); font-size: var(--fs-sm); }
    dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
    .mono { font-size: 13px; }
  `,
})
export class KeyValueListComponent {
  readonly items = input.required<readonly KeyValueItem[]>();
}

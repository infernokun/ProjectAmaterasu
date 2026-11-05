import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LabDisplayState, labStateMeta, StatusTone } from '../lab-status';
import { IconComponent } from './icon.component';

/**
 * A status chip. Pass a lab or server `state` (LabStatus or a client transition) to get the
 * standard label and color, or a `tone` and `label` for anything else.
 */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="chip" [class]="'s-' + resolvedTone()">
      @if (working()) {
        <app-icon name="spinner" size="sm" [spin]="true" />
      } @else {
        <span class="dot"></span>
      }
      {{ resolvedLabel() }}
    </span>
  `,
  styles: `:host { display: inline-flex; }`,
})
export class StatusBadgeComponent {
  readonly state = input<LabDisplayState | null>();
  readonly tone = input<StatusTone>();
  readonly label = input<string>();

  private readonly meta = computed(() => labStateMeta(this.state()));
  protected readonly resolvedTone = computed(() => this.tone() ?? this.meta().tone);
  protected readonly resolvedLabel = computed(() => this.label() ?? this.meta().label);
  protected readonly working = computed(() => !this.tone() && this.meta().working);
}

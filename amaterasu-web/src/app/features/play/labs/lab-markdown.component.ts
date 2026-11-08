import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MarkdownComponent } from '../../../shared/ui/markdown.component';
import { EnvironmentView } from '../data/play.models';
import { environmentStatus, trackerOf } from './environment-status';
import { bastionOf, loginUser } from './lab-view';
import { PasswordChipComponent } from './password-chip.component';

/**
 * Lab Markdown: a scenario or a question. {{address}}, {{username}} and {{subnet}} come from the
 * running environment; {{password}} is a masked chip that reveals on request.
 */
@Component({
  selector: 'app-lab-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MarkdownComponent, PasswordChipComponent],
  template: `
    <app-markdown [source]="source()" [placeholder]="slot" />
    <ng-template #slot let-name let-code="code">
      @if (live()) {
        @if (name === 'password') {
          <app-password-chip [trackerId]="trackerId()!" />
        } @else if (values()[name]; as value) {
          <span class="ph-value">{{ value }}</span>
        } @else {
          <span class="ph-wait">not reported yet</span>
        }
      } @else if (code) {
        <span class="ph-tag" [title]="waiting()">‹{{ name }}›</span>
      } @else {
        <span class="ph-wait">{{ waiting() }}</span>
      }
    </ng-template>
  `,
  styles: `
    :host { display: block; }
    .ph-wait { font-style: italic; color: var(--text-3); font-family: var(--font-sans); }
    .ph-value { font-family: var(--font-mono); }
    .ph-tag { color: var(--text-3); font-style: italic; }
  `,
})
export class LabMarkdownComponent {
  readonly source = input<string | null>('');
  readonly environment = input<EnvironmentView | null>(null);
  /** The viewer may see how to get in: it is the copy they work in. */
  readonly canSeeLogin = input(false);

  private readonly tracker = computed(() => trackerOf(this.environment()));
  protected readonly trackerId = computed(() => this.tracker()?.id ?? null);
  protected readonly live = computed(() => !!this.trackerId() && this.canSeeLogin()
    && environmentStatus(this.tracker()) === 'RUNNING');
  protected readonly waiting = computed(() => {
    if (!this.environment()) return 'not used in this lab';
    return environmentStatus(this.tracker()) === 'RUNNING' && !this.canSeeLogin()
      ? 'shown to the copy\'s players' : 'available once the environment is running';
  });
  protected readonly values = computed<Record<string, string | null>>(() => {
    const tracker = this.tracker();
    const bastion = bastionOf(tracker);
    return { address: bastion?.accessAddress ?? null, username: loginUser(tracker), subnet: bastion?.labSubnet ?? null };
  });
}

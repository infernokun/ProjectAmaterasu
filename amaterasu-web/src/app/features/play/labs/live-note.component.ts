import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { formatDistanceToNowStrict } from 'date-fns';

/** "Live · updated 12s ago", ticking while it is on screen. */
@Component({
  selector: 'app-live-note',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="hr-note"><span class="pill-dot pulse" style="color: var(--ok)"></span>Live{{ label() }}</span>`,
})
export class LiveNoteComponent {
  /** When the data last changed, in epoch ms. */
  readonly updatedAt = input<number | null>(null);

  private readonly now = signal(Date.now());
  protected readonly label = computed(() => {
    this.now();
    const at = this.updatedAt();
    return at ? ` · updated ${formatDistanceToNowStrict(at, { addSuffix: true })}` : '';
  });

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), 15000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }
}

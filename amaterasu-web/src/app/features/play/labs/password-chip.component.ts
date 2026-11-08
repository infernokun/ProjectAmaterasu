import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PlayApiService } from '../data/play-api.service';

/** A copy's VM password, masked until asked for; fetched only on Show or Copy. */
@Component({
  selector: 'app-password-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="chip-pw">
      <span class="mono pw" [class.masked]="!shown()" data-testid="password">{{ shown() && password() ? password() : '••••••••••••' }}</span>
      <button type="button" class="mini" (click)="toggle()" [disabled]="loading()" [attr.aria-pressed]="shown()"
              [attr.aria-label]="shown() ? 'Hide the password' : 'Show the password'">
        <app-icon [name]="loading() ? 'spinner' : 'eye'" size="sm" [spin]="loading()" />{{ shown() ? 'Hide' : 'Show' }}</button>
      <button type="button" class="mini" (click)="copy()" [disabled]="loading()" aria-label="Copy the password">
        <app-icon [name]="copied() ? 'check' : 'copy'" size="sm" />{{ copied() ? 'Copied' : 'Copy' }}</button>
    </span>
    @if (error()) {
      <span class="small s-bad" role="alert"> {{ error() }}</span>
    }
  `,
  styles: `
    :host { display: inline; }
    .chip-pw { display: inline-flex; align-items: center; gap: 4px; padding: 1px 4px 1px 8px; border: 1px solid var(--border);
      border-radius: 999px; background: var(--surface-2); vertical-align: middle; font-family: var(--font-sans); }
    .pw { min-width: 10ch; font-size: 13px; }
    .masked { letter-spacing: 1px; color: var(--text-3); }
    .mini { display: inline-flex; align-items: center; gap: 3px; border: 0; background: none; color: var(--text-2);
      font: inherit; font-size: 12px; padding: 2px 6px; border-radius: 999px; cursor: pointer; }
    .mini:hover:not(:disabled) { background: var(--surface-3, var(--border)); color: var(--text); }
  `,
})
export class PasswordChipComponent {
  private readonly api = inject(PlayApiService);
  private readonly clipboard = inject(Clipboard);

  readonly trackerId = input.required<string>();

  protected readonly password = signal<string | null>(null);
  protected readonly shown = signal(false);
  protected readonly loading = signal(false);
  protected readonly copied = signal(false);
  protected readonly error = signal('');
  private timer?: ReturnType<typeof setTimeout>;

  constructor() {
    // A fresh copy has a new password
    effect(() => {
      this.trackerId();
      untracked(() => {
        this.password.set(null);
        this.shown.set(false);
      });
    });
  }

  private async load(): Promise<string | null> {
    if (this.password()) return this.password();
    this.loading.set(true);
    this.error.set('');
    try {
      const access = await firstValueFrom(this.api.access(this.trackerId()));
      this.password.set(access.password);
      if (!access.password) this.error.set('This copy has no password.');
      return access.password;
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The password could not be loaded.');
      return null;
    } finally {
      this.loading.set(false);
    }
  }

  protected async toggle(): Promise<void> {
    if (this.shown()) {
      this.shown.set(false);
      return;
    }
    if (await this.load()) this.shown.set(true);
  }

  protected async copy(): Promise<void> {
    const password = await this.load();
    if (!password) return;
    if (this.clipboard.copy(password)) {
      this.copied.set(true);
      clearTimeout(this.timer);
      this.timer = setTimeout(() => this.copied.set(false), 1500);
    } else {
      this.shown.set(true);
      this.error.set('Copying is blocked here. Select the password and copy it yourself.');
    }
  }
}

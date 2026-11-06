import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { NgTemplateOutlet } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { IconComponent } from '../../../shared/ui/icon.component';
import { MonoIdComponent } from '../../../shared/ui/mono-id.component';
import { PlayApiService } from '../data/play-api.service';
import { bastionOf, loginUser } from './lab-view';

/**
 * How to get into a lab copy: SSH to its bastion when it has one, and the copy's VM password.
 * The password is fetched only when someone asks to see or copy it.
 */
@Component({
  selector: 'app-lab-access',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, IconComponent, MonoIdComponent],
  template: `
    @if (bastion(); as b) {
      <section class="access stack tight" [attr.aria-label]="'SSH access to ' + labName()">
        <div class="row between head">
          <strong class="row tight-row"><app-icon name="terminal" size="sm" />SSH access</strong>
          <span class="small muted">through {{ b.name }}</span>
        </div>
        @if (command(); as cmd) {
          <div class="cmd"><app-mono-id [value]="cmd" [copyable]="true" label="SSH command" /></div>
        } @else {
          <div class="small muted row tight-row" role="status"><app-icon name="spinner" size="sm" [spin]="true" />Waiting for the bastion's address…</div>
        }
        <ng-container *ngTemplateOutlet="secret" />
        @if (!compact()) {
          <div class="small dim">
            @if (b.labSubnet) {
              The lab network is <span class="mono">{{ b.labSubnet }}</span>; reach its hosts from the bastion.
            } @else {
              Reach the lab's hosts from the bastion.
            }
          </div>
          <pre class="code tips" aria-label="SSH tips">{{ tips() }}</pre>
        }
      </section>
    } @else if (user()) {
      <section class="access stack tight" [attr.aria-label]="'VM login for ' + labName()">
        <strong class="row tight-row"><app-icon name="key" size="sm" />VM login</strong>
        <ng-container *ngTemplateOutlet="secret" />
      </section>
    }

    <ng-template #secret>
      <div class="row secret">
        <span class="small muted">User</span><span class="mono">{{ user() ?? '-' }}</span>
        <span class="small muted">Password</span>
        <span class="mono pw" [class.masked]="!shown()" data-testid="password">{{ shown() && password() ? password() : '••••••••••••' }}</span>
        <button type="button" class="btn sm ghost" (click)="toggle()" [disabled]="loading()" [attr.aria-pressed]="shown()">
          <app-icon [name]="loading() ? 'spinner' : 'eye'" size="sm" [spin]="loading()" />{{ shown() ? 'Hide' : 'Show' }}</button>
        <button type="button" class="btn sm ghost" (click)="copyPassword()" [disabled]="loading()">
          <app-icon [name]="copied() ? 'check' : 'copy'" size="sm" />{{ copied() ? 'Copied' : 'Copy' }}</button>
      </div>
      @if (error()) {
        <div class="small s-bad" role="alert">{{ error() }}</div>
      }
    </ng-template>
  `,
  styles: `
    :host { display: block; }
    .access { border: 1px solid var(--border); border-radius: 10px; background: var(--surface-2); padding: 12px 14px; }
    .head { gap: 8px; }
    .tight-row { gap: 6px; align-items: center; }
    .cmd { display: flex; min-width: 0; }
    .secret { gap: 8px; align-items: center; flex-wrap: wrap; }
    .pw { min-width: 10ch; }
    .masked { letter-spacing: 1px; color: var(--text-3); }
    .tips { font-size: 12px; padding: 10px 12px; }
  `,
})
export class LabAccessComponent {
  private readonly api = inject(PlayApiService);
  private readonly clipboard = inject(Clipboard);

  readonly tracker = input.required<LabTracker>();
  /** Card size: the command and password, no tips. */
  readonly compact = input(false);

  private readonly trackerId = computed(() => this.tracker().id);
  protected readonly bastion = computed(() => bastionOf(this.tracker()));
  protected readonly user = computed(() => loginUser(this.tracker()));
  protected readonly labName = computed(() => this.tracker().labStarted?.name ?? 'this lab');
  protected readonly command = computed(() => {
    const address = this.bastion()?.accessAddress;
    return address ? `ssh ${this.user() ?? 'user'}@${address}` : null;
  });
  protected readonly tips = computed(() => {
    const login = `${this.user() ?? 'user'}@${this.bastion()?.accessAddress ?? '<bastion>'}`;
    return [
      `# A lab host, jumping through the bastion`,
      `ssh -J ${login} user@<lab-host>`,
      `# A lab web server at http://localhost:8080`,
      `ssh -L 8080:<lab-host>:80 ${login}`,
    ].join('\n');
  });

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
      const access = await firstValueFrom(this.api.access(this.tracker().id!));
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

  protected async copyPassword(): Promise<void> {
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

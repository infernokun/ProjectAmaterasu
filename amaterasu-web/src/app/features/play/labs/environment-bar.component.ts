import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { ApiResponse } from '../../../models/api-response.model';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { IconComponent } from '../../../shared/ui/icon.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { PlayApiService } from '../data/play-api.service';
import { EnvironmentActionResult, EnvironmentView } from '../data/play.models';
import { ENVIRONMENT_STATUS_META, environmentStatus, trackerOf } from './environment-status';
import { LabAccessComponent } from './lab-access.component';
import { bastionOf, isVm, loginUser, stopWarning } from './lab-view';

type Action = 'start' | 'pause' | 'stop';

/**
 * A lab's environment: its status, the action that fits the viewer's role, and how to get in. The
 * shared copy is run by facilitators; a team copy by its team.
 */
@Component({
  selector: 'app-environment-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, StatusBadgeComponent, LabAccessComponent],
  template: `
    <section class="card env stack" aria-label="Environment">
      <div class="row between head">
        <div class="row title">
          <div class="feed-ico"><app-icon [name]="vm() ? 'monitor' : 'compose'" /></div>
          <div class="stack tight">
            <div class="row tight-row">
              <strong>{{ environment().labName }}</strong>
              <app-status-badge [tone]="meta().tone" [label]="meta().label" />
            </div>
            <span class="small muted">{{ modeLine() }}</span>
          </div>
        </div>
        <div class="row actions">
          @if (busy()) {
            <button type="button" class="btn" disabled><app-icon name="spinner" [spin]="true" />{{ busyLabel() }}</button>
          } @else {
            @if (canOpen() && consoleLink(); as link) {
              <a class="btn primary" [routerLink]="link"><app-icon name="terminal" />Open console</a>
            }
            @if (environment().canControl) {
              @switch (status()) {
                @case ('NOT_RUNNING') {
                  <button type="button" class="btn primary" (click)="act('start')"><app-icon name="play" />Start</button>
                }
                @case ('FAILED') {
                  <button type="button" class="btn primary" (click)="act('start')"><app-icon name="play" />Start a fresh copy</button>
                }
                @case ('PAUSED') {
                  <button type="button" class="btn primary" (click)="act('start')"><app-icon name="play" />Resume</button>
                }
              }
              @if (status() === 'RUNNING' || status() === 'PAUSED') {
                <button type="button" class="btn danger" (click)="stop()"><app-icon name="stop" />Stop</button>
              }
            }
            @if (detailsLink(); as link) {
              <a class="btn ghost" [routerLink]="link" title="Services, VMs, logs and activity">Details</a>
            }
          }
        </div>
      </div>
      @if (environment().note) {
        <div class="small dim">{{ environment().note }}</div>
      }
      @if (status() === 'RUNNING' && tracker(); as t) {
        @if (canSeeLogin()) {
          @if (hasLogin()) {
            <app-lab-access [tracker]="t" [compact]="true" />
          }
        } @else {
          <div class="small dim">Join the lab to see how to get in.</div>
        }
      } @else if (status() === 'STARTING') {
        <div class="small dim">Building the environment. This usually takes a minute or two.</div>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .head { gap: 12px; align-items: flex-start; }
    .title { gap: 12px; flex-wrap: nowrap; align-items: center; }
    .tight-row { gap: 8px; align-items: center; }
    .actions { gap: 8px; }
  `,
})
export class EnvironmentBarComponent {
  private readonly api = inject(PlayApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly labId = input.required<string>();
  readonly environment = input.required<EnvironmentView>();
  /** The viewer joined the lab. */
  readonly joined = input(false);
  /** The viewer facilitates the lab or is an admin. */
  readonly canRun = input(false);
  readonly changed = output<EnvironmentView>();

  protected readonly busy = signal<Action | null>(null);
  protected readonly tracker = computed(() => trackerOf(this.environment()));
  protected readonly status = computed(() => environmentStatus(this.tracker(), this.busy() === 'start' ? 'STARTING' : null));
  protected readonly meta = computed(() => ENVIRONMENT_STATUS_META[this.status()]);
  protected readonly vm = computed(() => this.environment().labType === 'VIRTUAL_MACHINE');
  protected readonly busyLabel = computed(() => ({ start: 'Starting', pause: 'Pausing', stop: 'Stopping' })[this.busy()!]);

  protected readonly modeLine = computed(() => {
    const environment = this.environment();
    if (environment.mode === 'SHARED') return 'One shared copy for everyone in this lab';
    const others = environment.copiesRunning - (this.status() === 'RUNNING' ? 1 : 0);
    return `Each team gets its own copy${others > 0 ? ` · ${others} other ${others === 1 ? 'copy' : 'copies'} running` : ''}`;
  });

  /** Shared copies are open to everyone who joined; a team copy to its team, which is who receives it. */
  protected readonly canSeeLogin = computed(() =>
    this.environment().mode === 'PER_TEAM' || this.joined() || this.canRun());
  protected readonly canOpen = computed(() => this.status() === 'RUNNING' && this.canSeeLogin());
  protected readonly hasLogin = computed(() => !!bastionOf(this.tracker()) || !!loginUser(this.tracker()));

  protected readonly consoleLink = computed<unknown[] | null>(() => {
    const tracker = this.tracker();
    if (!tracker?.id) return null;
    if (!isVm(tracker.labStarted)) return ['/environments', tracker.id];
    const running = (tracker.vms ?? []).filter(vm => vm.status === 'running');
    const vm = running.find(machine => machine.role === 'bastion') ?? running[0];
    return vm ? ['/environments', tracker.id, 'console', vm.vmid] : null;
  });

  protected readonly detailsLink = computed<unknown[] | null>(() => {
    const tracker = this.tracker();
    const allowed = this.environment().canControl || (this.canSeeLogin() && this.status() === 'RUNNING');
    return tracker?.id && allowed && this.status() !== 'NOT_RUNNING' ? ['/environments', tracker.id] : null;
  });

  protected async stop(): Promise<void> {
    const name = this.environment().labName;
    const tracker = this.tracker();
    const shared = this.environment().mode === 'SHARED';
    const choice = await firstValueFrom(this.confirm.open({
      title: `Stop ${name}?`,
      message: `${tracker ? stopWarning(tracker) : ''} ${shared ? 'Everyone working in it loses their progress there.' : "Your team's progress in this copy is lost."} To keep it, pause instead.`,
      confirmLabel: 'Stop and destroy',
      alternativeLabel: 'Pause instead',
      typeToConfirm: name,
    }));
    if (choice === 'confirm') await this.act('stop');
    if (choice === 'alternative') await this.act('pause');
  }

  protected async act(action: Action): Promise<void> {
    this.busy.set(action);
    try {
      const result = await firstValueFrom(this.api.environmentAction(this.labId(), action));
      if (result.environment) this.changed.emit(result.environment);
      this.toast.success(result.message);
    } catch (error) {
      const body = error instanceof HttpErrorResponse ? (error.error as ApiResponse<EnvironmentActionResult> | null) : null;
      if (body?.data?.environment) this.changed.emit(body.data.environment);
      this.toast.show(errorMessage(error) ?? 'That did not work. Try again.', 'bad', 6000);
    } finally {
      this.busy.set(null);
    }
  }
}

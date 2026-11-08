import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { ApiResponse } from '../../../models/api-response.model';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { IconComponent } from '../../../shared/ui/icon.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { PlayApiService } from '../data/play-api.service';
import { EnvironmentActionResult, EnvironmentCopy, EnvironmentStatus, EnvironmentView } from '../data/play.models';
import { ENVIRONMENT_STATUS_META, environmentStatus, trackerOf } from './environment-status';
import { LabAccessComponent } from './lab-access.component';
import { bastionOf, isVm, loginUser, stopWarning } from './lab-view';

type Action = 'start' | 'pause' | 'stop';

export const NOT_RUNNING_NOTE = 'Not running yet: an admin starts this environment.';

/**
 * A lab's environment. Players see their copy's status and, while it runs, how to get in. Admins and
 * the lab's facilitators also get every copy with its own Start, Pause and Stop.
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
          @if (status() === 'RUNNING' && consoleLink(); as link) {
            <a class="btn primary" [routerLink]="link"><app-icon name="terminal" />Open console</a>
          }
          @if (detailsLink(); as link) {
            <a class="btn ghost" [routerLink]="link" title="Services, VMs and how to get in">Details</a>
          }
        </div>
      </div>
      @if (status() === 'RUNNING' && tracker(); as t) {
        @if (hasLogin()) {
          <app-lab-access [tracker]="t" [compact]="true" />
        }
      } @else if (status() === 'STARTING') {
        <div class="small dim">Building the environment. This usually takes a minute or two.</div>
      } @else if (!environment().canControl) {
        <div class="small dim note" role="status"><app-icon name="clock" size="sm" />{{ environment().note || notRunning }}</div>
      } @else if (environment().note) {
        <div class="small dim">{{ environment().note }}</div>
      }

      @if (environment().canControl && copies().length) {
        <div class="copies stack tight" aria-label="Copies">
          <div class="row between copies-head">
            <span class="nav-label">{{ environment().mode === 'SHARED' ? 'Shared copy' : 'Copies' }}</span>
            @if (environment().mode === 'PER_TEAM' && teamCopiesToStart().length) {
              <button type="button" class="btn sm" [disabled]="startingAll()" (click)="startAll()">
                @if (startingAll()) {<app-icon name="spinner" size="sm" [spin]="true" />} @else {<app-icon name="play" size="sm" />}
                Start for every team</button>
            }
          </div>
          @for (copy of copies(); track copy.target) {
            <div class="row between copy" [attr.data-target]="copy.target">
              <div class="row tight-row copy-name">
                <strong>{{ copy.label }}</strong>
                @if (copy.target !== 'shared') {
                  <span class="small muted">{{ copy.members }} {{ copy.members === 1 ? 'member' : 'members' }}</span>
                }
                <app-status-badge [tone]="copyMeta(copy).tone" [label]="copyMeta(copy).label" />
              </div>
              <div class="row copy-actions">
                @if (busy()[copy.target]; as action) {
                  <button type="button" class="btn sm" disabled><app-icon name="spinner" size="sm" [spin]="true" />{{ busyLabels[action] }}</button>
                } @else {
                  @switch (copyStatus(copy)) {
                    @case ('NOT_RUNNING') {
                      <button type="button" class="btn sm primary" (click)="act(copy, 'start')"><app-icon name="play" size="sm" />Start</button>
                    }
                    @case ('FAILED') {
                      <button type="button" class="btn sm primary" (click)="act(copy, 'start')"><app-icon name="play" size="sm" />Start a fresh copy</button>
                    }
                    @case ('PAUSED') {
                      <button type="button" class="btn sm primary" (click)="act(copy, 'start')"><app-icon name="play" size="sm" />Resume</button>
                    }
                    @case ('RUNNING') {
                      <button type="button" class="btn sm" (click)="act(copy, 'pause')"><app-icon name="pause" size="sm" />Pause</button>
                    }
                  }
                  @if (copyStatus(copy) === 'RUNNING' || copyStatus(copy) === 'PAUSED') {
                    <button type="button" class="btn sm danger" (click)="stop(copy)"><app-icon name="stop" size="sm" />Stop</button>
                  }
                  @if (copy.trackerId && copyStatus(copy) !== 'NOT_RUNNING') {
                    <a class="btn sm ghost" [routerLink]="['/environments', copy.trackerId]">Details</a>
                  }
                }
              </div>
            </div>
          }
        </div>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .head { gap: 12px; align-items: flex-start; }
    .title { gap: 12px; flex-wrap: nowrap; align-items: center; }
    .tight-row { gap: 8px; align-items: center; }
    .actions, .copy-actions { gap: 8px; }
    .note { display: flex; gap: 6px; align-items: center; }
    .copies { border-top: 1px solid var(--border); padding-top: 10px; }
    .copy { gap: 8px; padding: 4px 0; }
    .copy-name { flex-wrap: wrap; }
  `,
})
export class EnvironmentBarComponent {
  private readonly api = inject(PlayApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly labId = input.required<string>();
  readonly environment = input.required<EnvironmentView>();
  readonly changed = output<EnvironmentView>();

  protected readonly notRunning = NOT_RUNNING_NOTE;
  protected readonly busyLabels: Record<Action, string> = { start: 'Starting', pause: 'Pausing', stop: 'Stopping' };
  /** Requests in flight, by copy target. */
  protected readonly busy = signal<Record<string, Action>>({});
  protected readonly startingAll = signal(false);

  protected readonly tracker = computed(() => trackerOf(this.environment()));
  protected readonly copies = computed<EnvironmentCopy[]>(() => this.environment().copies ?? []);
  /** The viewer's own copy among the copies; the shared one in a shared lab. */
  private readonly ownTarget = computed(() => {
    const environment = this.environment();
    if (environment.mode === 'SHARED') return 'shared';
    return this.copies().find(copy => copy.trackerId && copy.trackerId === environment.trackerId)?.target ?? null;
  });
  protected readonly status = computed(() => {
    const own = this.ownTarget();
    return environmentStatus(this.tracker(), own && this.busy()[own] === 'start' ? 'STARTING' : null);
  });
  protected readonly meta = computed(() => ENVIRONMENT_STATUS_META[this.status()]);
  protected readonly vm = computed(() => this.environment().labType === 'VIRTUAL_MACHINE');
  protected readonly teamCopiesToStart = computed(() => this.copies()
    .filter(copy => copy.target.startsWith('team:') && this.copyStatus(copy) === 'NOT_RUNNING'));

  protected readonly modeLine = computed(() => {
    const environment = this.environment();
    const label = environment.copyLabel ?? (environment.mode === 'SHARED' ? 'Shared copy' : null);
    if (environment.mode === 'SHARED') return `${label} · one copy for everyone in this lab`;
    const others = environment.copiesRunning - (this.status() === 'RUNNING' ? 1 : 0);
    const rest = `each team gets its own copy${others > 0 ? ` · ${others} other ${others === 1 ? 'copy' : 'copies'} running` : ''}`;
    return label ? `${label} · ${rest}` : rest.charAt(0).toUpperCase() + rest.slice(1);
  });

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
    return tracker?.id && this.status() === 'RUNNING' ? ['/environments', tracker.id] : null;
  });

  protected copyStatus(copy: EnvironmentCopy): EnvironmentStatus {
    const tracker = copy.tracker ? new LabTracker(copy.tracker) : null;
    return environmentStatus(tracker, this.busy()[copy.target] === 'start' ? 'STARTING' : null);
  }

  protected copyMeta(copy: EnvironmentCopy) {
    return ENVIRONMENT_STATUS_META[this.copyStatus(copy)];
  }

  protected async stop(copy: EnvironmentCopy): Promise<void> {
    const name = this.environment().labName;
    const tracker = copy.tracker ? new LabTracker(copy.tracker) : null;
    const lost = copy.target === 'shared' ? 'Everyone working in it loses their progress there.'
      : `${copy.label} loses its progress in this copy.`;
    const choice = await firstValueFrom(this.confirm.open({
      title: `Stop ${name}${copy.target === 'shared' ? '' : ` for ${copy.label}`}?`,
      message: `${tracker ? stopWarning(tracker) : ''} ${lost} To keep it, pause instead.`,
      confirmLabel: 'Stop and destroy',
      alternativeLabel: 'Pause instead',
      typeToConfirm: name,
    }));
    if (choice === 'confirm') await this.act(copy, 'stop');
    if (choice === 'alternative') await this.act(copy, 'pause');
  }

  /** Starts the team copies that are not running, one after another. */
  protected async startAll(): Promise<void> {
    const copies = this.teamCopiesToStart();
    this.startingAll.set(true);
    let started = 0;
    try {
      for (const copy of copies) {
        if (await this.act(copy, 'start', false)) started++;
      }
    } finally {
      this.startingAll.set(false);
    }
    const failed = copies.length - started;
    if (failed) {
      this.toast.show(`Started ${started} of ${copies.length} team copies. ${failed} did not start.`, 'bad', 6000);
    } else {
      this.toast.success(`Started ${started} team ${started === 1 ? 'copy' : 'copies'}`);
    }
  }

  protected async act(copy: EnvironmentCopy, action: Action, report = true): Promise<boolean> {
    this.busy.update(busy => ({ ...busy, [copy.target]: action }));
    try {
      const result = await firstValueFrom(this.api.environmentAction(this.labId(), action, copy.target));
      if (result.environment) this.changed.emit(result.environment);
      if (report) this.toast.success(copy.target === 'shared' ? result.message : `${copy.label}: ${result.message}`);
      return result.successful !== false;
    } catch (error) {
      const body = error instanceof HttpErrorResponse ? (error.error as ApiResponse<EnvironmentActionResult> | null) : null;
      if (body?.data?.environment) this.changed.emit(body.data.environment);
      this.toast.show(`${copy.label}: ${errorMessage(error) ?? 'That did not work. Try again.'}`, 'bad', 6000);
      return false;
    } finally {
      this.busy.update(({ [copy.target]: _, ...rest }) => rest);
    }
  }
}

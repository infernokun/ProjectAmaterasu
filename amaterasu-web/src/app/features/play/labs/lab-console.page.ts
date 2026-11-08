import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CrumbsService } from '../../../core/crumbs.service';
import { errorMessage } from '../../../core/http-error';
import { AuditService } from '../../../core/services/audit.service';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { SessionStore } from '../../../core/stores/session.store';
import { LabStatus } from '../../../enums/lab-status.enum';
import { DockerServiceInfo } from '../../../models/lab/docker-service-info.model';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { ProxmoxVM } from '../../../models/lab/proxmox-vm.model';
import { ago } from '../../../shared/format';
import { StatusTone, labStateMeta } from '../../../shared/lab-status';
import { ToastService } from '../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { MonoIdComponent } from '../../../shared/ui/mono-id.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { ActivityLine, activityLine } from '../data/activity';
import { LabChallenge } from '../data/play.models';
import { PlayApiService } from '../data/play-api.service';
import { LabAccessComponent } from './lab-access.component';
import { LabActionsService } from './lab-actions.service';
import { LabFilesComponent } from './lab-files.component';
import { LabLogsComponent } from './lab-logs.component';
import {
  allowedActions, bastionOf, changedAgo, isRunningState, isVm, labActions, labTypeLabel, loginUser, pauseHelp, serviceName, stopWarning, vmLook,
} from './lab-view';

type Tab = 'units' | 'logs' | 'files' | 'activity';

/**
 * One environment copy: its services or VMs and how to get in. Admins and facilitators also get logs,
 * files and the lifecycle actions; admins the activity.
 */
@Component({
  selector: 'app-lab-console-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, MonoIdComponent, StatusBadgeComponent, EmptyStateComponent, SkeletonComponent,
    LabLogsComponent, LabFilesComponent, LabAccessComponent],
  templateUrl: './lab-console.page.html',
  styleUrl: './lab-console.page.scss',
  host: { class: 'content' },
})
export class LabConsolePage {
  private readonly store = inject(LabTrackersStore);
  private readonly api = inject(PlayApiService);
  private readonly audit = inject(AuditService);
  private readonly actions = inject(LabActionsService);
  private readonly toast = inject(ToastService);
  private readonly crumbs = inject(CrumbsService);
  protected readonly session = inject(SessionStore);

  readonly trackerId = input.required<string>();

  /** Loaded directly when the tracker is not one of the team's (an admin looking at another team). */
  private readonly fetched = signal<LabTracker | null>(null);
  protected readonly loadError = signal('');
  protected readonly tracker = computed(() => this.store.byId().get(this.trackerId()) ?? this.fetched());

  protected readonly tab = signal<Tab>('units');
  protected readonly logService = signal('all');
  protected readonly refreshing = signal(false);
  protected readonly activity = signal<ActivityLine[] | null>(null);
  /** Challenges that use this lab; null while loading. */
  protected readonly usedBy = signal<LabChallenge[] | null>(null);

  protected readonly state = computed(() => {
    const tracker = this.tracker();
    return tracker ? this.store.displayState()(tracker) : LabStatus.NONE;
  });
  protected readonly can = computed(() => {
    const tracker = this.tracker();
    return tracker ? allowedActions(labActions(tracker, this.state()), this.session.canRun(), this.session.isAdmin()) : null;
  });
  protected readonly vm = computed(() => isVm(this.tracker()?.labStarted));
  protected readonly typeLabel = computed(() => labTypeLabel(this.tracker()?.labStarted));
  protected readonly since = computed(() => (this.tracker() ? changedAgo(this.tracker()!) : ''));
  protected readonly services = computed(() => this.tracker()?.services ?? []);
  protected readonly vms = computed(() => this.tracker()?.vms ?? []);
  protected readonly bastion = computed(() => bastionOf(this.tracker()));
  /** The SSH block or VM login while the copy runs. */
  protected readonly showAccess = computed(() => this.vm() && isRunningState(this.state())
    && (!!this.bastion() || !!loginUser(this.tracker())));
  protected readonly unitCount = computed(() => (this.vm() ? this.vms().length : this.services().length));
  protected readonly serviceNames = computed(() => {
    const tracker = this.tracker();
    return tracker ? this.services().map(service => serviceName(tracker, service)) : [];
  });
  protected readonly server = computed(() => this.tracker()?.remoteServer);
  protected readonly serverOnline = computed(() => this.server()?.remoteServerStats?.status === LabStatus.ACTIVE);
  protected readonly pauseHelp = computed(() => (this.tracker() ? pauseHelp(this.tracker()!) : ''));
  protected readonly stopHelp = computed(() => (this.tracker() ? stopWarning(this.tracker()!) : ''));
  protected readonly ago = ago;

  constructor() {
    effect(() => {
      const id = this.trackerId();
      untracked(() => this.load(id));
    });
    effect(() => {
      const name = this.tracker()?.labStarted?.name;
      if (name) {
        untracked(() => this.crumbs.set([{ label: 'Labs', link: '/labs' }, { label: name }, { label: 'Environment' }]));
      }
    });
    effect(() => {
      const labId = this.tracker()?.labStarted?.id;
      if (labId) untracked(() => this.loadChallenges(labId));
    });
    effect(() => {
      if (this.tab() === 'activity' && this.session.isAdmin()) {
        untracked(() => this.loadActivity());
      }
    });
  }

  private async load(id: string): Promise<void> {
    if (this.store.byId().has(id)) {
      return;
    }
    try {
      this.fetched.set(await firstValueFrom(this.api.tracker(id)));
    } catch (error) {
      this.loadError.set(errorMessage(error) ?? 'This lab could not be loaded.');
    }
  }

  private loadedChallengesFor: string | null = null;

  private async loadChallenges(labId: string): Promise<void> {
    if (this.loadedChallengesFor === labId) return;
    this.loadedChallengesFor = labId;
    this.usedBy.set(null);
    try {
      const challenges = await firstValueFrom(this.api.challengesForLab(labId));
      if (this.loadedChallengesFor === labId) this.usedBy.set(challenges);
    } catch {
      if (this.loadedChallengesFor === labId) this.usedBy.set([]);
    }
  }

  private async loadActivity(): Promise<void> {
    try {
      const page = await firstValueFrom(this.audit.list({ targetType: 'LAB_TRACKER', targetId: this.trackerId(), size: 50 }));
      this.activity.set((page.content ?? []).map(activityLine));
    } catch {
      this.activity.set([]);
    }
  }

  protected serviceTone(service: DockerServiceInfo): StatusTone {
    switch (service.state) {
      case 'running': return 'active';
      case 'paused': return 'paused';
      case 'restarting': case 'created': return 'work';
      case 'exited': case 'dead': return 'bad';
      default: return 'off';
    }
  }

  protected vmTone(vm: ProxmoxVM): StatusTone {
    return vmLook(vm).tone;
  }

  protected vmLabel(vm: ProxmoxVM): string {
    return vmLook(vm).label;
  }

  protected cpuPct(vm: ProxmoxVM): number {
    return Math.round((vm.cpu ?? 0) * 100);
  }

  protected memPct(vm: ProxmoxVM): number {
    return vm.maxmem ? Math.round(((vm.mem ?? 0) / vm.maxmem) * 100) : 0;
  }

  protected gib(bytes: number | undefined): string {
    return `${((bytes ?? 0) / 1024 ** 3).toFixed(1)} GB`;
  }

  protected list(values: string[] | undefined): string {
    return values?.length ? values.join(', ') : '-';
  }

  protected showLogs(service: string): void {
    this.logService.set(service);
    this.tab.set('logs');
  }

  protected async refresh(): Promise<void> {
    const tracker = this.tracker();
    if (!tracker?.id) return;
    this.refreshing.set(true);
    try {
      const updated = await firstValueFrom(this.api.refreshTracker(tracker.id));
      if (this.store.byId().has(tracker.id)) {
        this.store.upsert(updated);
      } else {
        this.fetched.set(updated);
      }
    } catch (error) {
      this.toast.error('Status could not be refreshed', error);
    } finally {
      this.refreshing.set(false);
    }
  }

  protected resume(): void {
    this.actions.resume(this.tracker()!);
  }

  protected pause(): void {
    this.actions.pause(this.tracker()!);
  }

  protected stop(): void {
    this.actions.confirmStop(this.tracker()!);
  }

  protected restart(service: string): void {
    this.actions.restart(this.tracker()!, service);
  }

  protected meta = labStateMeta;
}

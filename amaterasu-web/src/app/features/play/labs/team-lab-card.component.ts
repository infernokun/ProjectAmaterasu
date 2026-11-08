import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { SessionStore } from '../../../core/stores/session.store';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { labStateMeta } from '../../../shared/lab-status';
import { IconComponent } from '../../../shared/ui/icon.component';
import { MonoIdComponent } from '../../../shared/ui/mono-id.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { LabAccessComponent } from './lab-access.component';
import { LabActionsService } from './lab-actions.service';
import {
  LifeStep, allowedActions, bastionOf, changedAgo, failedServices, isRunningState, isVm, labActions, labDetail, labTypeLabel, lifeStep, serviceName, shortId, stopWarning, toneOf,
} from './lab-view';

/** One of the viewer's copies, with its live state; admins and facilitators also get its actions. */
@Component({
  selector: 'app-team-lab-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, MonoIdComponent, StatusBadgeComponent, LabAccessComponent],
  templateUrl: './team-lab-card.component.html',
  styles: `
    :host { display: block; }
    .head { gap: 14px; align-items: flex-start; flex-wrap: nowrap; }
    .feed-ico { width: 42px; height: 42px; }
    .name { font-size: 15.5px; }
    .stop-confirm { padding: 0 18px 18px; }
  `,
})
export class TeamLabCardComponent {
  private readonly store = inject(LabTrackersStore);
  private readonly actions = inject(LabActionsService);
  protected readonly session = inject(SessionStore);

  readonly tracker = input.required<LabTracker>();

  protected readonly state = computed(() => this.store.displayState()(this.tracker()));
  protected readonly meta = computed(() => labStateMeta(this.state()));
  protected readonly can = computed(() =>
    allowedActions(labActions(this.tracker(), this.state()), this.session.canRun(), this.session.isAdmin()));
  protected readonly vm = computed(() => isVm(this.tracker().labStarted));
  protected readonly typeLabel = computed(() => labTypeLabel(this.tracker().labStarted));
  protected readonly detail = computed(() => labDetail(this.tracker(), this.state()));
  protected readonly step = computed(() => lifeStep(this.state()));
  protected readonly tone = computed(() => toneOf(this.state()));
  protected readonly since = computed(() => changedAgo(this.tracker()));
  protected readonly shortId = computed(() => shortId(this.tracker().id));
  protected readonly warning = computed(() => stopWarning(this.tracker()));
  protected readonly server = computed(() => this.tracker().remoteServer?.name ?? 'its server');
  protected readonly failedService = computed(() => {
    const failed = failedServices(this.tracker())[0];
    return failed ? serviceName(this.tracker(), failed) : '';
  });

  /** The first running VM of a VM lab, for the Console button. */
  protected readonly consoleVm = computed(() => {
    if (!this.vm() || this.can().working) return null;
    return (this.tracker().vms ?? []).find(machine => machine.status === 'running') ?? null;
  });

  /** SSH access while the copy runs, when it has a bastion. */
  protected readonly showAccess = computed(() => this.vm() && isRunningState(this.state()) && !!bastionOf(this.tracker()));

  protected readonly confirming = signal(false);

  protected stepClass(step: LifeStep): string {
    return this.step() === step ? `life-step now s-${this.tone()}` : 'life-step';
  }

  protected pause(): void {
    this.confirming.set(false);
    this.actions.pause(this.tracker());
  }

  protected resume(): void {
    this.actions.resume(this.tracker());
  }

  protected stop(): void {
    this.confirming.set(false);
    this.actions.stop(this.tracker());
  }

  protected remove(): void {
    this.actions.remove(this.tracker());
  }

  protected restart(): void {
    this.actions.restart(this.tracker(), this.failedService());
  }
}

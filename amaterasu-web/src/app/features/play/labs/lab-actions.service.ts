import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { LabActionOutcome, LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { SessionStore } from '../../../core/stores/session.store';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { PlayApiService } from '../data/play-api.service';
import { isVm, stopWarning } from './lab-view';

/** Lab actions with their confirmations and the toast that reports how they went. */
@Injectable({ providedIn: 'root' })
export class LabActionsService {
  private readonly trackers = inject(LabTrackersStore);
  private readonly session = inject(SessionStore);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly api = inject(PlayApiService);

  private report(outcome: LabActionOutcome): LabActionOutcome {
    if (outcome.ok) {
      this.toast.success(outcome.message);
    } else {
      this.toast.show(outcome.message, 'bad', 6000);
    }
    return outcome;
  }

  async resume(tracker: LabTracker, serverId?: string): Promise<LabActionOutcome> {
    return this.report(await this.trackers.resume(tracker, serverId));
  }

  async pause(tracker: LabTracker): Promise<LabActionOutcome> {
    return this.report(await this.trackers.pause(tracker));
  }

  async stop(tracker: LabTracker): Promise<LabActionOutcome> {
    return this.report(await this.trackers.stop(tracker));
  }

  /** The console's Stop: a dialog that asks for the lab's name and offers Pause instead. */
  async confirmStop(tracker: LabTracker): Promise<LabActionOutcome | null> {
    const name = tracker.labStarted?.name ?? 'this lab';
    const choice = await firstValueFrom(this.confirm.open({
      title: `Stop ${name}?`,
      message: `${stopWarning(tracker)} Your team's progress in this copy is lost. To keep it, pause instead.`,
      confirmLabel: 'Stop and destroy',
      alternativeLabel: 'Pause instead',
      typeToConfirm: name,
    }));
    if (choice === 'confirm') return this.stop(tracker);
    if (choice === 'alternative') return this.pause(tracker);
    return null;
  }

  async remove(tracker: LabTracker): Promise<LabActionOutcome | null> {
    const name = tracker.labStarted?.name ?? 'this lab';
    const team = this.session.team()?.name ?? 'your team';
    const kept = tracker.labStatus === LabStatus.PAUSED
      ? `${isVm(tracker.labStarted) ? 'The paused VMs are destroyed' : 'The paused containers are destroyed'} and your team's progress in them is lost.`
      : 'Nothing is running for it now.';
    const choice = await firstValueFrom(this.confirm.open({
      title: `Remove ${name} from ${team}?`,
      message: `${kept} You can start it again later from Available labs.`,
      confirmLabel: 'Remove from team',
    }));
    return choice === 'confirm' ? this.report(await this.trackers.removeFromTeam(tracker)) : null;
  }

  async restart(tracker: LabTracker, service: string): Promise<void> {
    this.trackers.setPending(tracker.id!, 'RESTARTING');
    try {
      const updated = await firstValueFrom(this.api.restartService(tracker.id!, service));
      this.trackers.upsert(updated);
      this.toast.success(`Restarted ${service}`);
    } catch (error) {
      this.toast.error(`${service} did not restart`, error);
    } finally {
      this.trackers.clearPending(tracker.id!);
    }
  }
}

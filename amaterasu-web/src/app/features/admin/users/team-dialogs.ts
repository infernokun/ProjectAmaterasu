import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { errorMessage } from '../../../core/http-error';
import { initialsOf } from '../../../core/stores/session.store';
import { ROLE_LABELS } from '../../../core/auth/roles';
import { IconComponent } from '../../../shared/ui/icon.component';
import { ToastService } from '../../../shared/services/toast.service';
import { AdminTeam, AdminUser } from './admin-users.service';

export interface TeamFormData {
  team?: AdminTeam;
  save: (name: string, description: string) => Promise<unknown>;
}

@Component({
  selector: 'app-team-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" role="dialog" aria-modal="true" aria-labelledby="team-h" (submit)="$event.preventDefault(); submit()">
      <div class="row between">
        <h2 class="h2" id="team-h">{{ data.team ? 'Edit ' + data.team.name : 'New team' }}</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button>
      </div>
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="close" /><div>{{ error() }}</div></div>
      }
      <label class="field">
        <span class="label">Name</span>
        <input class="input" [value]="name()" (input)="name.set($any($event.target).value)" maxlength="80" required>
      </label>
      <label class="field">
        <span class="label">Description</span>
        <input class="input" [value]="description()" (input)="description.set($any($event.target).value)" maxlength="200"
               placeholder="Who is on it, for example Night shift SOC analysts">
      </label>
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="submit" class="btn primary" [disabled]="!name().trim() || saving()">{{ data.team ? 'Save' : 'Create team' }}</button>
      </div>
    </form>
  `,
})
export class TeamDialogComponent {
  protected readonly data = inject<TeamFormData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<TeamDialogComponent>>(MatDialogRef);
  protected readonly name = signal(this.data.team?.name ?? '');
  protected readonly description = signal(this.data.team?.description ?? '');
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  protected async submit(): Promise<void> {
    if (!this.name().trim() || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set('');
    try {
      await this.data.save(this.name().trim(), this.description().trim());
      this.ref.close(true);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The team could not be saved.');
    } finally {
      this.saving.set(false);
    }
  }
}

export interface TeamMembersData {
  team: AdminTeam;
  /** Read on every change detection so the list follows the store. */
  users: () => AdminUser[];
  move: (userId: string, teamId: string | null) => Promise<AdminUser>;
}

@Component({
  selector: 'app-team-members-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="tm-h">
      <div class="row between">
        <h2 class="h2" id="tm-h">{{ data.team.name }} members</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button>
      </div>
      <div class="list card flush">
        @for (member of members(); track member.id) {
          <div class="list-item">
            <span class="avatar">{{ initials(member.username) }}</span>
            <div class="grow">
              <div class="name">{{ member.username }}</div>
              <div class="small muted">{{ labels[member.role] }}{{ member.disabled ? ' · Disabled' : '' }}</div>
            </div>
            <button type="button" class="btn sm ghost" [disabled]="busy()" (click)="move(member, null)">Remove</button>
          </div>
        } @empty {
          <div class="empty small">Nobody is on {{ data.team.name }} yet.</div>
        }
      </div>
      <label class="field">
        <span class="label">Add someone</span>
        <select class="input" [disabled]="busy() || !candidates().length" (change)="add($event)">
          <option value="">{{ candidates().length ? 'Choose a user...' : 'Everyone is already on this team' }}</option>
          @for (user of candidates(); track user.id) {
            <option [value]="user.id">{{ user.username }}{{ user.team ? ' (now on ' + user.team.name + ')' : '' }}</option>
          }
        </select>
        <span class="help">Moving someone takes them off their current team. Their points stay with them.</span>
      </label>
      <div class="row end"><button type="button" class="btn" (click)="ref.close()">Done</button></div>
    </div>
  `,
  styles: `.list { max-height: 320px; overflow: auto; } .name { font-weight: 500; }`,
})
export class TeamMembersDialogComponent {
  protected readonly data = inject<TeamMembersData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<TeamMembersDialogComponent>>(MatDialogRef);
  private readonly toast = inject(ToastService);
  protected readonly labels = ROLE_LABELS;
  protected readonly initials = initialsOf;
  protected readonly busy = signal(false);

  protected readonly members = computed(() => this.data.users().filter(user => user.team?.id === this.data.team.id));
  protected readonly candidates = computed(() => this.data.users().filter(user => user.team?.id !== this.data.team.id));

  protected add(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const user = this.data.users().find(candidate => candidate.id === select.value);
    select.value = '';
    if (user) {
      this.move(user, this.data.team.id);
    }
  }

  protected async move(user: AdminUser, teamId: string | null): Promise<void> {
    this.busy.set(true);
    try {
      await this.data.move(user.id, teamId);
      this.toast.success(teamId ? `${user.username} joined ${this.data.team.name}` : `${user.username} left ${this.data.team.name}`);
    } catch (error) {
      this.toast.error(`${user.username} could not be moved.`, error);
    } finally {
      this.busy.set(false);
    }
  }
}

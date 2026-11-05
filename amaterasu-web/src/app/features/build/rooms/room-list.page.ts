import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { relativeTime } from '../../../core/time';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { DIALOG_DEFAULTS } from '../../../shared/ui/dialog';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { AuthorChallenge, AuthorRoom, AuthoringService } from './authoring.service';

interface NewRoomData {
  create: (name: string, tag: string) => Promise<unknown>;
}

@Component({
  selector: 'app-new-room-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <form class="modal" role="dialog" aria-modal="true" aria-labelledby="nr-h" (submit)="$event.preventDefault(); submit()">
      <div class="row between">
        <h2 class="h2" id="nr-h">New room</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="ref.close()"><app-icon name="close" /></button>
      </div>
      @if (error()) { <div class="banner bad" role="alert"><app-icon name="close" /><div>{{ error() }}</div></div> }
      <label class="field"><span class="label">Room name</span>
        <input class="input" [value]="name()" (input)="name.set($any($event.target).value)" placeholder="Winter Range 2026" maxlength="80" required>
      </label>
      <label class="field"><span class="label">Flag wrapper</span>
        <input class="input mono" [value]="tag()" (input)="tag.set($any($event.target).value)" maxlength="40">
        <span class="help">How flags look in this room, with x where the answer goes</span>
      </label>
      <div class="row end">
        <button type="button" class="btn ghost" (click)="ref.close()">Cancel</button>
        <button type="submit" class="btn primary" [disabled]="!name().trim() || saving()">Create room</button>
      </div>
    </form>
  `,
})
export class NewRoomDialogComponent {
  private readonly data = inject<NewRoomData>(MAT_DIALOG_DATA);
  protected readonly ref = inject<MatDialogRef<NewRoomDialogComponent>>(MatDialogRef);
  protected readonly name = signal('');
  protected readonly tag = signal('CTF{x}');
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  protected async submit(): Promise<void> {
    if (!this.name().trim() || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set('');
    try {
      await this.data.create(this.name().trim(), this.tag().trim());
      this.ref.close(true);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The room could not be created.');
    } finally {
      this.saving.set(false);
    }
  }
}

@Component({
  selector: 'app-room-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <div class="page-head">
      <div>
        <h1 class="h1">Rooms and challenges</h1>
        <p class="sub">Rooms hold the challenges players solve. Open one to edit its challenges, flags and hints.</p>
      </div>
      <button type="button" class="btn primary" (click)="newRoom()"><app-icon name="plus" />New room</button>
    </div>

    @if (error()) {
      <div class="banner bad" role="alert"><app-icon name="alert" /><div class="grow">{{ error() }}</div>
        <button type="button" class="btn sm" (click)="load()">Try again</button></div>
    }

    <section class="card flush" aria-label="Rooms">
      @if (loading()) {
        <app-skeleton variant="rows" [count]="3" label="Loading rooms" />
      } @else if (!rooms().length) {
        <app-empty-state icon="flag" title="No rooms yet" message="Create a room, then add challenges to it or import them from a JSON file.">
          <button type="button" class="btn primary" (click)="newRoom()"><app-icon name="plus" />New room</button>
        </app-empty-state>
      } @else {
        <div class="table-wrap">
          <table class="t">
            <thead><tr><th>Room</th><th>Flag wrapper</th><th class="r">Challenges</th><th class="r">Points</th><th>Hidden or scheduled</th><th>Updated</th><th class="r">Actions</th></tr></thead>
            <tbody>
              @for (room of rooms(); track room.id) {
                <tr>
                  <td><a class="name" [routerLink]="room.id">{{ room.name }}</a></td>
                  <td class="mono small dim">{{ room.surroundTag }}</td>
                  <td class="r num">{{ stats().get(room.id)?.count ?? 0 }}</td>
                  <td class="r num">{{ (stats().get(room.id)?.points ?? 0).toLocaleString('en-US') }}</td>
                  <td class="dim small">{{ stats().get(room.id)?.held ?? 0 }}</td>
                  <td class="dim small">{{ ago(room.updatedAt) }}</td>
                  <td class="r"><div class="row end nowrap">
                    <a class="btn sm" [routerLink]="room.id"><app-icon name="edit" size="sm" />Edit</a>
                    <a class="btn sm ghost" [routerLink]="['/rooms', room.id]"><app-icon name="eye" size="sm" />View</a>
                    <button type="button" class="btn sm danger" (click)="remove(room)"><app-icon name="trash" size="sm" />Delete</button>
                  </div></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: `
    .name { font-weight: 500; color: var(--text); text-decoration: none; }
    .name:hover { text-decoration: underline; }
    .nowrap { flex-wrap: nowrap; }
  `,
  host: { class: 'content' },
})
export class RoomListPage {
  private readonly api = inject(AuthoringService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly playRooms = inject(RoomsStore);

  protected readonly rooms = signal<AuthorRoom[]>([]);
  private readonly challenges = signal<AuthorChallenge[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly ago = relativeTime;

  protected readonly stats = computed(() => {
    const now = Date.now();
    const map = new Map<string, { count: number; points: number; held: number }>();
    for (const challenge of this.challenges()) {
      if (!challenge.roomId) {
        continue;
      }
      const entry = map.get(challenge.roomId) ?? { count: 0, points: 0, held: 0 };
      entry.count++;
      entry.points += challenge.points;
      const opens = challenge.releaseDate ? Date.parse(challenge.releaseDate.replace(' ', 'T') + 'Z') : 0;
      if (!challenge.visible || opens > now) {
        entry.held++;
      }
      map.set(challenge.roomId, entry);
    }
    return map;
  });

  constructor() {
    this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const [rooms, challenges] = await Promise.all([firstValueFrom(this.api.rooms()), firstValueFrom(this.api.allChallenges())]);
      this.rooms.set([...rooms].sort((a, b) => a.name.localeCompare(b.name)));
      this.challenges.set(challenges);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'Rooms could not be loaded.');
    } finally {
      this.loading.set(false);
    }
  }

  protected newRoom(): void {
    const data: NewRoomData = {
      create: async (name, tag) => {
        const room = await firstValueFrom(this.api.createRoom(name, tag));
        this.playRooms.load();
        this.toast.success(`${room.name} created`);
        this.router.navigate(['/build/rooms', room.id]);
      },
    };
    this.dialog.open(NewRoomDialogComponent, { ...DIALOG_DEFAULTS, data });
  }

  protected async remove(room: AuthorRoom): Promise<void> {
    const count = this.stats().get(room.id)?.count ?? 0;
    const answer = await firstValueFrom(this.confirm.open({
      title: `Delete ${room.name}?`,
      message: `Its ${count} ${count === 1 ? 'challenge' : 'challenges'}, every answer and hint players used, and the list of who joined are deleted. `
        + 'Points scored in this room are lost.',
      confirmLabel: 'Delete room',
      typeToConfirm: room.name,
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await firstValueFrom(this.api.deleteRoom(room.id));
      this.rooms.update(rooms => rooms.filter(r => r.id !== room.id));
      this.playRooms.load();
      this.toast.success(`${room.name} deleted`);
    } catch (error) {
      this.toast.error(`${room.name} could not be deleted.`, error);
    }
  }
}

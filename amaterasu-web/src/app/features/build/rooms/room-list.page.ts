import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { relativeTime } from '../../../core/time';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { AuthorChallenge, AuthorRoom, AuthoringService, LabOption } from './authoring.service';

@Component({
  selector: 'app-room-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, EmptyStateComponent, SkeletonComponent],
  template: `
    <div class="page-head">
      <div>
        <h1 class="h1">Labs</h1>
        <p class="sub">A lab is a scenario, its questions and, if it needs one, an environment. Open one to edit it.</p>
      </div>
      <button type="button" class="btn primary" (click)="newRoom()"><app-icon name="plus" />New lab</button>
    </div>

    @if (error()) {
      <div class="banner bad" role="alert"><app-icon name="alert" /><div class="grow">{{ error() }}</div>
        <button type="button" class="btn sm" (click)="load()">Try again</button></div>
    }

    <section class="card flush" aria-label="Labs">
      @if (loading()) {
        <app-skeleton variant="rows" [count]="3" label="Loading labs" />
      } @else if (!rooms().length) {
        <app-empty-state icon="flask" title="No labs yet" message="Create a lab, then add questions to it or import them from a JSON file.">
          <button type="button" class="btn primary" (click)="newRoom()"><app-icon name="plus" />New lab</button>
        </app-empty-state>
      } @else {
        <div class="table-wrap">
          <table class="t">
            <thead><tr><th>Lab</th><th>Environment</th><th>Flag wrapper</th><th class="r">Questions</th><th class="r">Points</th><th>Hidden or scheduled</th><th>Updated</th><th class="r">Actions</th></tr></thead>
            <tbody>
              @for (room of rooms(); track room.id) {
                <tr>
                  <td><a class="name" [routerLink]="room.id">{{ room.name }}</a>@if (!room.published) { <span class="tag">Draft</span> }</td>
                  <td class="small dim">{{ envLabel(room) }}</td>
                  <td class="mono small dim">{{ room.surroundTag }}</td>
                  <td class="r num">{{ stats().get(room.id)?.count ?? 0 }}</td>
                  <td class="r num">{{ (stats().get(room.id)?.points ?? 0).toLocaleString('en-US') }}</td>
                  <td class="dim small">{{ stats().get(room.id)?.held ?? 0 }}</td>
                  <td class="dim small">{{ ago(room.updatedAt) }}</td>
                  <td class="r"><div class="row end nowrap">
                    <a class="btn sm" [routerLink]="room.id"><app-icon name="edit" size="sm" />Edit</a>
                    <a class="btn sm ghost" [routerLink]="['/labs', room.id]"><app-icon name="eye" size="sm" />View</a>
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
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly playRooms = inject(RoomsStore);

  protected readonly rooms = signal<AuthorRoom[]>([]);
  private readonly environments = signal<LabOption[]>([]);
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
      const [rooms, challenges, environments] = await Promise.all([firstValueFrom(this.api.rooms()),
        firstValueFrom(this.api.allChallenges()), firstValueFrom(this.api.labs())]);
      this.environments.set(environments);
      this.rooms.set([...rooms].sort((a, b) => a.name.localeCompare(b.name)));
      this.challenges.set(challenges);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'Labs could not be loaded.');
    } finally {
      this.loading.set(false);
    }
  }

  protected envLabel(room: AuthorRoom): string {
    if (room.environmentMode === 'NONE') return 'None';
    const name = this.environments().find(lab => lab.id === room.environmentId)?.name ?? 'An environment';
    return `${name} · ${room.environmentMode === 'SHARED' ? 'shared' : 'per team'}`;
  }

  protected newRoom(): void {
    this.router.navigate(['/build/labs', 'new']);
  }

  protected async remove(room: AuthorRoom): Promise<void> {
    const count = this.stats().get(room.id)?.count ?? 0;
    const answer = await firstValueFrom(this.confirm.open({
      title: `Delete ${room.name}?`,
      message: `Its ${count} ${count === 1 ? 'question' : 'questions'}, every answer and hint players used, and the list of who joined are deleted. `
        + 'Points scored in this lab are lost.',
      confirmLabel: 'Delete lab',
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

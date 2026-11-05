import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, firstValueFrom, forkJoin, of } from 'rxjs';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { SessionStore } from '../../../core/stores/session.store';
import { RoomUserStatus } from '../../../enums/room-user-status.enum';
import { ordinal, points } from '../../../shared/format';
import { ToastService } from '../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { PlayApiService } from '../data/play-api.service';
import { RoomOverview } from '../data/play.models';

/** Every room, with whether you joined it and how you are doing there. */
@Component({
  selector: 'app-rooms-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, IconComponent, EmptyStateComponent, SkeletonComponent, StatusBadgeComponent],
  templateUrl: './rooms.page.html',
  styles: `
    .search { margin-left: 0; flex: none; width: 100%; max-width: 420px; }
    .room { height: 100%; }
    .room h3 { font-size: 15.5px; }
    .grow-space { flex: 1 1 auto; }
  `,
  host: { class: 'content' },
})
export class RoomsPage {
  protected readonly rooms = inject(RoomsStore);
  protected readonly session = inject(SessionStore);
  private readonly api = inject(PlayApiService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly query = signal('');
  protected readonly busy = signal('');
  private readonly overviews = signal<Record<string, RoomOverview>>({});
  protected readonly fmt = { ordinal, points };

  constructor() {
    this.rooms.load();
    effect(() => {
      const ids = this.rooms.rooms().map(room => room.id!).filter(Boolean);
      untracked(() => this.loadOverviews(ids));
    });
  }

  private async loadOverviews(ids: string[]): Promise<void> {
    if (!ids.length) return;
    const results = await firstValueFrom(forkJoin(ids.map(id => this.api.overview(id).pipe(catchError(() => of(null))))));
    const overviews: Record<string, RoomOverview> = {};
    results.forEach(result => {
      if (result) overviews[result.room.id] = result;
    });
    this.overviews.set(overviews);
  }

  protected readonly cards = computed(() => {
    const query = this.query().trim().toLowerCase();
    return this.rooms.rooms()
      .filter(room => !query || `${room.name} ${room.surroundTag ?? ''}`.toLowerCase().includes(query))
      .map(room => {
        const status = this.rooms.membership()[room.id!]?.roomUserStatus;
        const overview = this.overviews()[room.id!];
        const total = overview?.challenges.length ?? 0;
        return {
          id: room.id!,
          name: room.name ?? 'Room',
          tag: room.surroundTag,
          joined: status === RoomUserStatus.JOINED,
          left: status === RoomUserStatus.LEFT,
          overview,
          total,
          pct: total ? Math.round(((overview?.me.solved ?? 0) / total) * 100) : 0,
        };
      });
  });

  protected async join(roomId: string, name: string): Promise<void> {
    this.busy.set(roomId);
    try {
      await firstValueFrom(this.rooms.join(roomId));
      this.rooms.setCurrentRoom(roomId);
      this.toast.success(`Joined ${name}`);
      this.router.navigate(['/rooms', roomId]);
    } catch (error) {
      this.toast.error(`Could not join ${name}`, error);
    } finally {
      this.busy.set('');
    }
  }

  protected async leave(roomId: string, name: string): Promise<void> {
    this.busy.set(roomId);
    try {
      await firstValueFrom(this.rooms.leave(roomId));
      this.toast.success(`Left ${name}. Your points stay if you rejoin.`);
    } catch (error) {
      this.toast.error(`Could not leave ${name}`, error);
    } finally {
      this.busy.set('');
    }
  }
}

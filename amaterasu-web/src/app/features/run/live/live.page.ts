import { ChangeDetectionStrategy, Component, computed, effect, inject, input, untracked } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, firstValueFrom, map, of } from 'rxjs';
import { Room } from '../../../models/ctf/room.model';
import { RoomService } from '../../../services/ctf/room.service';
import { CrumbsService } from '../../../core/crumbs.service';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { SessionStore } from '../../../core/stores/session.store';
import { ago, clock, signed } from '../../../shared/format';
import { ToastService } from '../../../shared/services/toast.service';
import { DIALOG_DEFAULTS } from '../../../shared/ui/dialog';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { FeedKind } from '../../play/data/play.models';
import { AwardDialogComponent, AwardDialogData, AwardResult } from './award-dialog.component';
import { FeedFilter, LiveStore } from './live.store';

/** The facilitator's live event view: what needs attention, every team's labs, challenge health and the feed. */
@Component({
  selector: 'app-live-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, EmptyStateComponent, SkeletonComponent],
  providers: [LiveStore],
  templateUrl: './live.page.html',
  styleUrl: './live.page.scss',
  host: { class: 'content' },
})
export class LivePage {
  protected readonly store = inject(LiveStore);
  protected readonly rooms = inject(RoomsStore);
  protected readonly session = inject(SessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly crumbs = inject(CrumbsService);

  /** ?room= picks the room; otherwise the current room, then the first one. */
  readonly room = input<string>();

  protected readonly fmt = { ago, clock, signed };
  protected readonly filters: { value: FeedFilter; label: string }[] = [
    { value: 'all', label: 'All' }, { value: 'SOLVE', label: 'Solves' }, { value: 'HINT', label: 'Hints' }, { value: 'AWARD', label: 'Awards' },
  ];

  /** Rooms this person may run; null while loading. */
  protected readonly runnable = toSignal(inject(RoomService).getFacilitatedRooms().pipe(
    map(response => (response.data ?? []).map(room => new Room(room))),
    catchError(() => of([] as Room[])),
  ), { initialValue: null });
  protected readonly runRooms = computed(() => this.runnable() ?? []);

  protected readonly roomId = computed(() => {
    const allowed = new Set(this.runRooms().map(room => room.id));
    return [this.room(), this.rooms.currentRoom()?.id, this.runRooms()[0]?.id].find(id => !!id && allowed.has(id)) ?? null;
  });

  constructor() {
    this.rooms.load();
    effect(() => {
      const id = this.roomId();
      if (id) untracked(() => this.store.load(id));
    });
    effect(() => {
      const name = this.store.live()?.roomName;
      if (name) untracked(() => this.crumbs.set([{ label: 'Run' }, { label: name }]));
    });
  }

  protected readonly feedTone = (kind: FeedKind) => (kind === 'SOLVE' ? 's-active' : kind === 'HINT' ? 's-warn' : 's-accent');
  protected readonly solveRate = (solves: number) => {
    const players = this.store.live()?.playerCount ?? 0;
    return players ? Math.round((solves / players) * 100) : 0;
  };

  protected pickRoom(id: string): void {
    this.router.navigate([], { queryParams: { room: id }, replaceUrl: true });
  }

  protected async openAward(): Promise<void> {
    const live = this.store.live();
    if (!live) return;
    const result = await firstValueFrom(this.dialog.open<AwardDialogComponent, AwardDialogData, AwardResult>(AwardDialogComponent, {
      ...DIALOG_DEFAULTS,
      width: '520px',
      data: { roomId: live.roomId, roomName: live.roomName, players: live.players },
    }).afterClosed());
    if (result) {
      this.toast.success(`${signed(result.points)} pts to ${result.username}`);
      this.store.reloadLive();
    }
  }
}

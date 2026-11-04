import { ChangeDetectionStrategy, Component, computed, effect, inject, input, untracked } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { CrumbsService } from '../../../core/crumbs.service';
import { ordinal, points } from '../../../shared/format';
import { ToastService } from '../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { ChallengeDetailComponent } from './challenge-detail.component';
import { RoomPlayStore, StatusFilter } from './room-play.store';

/** A room: its challenges on the left, the chosen one on the right, and your standing on top. */
@Component({
  selector: 'app-room-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TitleCasePipe, IconComponent, EmptyStateComponent, SkeletonComponent, ChallengeDetailComponent],
  providers: [RoomPlayStore],
  templateUrl: './room.page.html',
  styleUrl: './room.page.scss',
  host: { class: 'content' },
})
export class RoomPage {
  protected readonly store = inject(RoomPlayStore);
  private readonly crumbs = inject(CrumbsService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly roomId = input.required<string>();
  /** The selected challenge, kept in the address so it can be shared. */
  readonly challenge = input<string>();

  protected readonly fmt = { ordinal, points };
  protected readonly statuses: { value: StatusFilter; label: string }[] = [
    { value: 'all', label: 'All' }, { value: 'open', label: 'Open' }, { value: 'solved', label: 'Solved' },
  ];

  protected readonly overview = computed(() => this.store.overview());
  protected readonly byline = computed(() => {
    const room = this.overview()?.room;
    if (!room) return '';
    const parts: string[] = [];
    if (room.creator) parts.push(`Created by ${room.creator}`);
    if (room.facilitators.length) parts.push(`facilitated by ${room.facilitators.join(' and ')}`);
    return parts.join(' · ');
  });

  constructor() {
    effect(() => {
      const id = this.roomId();
      untracked(() => this.store.load(id));
    });
    effect(() => {
      const id = this.challenge();
      if (id) untracked(() => this.store.select(id));
    });
    effect(() => {
      const name = this.overview()?.room.name;
      if (name) untracked(() => this.crumbs.set([{ label: 'Rooms', link: '/rooms' }, { label: name }]));
    });
  }

  protected pick(id: string): void {
    this.store.select(id);
    this.router.navigate([], { queryParams: { challenge: id }, replaceUrl: true });
    // On narrow screens the detail sits under the list
    if (window.matchMedia?.('(max-width: 959.98px)').matches) {
      setTimeout(() => document.getElementById('ch-title')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    }
  }

  protected async join(): Promise<void> {
    const error = await this.store.join();
    if (error) {
      this.toast.error(error);
    } else {
      this.toast.success(`Joined ${this.overview()?.room.name ?? 'the room'}`);
    }
  }
}

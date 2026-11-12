import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../../core/http-error';
import { dateTime, signed } from '../../../../shared/format';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../../../shared/ui/skeleton.component';
import { LabManageApi } from './lab-manage-api.service';
import { ActivityItem, ActivityKind } from './lab-manage.models';
import { ManageActions } from './manage-actions.service';

type ActivityFilter = 'all' | 'play' | 'staff' | 'environment';

const GROUP: Record<ActivityKind, Exclude<ActivityFilter, 'all'>> = {
  FIRST_ACTIVITY: 'play', HINT: 'play', SOLVE: 'play', CREDIT: 'staff', AWARD: 'staff', TEAM_AWARD: 'staff', RESET: 'staff',
  ACCEPTED_ANSWER: 'staff', TEAMS: 'staff', ENVIRONMENT: 'environment',
};

const TONE: Record<ActivityKind, string> = {
  FIRST_ACTIVITY: 's-off', HINT: 's-warn', SOLVE: 's-active', CREDIT: 's-accent', AWARD: 's-accent', TEAM_AWARD: 's-accent',
  RESET: 's-bad', ACCEPTED_ANSWER: 's-accent', TEAMS: 's-work', ENVIRONMENT: 's-work',
};

/** The lab's own feed: first activity, hints, solves, credit and awards, resets, team changes and the environment. */
@Component({
  selector: 'app-manage-activity',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, SkeletonComponent],
  template: `
    <section class="card flush" aria-label="Activity">
      <div class="card-head">
        <div class="seg" role="group" aria-label="Filter activity">
          @for (option of filters; track option.value) {
            <button type="button" [class.on]="filter() === option.value" [attr.aria-pressed]="filter() === option.value"
                    (click)="filter.set(option.value)">{{ option.label }}</button>
          }
        </div>
      </div>
      @if (items(); as all) {
        <div class="feed">
          @for (item of shown(); track $index) {
            <div class="feed-item">
              <div class="feed-ico" [class]="'feed-ico ' + tone[item.kind]"><span class="pill-dot"></span></div>
              <div class="grow"><div><strong>{{ item.who ?? 'Someone' }}</strong> {{ item.text }}</div>
                <div class="small muted">{{ item.detail ? item.detail + ' · ' : '' }}{{ item.team ? item.team + ' · ' : '' }}{{ fmt.dateTime(item.at) }}</div></div>
              @if (item.points) {<span class="num small pts">{{ fmt.signed(item.points) }}</span>}
            </div>
          } @empty {
            <div class="empty">{{ all.length ? 'Nothing of this kind yet.' : 'No activity yet.' }}</div>
          }
        </div>
      } @else if (error()) {
        <app-empty-state icon="alert" title="Activity could not be loaded" [message]="error()!" />
      } @else {
        <app-skeleton variant="rows" [count]="6" label="Loading activity" />
      }
    </section>
  `,
  styleUrls: ['./manage-shared.scss'],
  styles: `.pts { font-weight: 600; }`,
})
export class ManageActivityComponent {
  private readonly api = inject(LabManageApi);
  private readonly actions = inject(ManageActions);

  protected readonly filters: { value: ActivityFilter; label: string }[] = [
    { value: 'all', label: 'All' }, { value: 'play', label: 'Players' }, { value: 'staff', label: 'Staff' },
    { value: 'environment', label: 'Environment' },
  ];
  protected readonly tone = TONE;
  protected readonly fmt = { dateTime, signed };
  protected readonly filter = signal<ActivityFilter>('all');
  protected readonly items = signal<ActivityItem[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly shown = computed(() => {
    const filter = this.filter();
    const items = this.items() ?? [];
    return filter === 'all' ? items : items.filter(item => GROUP[item.kind] === filter);
  });

  constructor() {
    effect(() => {
      const id = this.actions.labId();
      this.actions.version();
      if (id) untracked(() => this.load(id));
    });
  }

  private async load(id: string): Promise<void> {
    try {
      this.items.set(await firstValueFrom(this.api.activity(id)));
      this.error.set(null);
    } catch (error) {
      if (!this.items()) this.error.set(errorMessage(error) ?? 'Check your connection and try again.');
    }
  }
}

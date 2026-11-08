import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { roomActivity } from '../../../core/room-activity';
import { isLabTrackerMessage } from '../../../core/stores/lab-trackers.store';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { SessionStore } from '../../../core/stores/session.store';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { WebsocketService } from '../../../services/websocket.service';
import { points } from '../../../shared/format';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { PlayApiService } from '../data/play-api.service';
import { EnvironmentView, LabCard } from '../data/play.models';
import { ENVIRONMENT_STATUS_META, environmentStatus, trackerOf, withTrackerChange } from './environment-status';

/**
 * Every lab: its scenario in a line, whether its environment runs, and how far you got. Anyone can open
 * any lab; admins and facilitators also start and stop shared environments from here.
 */
@Component({
  selector: 'app-labs-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, IconComponent, EmptyStateComponent, SkeletonComponent, StatusBadgeComponent],
  template: `
    <app-page-header title="Labs" subtitle="Each lab has a scenario, questions to answer and, for some, an environment to work in.">
      @if (session.canBuild()) {
        <a class="btn" routerLink="/build/labs/new"><app-icon name="plus" />Create a lab</a>
      }
    </app-page-header>

    <label class="search"><app-icon name="search" size="sm" />
      <input type="search" placeholder="Find a lab" aria-label="Find a lab" [value]="query()" (input)="query.set($any($event.target).value)"></label>

    @if (loading() && !cards().length) {
      <app-skeleton variant="card" [count]="3" label="Loading labs" />
    } @else if (error()) {
      <div class="banner bad" role="alert"><app-icon name="alert" /><div class="grow row between"><span>{{ error() }}</span><button type="button" class="btn sm" (click)="load()">Try again</button></div></div>
    } @else {
      <div class="grid wide">
        @for (card of visible(); track card.id) {
          <article class="card stack lab" [attr.aria-label]="card.name">
            <div class="row badges">
              @if (card.view.meta; as meta) {
                <app-status-badge [tone]="meta.tone" [label]="meta.label" />
                <span class="tag">{{ card.environmentMode === 'SHARED' ? 'Shared environment' : 'Team copies' }}</span>
                @if (card.environmentMode === 'PER_TEAM' && card.canRun && card.environment!.copiesRunning) {
                  <span class="tag">{{ card.environment!.copiesRunning }} running</span>
                }
              } @else {
                <span class="tag accent">No setup needed</span>
              }
            </div>
            <h3 class="h3"><a class="name" [routerLink]="['/labs', card.id]">{{ card.name }}</a></h3>
            @if (card.summary) {
              <p class="small dim summary">{{ card.summary }}</p>
            }
            <div class="small muted">
              {{ card.questionCount }} {{ card.questionCount === 1 ? 'question' : 'questions' }} · {{ card.playerCount }} {{ card.playerCount === 1 ? 'player' : 'players' }}
            </div>
            @if (card.points || card.solved) {
              <div class="bar" aria-hidden="true"><span [style.width.%]="card.view.pct"></span></div>
              <div class="row between small muted">
                <span>{{ card.solved }} of {{ card.questionCount }} solved</span>
                <span class="num">{{ fmt.points(card.points) }} pts</span>
              </div>
            }
            <div class="grow-space"></div>
            <div class="row">
              <a class="btn sm primary" [routerLink]="['/labs', card.id]" (click)="rooms.setCurrentRoom(card.id)"><app-icon name="flask" size="sm" />Open lab</a>
              <a class="btn sm" [routerLink]="['/labs', card.id, 'scoreboard']"><app-icon name="trophy" size="sm" />Scoreboard</a>
              @if (card.canRun && card.environment; as env) {
                <span class="grow"></span>
                @if (card.environmentMode === 'SHARED' && env.canControl) {
                  @if (busy() === card.id) {
                    <button type="button" class="btn sm" disabled><app-icon name="spinner" size="sm" [spin]="true" />Working</button>
                  } @else if (card.view.status === 'RUNNING' || card.view.status === 'PAUSED') {
                    <button type="button" class="btn sm danger" (click)="stopShared(card)"><app-icon name="stop" size="sm" />Stop</button>
                  } @else if (card.view.status !== 'STARTING') {
                    <button type="button" class="btn sm" (click)="startShared(card)"><app-icon name="play" size="sm" />{{ card.view.status === 'FAILED' ? 'Start fresh' : 'Start' }}</button>
                  }
                } @else if (card.environmentMode === 'PER_TEAM') {
                  <a class="btn sm ghost" [routerLink]="['/labs', card.id]" title="Start and stop each team's copy">Manage</a>
                }
              }
            </div>
          </article>
        }
      </div>
      @if (!visible().length) {
        <section class="card">
          <app-empty-state icon="flask" [title]="query() ? 'No labs match' : 'No labs yet'"
            [message]="query() ? 'Try another name.' : 'Labs show here once someone creates one.'" />
        </section>
      }
    }
  `,
  styles: `
    .search { margin-left: 0; flex: none; width: 100%; max-width: 420px; }
    .lab { height: 100%; }
    .lab h3 { font-size: 15.5px; margin: 0; }
    .name { color: var(--text); text-decoration: none; }
    .name:hover { text-decoration: underline; }
    .summary { margin: 0; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .badges { gap: 6px; }
    .grow-space { flex: 1 1 auto; }
  `,
  host: { class: 'content' },
})
export class LabsPage {
  protected readonly session = inject(SessionStore);
  protected readonly rooms = inject(RoomsStore);
  private readonly api = inject(PlayApiService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  protected readonly cards = signal<LabCard[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly query = signal('');
  protected readonly busy = signal('');
  protected readonly fmt = { points };

  protected readonly visible = computed(() => {
    const query = this.query().trim().toLowerCase();
    return this.cards()
      .filter(card => !query || `${card.name} ${card.summary}`.toLowerCase().includes(query))
      .map(card => ({
        ...card,
        view: {
          status: card.environment ? environmentStatus(trackerOf(card.environment)) : null,
          meta: card.environment ? ENVIRONMENT_STATUS_META[environmentStatus(trackerOf(card.environment))] : null,
          pct: card.questionCount ? Math.round((card.solved / card.questionCount) * 100) : 0,
        },
      }));
  });

  constructor() {
    this.load();
    const socket = inject(WebsocketService);
    roomActivity(socket.getMessage(), roomId => this.cards().some(card => card.id === roomId))
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load());
    socket.getMessage()
      .pipe(filter(isLabTrackerMessage), takeUntilDestroyed())
      .subscribe(message => {
        const tracker = new LabTracker(message.labTracker);
        const removed = message.action === 'removed';
        this.cards.update(cards => cards.map(card => {
          if (!card.environment) return card;
          const environment = withTrackerChange(card.environment, card.id, tracker, removed, card.teamId);
          return environment === card.environment ? card : { ...card, environment };
        }));
      });
  }

  protected async load(): Promise<void> {
    try {
      this.cards.set(await firstValueFrom(this.api.cards()));
      this.error.set('');
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'Labs could not be loaded.');
    } finally {
      this.loading.set(false);
    }
  }

  /** Admins' and facilitators' quick Start for a shared lab. */
  protected async startShared(card: LabCard): Promise<void> {
    await this.act(card, 'start', `${card.name} is running`, `${card.name} did not start`);
  }

  protected async stopShared(card: LabCard): Promise<void> {
    const choice = await firstValueFrom(this.confirm.open({
      title: `Stop ${card.name}?`,
      message: 'The shared copy is destroyed and everyone working in it loses their progress there. To keep it, pause it from the lab page.',
      confirmLabel: 'Stop and destroy',
      typeToConfirm: card.name,
    }));
    if (choice === 'confirm') await this.act(card, 'stop', `${card.name} stopped`, `${card.name} did not stop`);
  }

  private async act(card: LabCard, action: 'start' | 'stop', done: string, failed: string): Promise<void> {
    this.busy.set(card.id);
    try {
      const result = await firstValueFrom(this.api.environmentAction(card.id, action, 'shared'));
      if (result.environment) this.setEnvironment(card.id, { ...result.environment, copies: [] });
      this.toast.success(done);
    } catch (error) {
      this.toast.error(failed, error);
    } finally {
      this.busy.set('');
    }
  }

  private setEnvironment(cardId: string, environment: EnvironmentView): void {
    this.cards.update(cards => cards.map(card => (card.id === cardId ? { ...card, environment } : card)));
  }
}

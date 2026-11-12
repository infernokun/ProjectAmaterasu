import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../../core/http-error';
import { ago } from '../../../../shared/format';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../../../shared/ui/skeleton.component';
import { ChartSeries, PointsChartComponent } from '../../scoreboard/points-chart.component';
import { ENVIRONMENT_STATUS_META } from '../environment-status';
import { LabManageApi } from './lab-manage-api.service';
import { ManageOverview } from './lab-manage.models';
import { ManageActions } from './manage-actions.service';

const MODE_LABEL = { SITE: 'Site teams', NONE: 'No teams', LAB: 'Lab teams' } as const;

/** The lab at a glance: who is playing, solves over time, solve rate per question, hints and the environment's copies. */
@Component({
  selector: 'app-manage-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, SkeletonComponent, PointsChartComponent],
  template: `
    @if (overview(); as o) {
      <div class="stack">
        <div class="kpis">
          <div class="card"><div class="kpi-label">Players</div><div class="kpi-val">{{ o.activePlayers }} / {{ o.players }}</div>
            <div class="kpi-note">active in the last 15 min{{ o.teams ? ' · ' + o.teams + (o.teams === 1 ? ' team' : ' teams') : '' }}</div></div>
          <div class="card"><div class="kpi-label">Solves</div><div class="kpi-val">{{ o.solves }}</div>
            <div class="kpi-note">{{ o.attempts }} attempts, {{ o.wrongAttempts }} wrong</div></div>
          <button type="button" class="card kpi-btn" (click)="actions.showSubmissions({ result: 'near' })" [disabled]="!o.nearMisses">
            <div class="kpi-label">Near misses</div><div class="kpi-val" [class.s-warn]="o.nearMisses">{{ o.nearMisses }}</div>
            <div class="kpi-note">{{ o.nearMisses ? 'Review them in Submissions' : 'None so far' }}</div></button>
          <div class="card"><div class="kpi-label">Hints used</div><div class="kpi-val">{{ o.hintsUsed }}</div>
            <div class="kpi-note">{{ o.hintPoints ? o.hintPoints + ' pts spent' : 'No points spent' }}</div></div>
        </div>

        <section class="card stack tight" aria-labelledby="ov-chart">
          <div class="row between"><h3 class="h3" id="ov-chart">Solves over time</h3>
            <span class="small muted">{{ modeLabel[o.teamMode] }} · {{ o.teamCredit === 'SHARED_SOLVES' ? 'shared solves' : 'teammates see solves' }}</span></div>
          @if (o.solves) {
            <app-points-chart [series]="series()" [start]="start()" label="Solves in this lab over time" />
          } @else {
            <p class="small muted">No solves yet{{ o.firstActivityAt ? '; first activity ' + fmt.ago(o.firstActivityAt) : '' }}.</p>
          }
        </section>

        <section class="card flush" aria-labelledby="ov-rates">
          <div class="card-head"><h3 class="h3" id="ov-rates">Solve rate per question</h3><span class="small muted">of {{ o.players }} players</span></div>
          @for (question of o.questionRates; track question.id) {
            <div class="rate-row">
              <div class="row between"><span class="grow title">{{ question.title }}</span>
                <span class="small num">{{ question.solves }} / {{ question.attemptedBy }} tried · {{ question.solveRate }}%</span></div>
              <div class="bar" [class.warn]="question.solveRate < 20 && question.attemptedBy > 0" [class.ok]="question.solveRate >= 40">
                <span [style.width.%]="question.solveRate"></span></div>
            </div>
          } @empty {
            <app-empty-state icon="flag" title="No questions in this lab" />
          }
        </section>

        @if (o.copies.length) {
          <section class="card flush" aria-labelledby="ov-copies">
            <div class="card-head"><h3 class="h3" id="ov-copies">Environment copies</h3><span class="small muted">{{ running() }} running</span></div>
            @for (copy of o.copies; track copy.target) {
              <div class="row between copy">
                <span>{{ copy.label }} <span class="small muted">{{ copy.members }} {{ copy.members === 1 ? 'person' : 'people' }}</span></span>
                <span class="chip" [class]="'chip s-' + statusMeta[copy.status].tone">{{ statusMeta[copy.status].label }}</span>
              </div>
            }
          </section>
        }
      </div>
    } @else if (error()) {
      <section class="card"><app-empty-state icon="alert" title="The overview could not be loaded" [message]="error()!" /></section>
    } @else {
      <app-skeleton variant="card" [count]="3" label="Loading the overview" />
    }
  `,
  styleUrls: ['./manage-shared.scss'],
  styles: `
    .kpi-btn { text-align: left; font: inherit; color: inherit; cursor: pointer; }
    .kpi-btn:hover:not([disabled]) { border-color: var(--border-2); }
    .kpi-btn[disabled] { cursor: default; }
    .rate-row { padding: 10px var(--pad-card); border-bottom: 1px solid var(--border); display: flex; flex-direction: column; gap: 6px; }
    .rate-row:last-child { border-bottom: 0; }
    .title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .copy { padding: 10px var(--pad-card); border-bottom: 1px solid var(--border); }
    .copy:last-child { border-bottom: 0; }
    @container (max-width: 560px) { .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } .kpi-val { font-size: 22px; } }
  `,
})
export class ManageOverviewComponent {
  private readonly api = inject(LabManageApi);
  protected readonly actions = inject(ManageActions);

  protected readonly overview = signal<ManageOverview | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly statusMeta = ENVIRONMENT_STATUS_META;
  protected readonly modeLabel = MODE_LABEL;
  protected readonly fmt = { ago };

  /** Running solve count, one step per bucket that had solves. */
  protected readonly series = computed<ChartSeries[]>(() => {
    let total = 0;
    const points = (this.overview()?.solvesOverTime ?? []).filter(bucket => bucket.solves).map(bucket => {
      total += bucket.solves;
      return { at: new Date(bucket.at).getTime(), total };
    });
    return [{ name: 'Solves', tone: 'you', points }];
  });
  protected readonly start = computed(() => {
    const first = this.overview()?.solvesOverTime[0]?.at;
    return first ? new Date(first).getTime() : null;
  });
  protected readonly running = computed(() => (this.overview()?.copies ?? []).filter(copy => copy.status === 'RUNNING').length);

  constructor() {
    effect(() => {
      const id = this.actions.labId();
      this.actions.version();
      if (id) untracked(() => this.load(id));
    });
  }

  private async load(id: string): Promise<void> {
    try {
      const overview = await firstValueFrom(this.api.overview(id));
      if (id === this.actions.labId()) {
        this.overview.set(overview);
        this.error.set(null);
      }
    } catch (error) {
      if (!this.overview()) this.error.set(errorMessage(error) ?? 'Check your connection and try again.');
    }
  }
}

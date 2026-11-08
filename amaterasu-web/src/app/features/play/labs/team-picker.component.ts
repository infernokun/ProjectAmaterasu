import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { errorMessage } from '../../../core/http-error';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PlayApiService } from '../data/play-api.service';
import { LabSeat, LabTeam, TeamChoice, TeamMode } from '../data/play.models';

/** Joining a lab, or changing team in it: play solo, join a team, or create one. */
@Component({
  selector: 'app-team-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <section class="card stack" [attr.aria-label]="joined() ? 'Change team' : 'Join ' + labName()">
      <div class="row between">
        <h2 class="h2">{{ joined() ? 'Change your team' : 'How do you want to play?' }}</h2>
        <button type="button" class="iconbtn" aria-label="Close" (click)="closed.emit()"><app-icon name="close" /></button>
      </div>
      <p class="small dim intro">Answers and points are always your own.
        {{ teamMode() === 'TEAMS_REQUIRED' ? 'This lab is played in teams.' : '' }}
        {{ maxTeamSize() ? 'Teams have up to ' + maxTeamSize() + ' players.' : '' }}</p>
      @if (error()) {
        <div class="banner bad" role="alert"><app-icon name="alert" /><div>{{ error() }}</div></div>
      }
      <div class="options">
        @if (teamMode() !== 'TEAMS_REQUIRED') {
          <div class="option row between">
            <div class="stack tight"><strong>Play solo</strong><span class="small muted">On your own; you can team up later.</span></div>
            <button type="button" class="btn sm" [disabled]="busy() || (joined() && !currentTeamId())" (click)="choose({})">
              {{ joined() && !currentTeamId() ? 'Playing solo' : 'Play solo' }}</button>
          </div>
        }
        @for (team of teams(); track team.id) {
          <div class="option row between">
            <div class="stack tight">
              <strong>{{ team.name }}</strong>
              <span class="small muted">{{ team.members.length ? team.members.join(', ') : 'No players yet' }}{{ maxTeamSize() ? ' · ' + team.size + ' of ' + maxTeamSize() : '' }}</span>
            </div>
            @if (team.id === currentTeamId()) {
              <span class="tag">Your team</span>
            } @else {
              <button type="button" class="btn sm" [disabled]="busy() || team.full" (click)="choose({ teamId: team.id })">
                {{ team.full ? 'Full' : 'Join team' }}</button>
            }
          </div>
        }
        <form class="option row create" (submit)="$event.preventDefault(); create()">
          <label class="field grow"><span class="label">New team</span>
            <input class="input" [value]="newName()" (input)="newName.set($any($event.target).value)" maxlength="40" placeholder="Night Owls"></label>
          <button type="submit" class="btn sm primary" [disabled]="busy() || !newName().trim()">Create and join</button>
        </form>
      </div>
    </section>
  `,
  styles: `
    :host { display: block; }
    .intro { margin: 0; }
    .options { display: flex; flex-direction: column; gap: 8px; }
    .option { padding: 10px 12px; border: 1px solid var(--border); border-radius: 10px; gap: 12px; }
    .create { align-items: flex-end; }
  `,
})
export class TeamPickerComponent {
  private readonly api = inject(PlayApiService);

  readonly labId = input.required<string>();
  readonly labName = input('');
  readonly teamMode = input<TeamMode>('TEAMS_OPTIONAL');
  readonly maxTeamSize = input<number | null>(null);
  readonly joined = input(false);
  readonly currentTeamId = input<string | null>(null);
  readonly done = output<LabSeat>();
  readonly closed = output<void>();

  protected readonly teams = signal<LabTeam[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly newName = signal('');
  protected readonly hasTeams = computed(() => this.teams().length > 0);

  constructor() {
    effect(() => {
      const id = this.labId();
      untracked(() => this.load(id));
    });
  }

  private async load(id: string): Promise<void> {
    try {
      this.teams.set(await firstValueFrom(this.api.labTeams(id)));
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'The teams could not be loaded.');
    }
  }

  protected create(): void {
    const name = this.newName().trim();
    if (name) this.choose({ newTeamName: name });
  }

  protected async choose(choice: TeamChoice): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const seat = await firstValueFrom(this.joined()
        ? this.api.changeTeam(this.labId(), choice) : this.api.joinLab(this.labId(), choice));
      this.done.emit(seat);
    } catch (error) {
      this.error.set(errorMessage(error) ?? 'That did not work. Try again.');
      await this.load(this.labId());
    } finally {
      this.busy.set(false);
    }
  }
}

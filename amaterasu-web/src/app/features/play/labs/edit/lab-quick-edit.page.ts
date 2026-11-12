import { ChangeDetectionStrategy, Component, computed, effect, inject, input, linkedSignal, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ConfirmService } from '../../../../shared/services/confirm.service';
import { ToastService } from '../../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../../shared/ui/skeleton.component';
import { ChallengeFormComponent } from '../../../build/rooms/challenge-form.component';
import { RoomEditorStore } from '../../../build/rooms/room-editor.store';
import { ScenarioFieldComponent } from '../../../build/rooms/scenario-field.component';
import { RoomPlayStore } from '../../rooms/room-play.store';
import { LeavesEdits } from './leave-edit.guard';

/**
 * Quick edits from the lab page: the scenario, and one question's text, flags, hints and points,
 * with the full editor's own form pieces. The rest stays in the full editor.
 */
@Component({
  selector: 'app-lab-quick-edit-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, SkeletonComponent, EmptyStateComponent, ChallengeFormComponent, ScenarioFieldComponent],
  providers: [RoomEditorStore],
  host: { class: 'stack loose' },
  template: `
    @if (store.status() === 'error') {
      <div class="banner bad" role="alert"><app-icon name="alert" /><div class="grow">{{ store.error() }}</div></div>
    } @else if (!store.room()) {
      <app-skeleton variant="card" [count]="2" label="Loading the editor" />
    } @else {
      <section class="card flush" aria-labelledby="qe-question-h">
        <div class="card-head">
          <h2 class="h2" id="qe-question-h">Question</h2>
          @if (store.challenges().length) {
            <label class="field q-pick"><span class="visually-hidden">Question to edit</span>
              <select class="input" (change)="pickQuestion($any($event.target))">
                @for (question of store.challenges(); track question.id) {
                  <option [value]="question.id" [selected]="question.id === store.selectedId()">{{ question.question }}</option>
                }
              </select></label>
          }
          <div class="row">
            @if (store.dirty()) { <span class="chip s-warn"><span class="dot"></span>Unsaved</span> }
            <button type="button" class="btn sm primary" [disabled]="!store.draft() || !store.dirty() || !!store.problem() || store.saving()"
                    [title]="store.problem() ?? ''" (click)="saveQuestion()">
              @if (store.saving()) { <app-icon name="spinner" size="sm" [spin]="true" /> }
              Save question</button>
          </div>
        </div>
        @if (store.draft()) {
          <app-challenge-form [quick]="true" />
        } @else {
          <app-empty-state icon="flag" title="No questions yet" message="Add questions in the full editor." />
        }
      </section>

      <section class="card stack" aria-labelledby="qe-scenario-h">
        <div class="row between">
          <h2 class="h2" id="qe-scenario-h">Scenario</h2>
          <div class="row">
            @if (scenarioDirty()) { <span class="chip s-warn"><span class="dot"></span>Unsaved</span> }
            <button type="button" class="btn sm primary" [disabled]="!scenarioDirty() || !scenario().trim() || savingScenario()"
                    [title]="scenario().trim() ? '' : 'Write the scenario players read first.'" (click)="saveScenario()">
              @if (savingScenario()) { <app-icon name="spinner" size="sm" [spin]="true" /> }
              Save scenario</button>
          </div>
        </div>
        <app-scenario-field [(value)]="scenario" />
      </section>

      <div class="row">
        <a class="btn" [routerLink]="['/build/labs', labId()]" [queryParams]="{ from: 'lab', step: 'questions' }">
          <app-icon name="edit" />Open full editor</a>
        <span class="small muted">New questions, the schedule, files, environment, teams and publishing are there.</span>
      </div>
    }
  `,
  styles: `
    .q-pick { flex: 1 1 220px; max-width: 420px; }
  `,
})
export class LabQuickEditPage implements LeavesEdits {
  protected readonly store = inject(RoomEditorStore);
  /** The lab page around this one, refreshed after a save. */
  private readonly lab = inject(RoomPlayStore, { optional: true });
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly labId = input.required<string>();

  protected readonly scenario = linkedSignal(() => this.store.room()?.scenario ?? '');
  protected readonly scenarioDirty = computed(() => {
    const room = this.store.room();
    return !!room && this.scenario().trim() !== room.scenario.trim();
  });
  protected readonly savingScenario = signal(false);

  constructor() {
    effect(() => {
      const id = this.labId();
      untracked(() => this.store.init(id));
    });
    // Starts on the question open on the lab page
    let started = false;
    effect(() => {
      if (started || this.store.status() !== 'loaded') return;
      started = true;
      const open = untracked(() => this.lab?.selected()?.id);
      if (open && open !== untracked(() => this.store.selectedId())) untracked(() => this.store.select(open));
    });
  }

  protected async pickQuestion(select: HTMLSelectElement): Promise<void> {
    const id = select.value;
    if (id === this.store.selectedId()) return;
    if (this.store.dirty() && !(await this.discard(`Your edits to ${this.store.draft()?.question || 'this question'} are lost.`))) {
      select.value = this.store.selectedId() ?? '';
      return;
    }
    this.store.select(id);
  }

  protected async saveQuestion(): Promise<void> {
    try {
      const saved = await this.store.save();
      this.toast.success(`${saved.question} saved`);
      await this.lab?.reload();
    } catch (error) {
      this.toast.error(this.store.problem() ?? 'The question could not be saved.', error);
    }
  }

  protected async saveScenario(): Promise<void> {
    this.savingScenario.set(true);
    try {
      await this.store.saveRoom({ scenario: this.scenario().trim() });
      this.toast.success('Scenario saved');
      await this.lab?.reload();
    } catch (error) {
      this.toast.error('The scenario could not be saved.', error);
    } finally {
      this.savingScenario.set(false);
    }
  }

  canLeave(): boolean | Promise<boolean> {
    if (!this.store.dirty() && !this.scenarioDirty()) return true;
    return this.discard('Your edits on this page are not saved and are lost if you leave.');
  }

  private async discard(message: string): Promise<boolean> {
    const answer = await firstValueFrom(this.confirm.open({
      title: 'Discard unsaved changes?', message, confirmLabel: 'Discard changes', cancelLabel: 'Keep editing',
    }));
    return answer === 'confirm';
  }
}

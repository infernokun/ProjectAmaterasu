import { ChangeDetectionStrategy, Component, computed, effect, inject, input, linkedSignal, signal, untracked } from '@angular/core';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDragPlaceholder, CdkDropList } from '@angular/cdk/drag-drop';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { DifficultyLevel } from '../../../enums/difficulty-level.enum';
import { CrumbsService } from '../../../core/crumbs.service';
import { SessionStore } from '../../../core/stores/session.store';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { ToastService } from '../../../shared/services/toast.service';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { MarkdownComponent } from '../../../shared/ui/markdown.component';
import { LabBuilderPage } from '../labs/lab-builder.page';
import { EnvironmentView } from '../../play/data/play.models';
import { ScenarioCardComponent } from '../../play/labs/scenario-card.component';
import { AuthorChallenge, EnvironmentMode, LabFileView, Person, RoomChanges, TeamCredit } from './authoring.service';

type WizardStep = 'scenario' | 'environment' | 'questions' | 'teams' | 'publish';
import { NEW_ID, RoomEditorStore, scheduleLabel } from './room-editor.store';
import { DIFFICULTIES, wrapperParts } from './difficulties';
import { ChallengeFormComponent } from './challenge-form.component';
import { ScenarioFieldComponent } from './scenario-field.component';

export { DIFFICULTIES, wrapperParts } from './difficulties';

@Component({
  selector: 'app-room-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CdkDropList, CdkDrag, CdkDragHandle, CdkDragPlaceholder, MatMenuModule, IconComponent,
    EmptyStateComponent, SkeletonComponent, MarkdownComponent, LabBuilderPage, ScenarioCardComponent, ChallengeFormComponent,
    ScenarioFieldComponent],
  providers: [RoomEditorStore],
  templateUrl: './room-editor.page.html',
  styleUrl: './room-editor.page.scss',
  host: { class: 'content' },
})
export class RoomEditorPage {
  protected readonly store = inject(RoomEditorStore);
  protected readonly session = inject(SessionStore);
  private readonly crumbs = inject(CrumbsService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly roomId = input.required<string>();
  /** The wizard step, kept in the address. */
  readonly stepParam = input<string | undefined>(undefined, { alias: 'step' });
  /** 'lab' when opened from the lab page, which saving goes back to. */
  readonly from = input<string>();

  protected readonly steps: { key: WizardStep; label: string }[] = [
    { key: 'scenario', label: 'Scenario' },
    { key: 'environment', label: 'Environment' },
    { key: 'questions', label: 'Questions' },
    { key: 'teams', label: 'Teams and scoring' },
    { key: 'publish', label: 'Preview and publish' },
  ];
  protected readonly isNew = computed(() => this.roomId() === NEW_ID);
  protected readonly fromLab = computed(() => this.from() === 'lab' && !this.isNew());
  protected readonly step = computed<WizardStep>(() => {
    const wanted = this.steps.find(item => item.key === this.stepParam())?.key ?? 'scenario';
    return this.isNew() ? 'scenario' : wanted;
  });
  protected readonly stepIndex = computed(() => this.steps.findIndex(item => item.key === this.step()));
  /** Building a new environment inside the Environment step. */
  protected readonly building = signal(false);

  protected readonly newId = NEW_ID;
  protected readonly scheduled = scheduleLabel;
  protected readonly mode = signal<'edit' | 'preview'>('edit');

  // Lab settings, edited in place and saved with Save lab
  protected readonly roomName = linkedSignal(() => this.store.room()?.name ?? '');
  protected readonly roomTag = linkedSignal(() => this.store.room()?.surroundTag ?? 'CTF{x}');
  protected readonly scenario = linkedSignal(() => this.store.room()?.scenario ?? '');
  protected readonly envMode = linkedSignal<EnvironmentMode>(() => this.store.room()?.environmentMode ?? 'NONE');
  protected readonly envId = linkedSignal(() => this.store.room()?.environmentId ?? '');
  protected readonly teamCredit = linkedSignal<TeamCredit>(() => this.store.room()?.teamCredit ?? 'SEE_TEAMMATES');
  protected readonly roomDirty = computed(() => {
    const room = this.store.room();
    return !!room && (this.roomName().trim() !== room.name || this.roomTag().trim() !== room.surroundTag
      || this.scenario().trim() !== room.scenario.trim() || this.envMode() !== room.environmentMode
      || (this.envMode() !== 'NONE' && this.envId() !== (room.environmentId ?? ''))
      || this.teamCredit() !== room.teamCredit);
  });
  protected readonly credits: { value: TeamCredit; label: string; help: string }[] = [
    { value: 'SEE_TEAMMATES', label: 'See teammates', help: "Teammates see each other's solves; everyone answers for themselves." },
    { value: 'SHARED_SOLVES', label: 'Shared solves', help: "A teammate's solve counts for the whole team; the points go to whoever answered." },
  ];
  protected readonly creditHelp = computed(() => this.credits.find(credit => credit.value === this.teamCredit())?.help ?? '');
  protected readonly roomProblem = computed(() => {
    if (!this.roomName().trim()) return 'Give the lab a name.';
    if (!this.scenario().trim()) return 'Write the scenario players read first.';
    if (this.envMode() !== 'NONE' && !this.envId()) return 'Pick the environment players work in.';
    return null;
  });
  protected readonly envModes: { value: EnvironmentMode; label: string; help: string }[] = [
    { value: 'NONE', label: 'No environment', help: 'A plain CTF. Players see No setup needed.' },
    { value: 'SHARED', label: 'Shared', help: 'One running copy for everyone. Admins and the lab\'s facilitators start and stop it.' },
    { value: 'PER_TEAM', label: 'One per team', help: 'Each team gets its own copy, and players without a team share a Solo copy. Admins and the lab\'s facilitators start them.' },
  ];
  protected readonly envHelp = computed(() => this.envModes.find(mode => mode.value === this.envMode())?.help ?? '');
  protected readonly savingRoom = signal(false);
  protected readonly savingFacilitators = signal(false);

  protected readonly wrapper = computed(() => wrapperParts(this.store.room()?.surroundTag ?? 'CTF{x}'));
  protected readonly summary = computed(() => {
    const count = this.store.challenges().length;
    const players = this.store.players();
    return `${count} ${count === 1 ? 'question' : 'questions'} · ${this.store.totalPoints().toLocaleString('en-US')} pts available · `
      + `${players} ${players === 1 ? 'player' : 'players'} so far`;
  });

  protected readonly envName = computed(() => this.store.labs().find(lab => lab.id === this.store.room()?.environmentId)?.name ?? 'An environment');
  protected readonly teamLabel = computed(() =>
    this.store.room()?.teamCredit === 'SHARED_SOLVES' ? 'Shared team solves' : 'Teammates see solves');
  protected readonly previewGroups = computed(() => {
    const groups = new Map<string, AuthorChallenge[]>();
    for (const question of this.store.challenges()) {
      const name = question.category || 'General';
      groups.set(name, [...(groups.get(name) ?? []), question]);
    }
    return [...groups].map(([name, items]) => ({ name, items }));
  });
  /** What a lab needs before players see it. */
  protected readonly readiness = computed(() => {
    const room = this.store.room();
    const questions = this.store.challenges();
    const env = this.store.labs().find(lab => lab.id === room?.environmentId);
    const checks = [
      { label: 'It has a scenario', ok: !!room?.scenario.trim() },
      { label: questions.length ? `${questions.length} ${questions.length === 1 ? 'question' : 'questions'}` : 'It has at least one question', ok: questions.length > 0 },
      { label: 'Every question has a flag', ok: questions.length > 0 && questions.every(question => question.flags.some(flag => flag.flag.trim())) },
    ];
    if (room?.environmentMode !== 'NONE') {
      checks.push({ label: env ? `${env.name} is ${env.published ? 'published' : 'still a draft'}` : 'Its environment exists', ok: !!env?.published });
    }
    checks.push({ label: 'All changes are saved', ok: !this.roomDirty() && !this.store.dirty() });
    return checks;
  });
  protected readonly canPublish = computed(() => this.readiness().every(check => check.ok));
  /** An environment that is not running, so the preview reads like the lab page before a start. */
  protected readonly previewEnvironment = computed<EnvironmentView | null>(() => {
    const room = this.store.room();
    if (!room || room.environmentMode === 'NONE') return null;
    return { labId: room.environmentId ?? '', labName: this.envName(), labType: null, mode: room.environmentMode, status: 'NOT_RUNNING',
      trackerId: null, tracker: null, canControl: false, note: null, copiesRunning: 0 };
  });

  constructor() {
    effect(() => {
      const id = this.roomId();
      untracked(() => this.store.init(id));
    });
    effect(() => {
      const name = this.store.room()?.name;
      untracked(() => this.crumbs.set(this.fromLab()
        ? [{ label: 'Labs', link: '/labs' }, { label: name || 'Lab', link: ['/labs', this.roomId()] }, { label: 'Full editor' }]
        : [{ label: 'Build' }, { label: 'Labs', link: '/build/labs' }, { label: name || (this.isNew() ? 'New lab' : 'Lab') }]));
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected size(bytes: number): string {
    return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : bytes >= 1024 ? `${Math.round(bytes / 1024)} KB` : `${bytes} B`;
  }

  protected readonly uploading = signal(false);


  protected async uploadFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length) return;
    this.uploading.set(true);
    try {
      const saved = await this.store.uploadFiles(files);
      this.toast.success(`Uploaded ${saved.length} ${saved.length === 1 ? 'file' : 'files'}`);
    } catch (error) {
      this.toast.error('The upload did not finish.', error);
    } finally {
      this.uploading.set(false);
    }
  }

  protected async deleteFile(file: LabFileView): Promise<void> {
    const answer = await firstValueFrom(this.confirm.open({
      title: `Delete ${file.name}?`,
      message: 'Players can no longer download it. Questions that list it show only its name.',
      confirmLabel: 'Delete file',
    }));
    if (answer !== 'confirm') return;
    try {
      await this.store.deleteFile(file);
      this.toast.success(`${file.name} deleted`);
    } catch (error) {
      this.toast.error(`${file.name} could not be deleted.`, error);
    }
  }

  protected difficultyLabel(level: DifficultyLevel): string {
    return DIFFICULTIES.find(d => d.value === level)?.label ?? level;
  }

  /** Asks before throwing away unsaved edits; true when it is fine to move on. */
  private async leaveDraft(): Promise<boolean> {
    if (!this.store.dirty()) {
      return true;
    }
    const answer = await firstValueFrom(this.confirm.open({
      title: 'Discard unsaved changes?',
      message: `Your edits to ${this.store.draft()?.question || 'the new question'} are lost.`,
      confirmLabel: 'Discard changes',
      cancelLabel: 'Keep editing',
    }));
    return answer === 'confirm';
  }

  protected async pick(challenge: AuthorChallenge): Promise<void> {
    if (challenge.id === this.store.selectedId() || !(await this.leaveDraft())) {
      return;
    }
    this.store.select(challenge.id!);
    this.mode.set('edit');
  }

  protected async newChallenge(): Promise<void> {
    if (await this.leaveDraft()) {
      this.store.newChallenge(this.session.username() || null);
      this.mode.set('edit');
    }
  }

  protected async save(): Promise<void> {
    try {
      const saved = await this.store.save();
      this.toast.success('Saved. Players see the change on their next refresh.');
      if (this.fromLab()) this.backToLab(saved.id);
    } catch (error) {
      this.toast.error(this.store.problem() ?? 'The question could not be saved.', error);
    }
  }

  protected async deleteChallenge(): Promise<void> {
    const draft = this.store.draft();
    if (!draft) {
      return;
    }
    if (!draft.id) {
      this.store.revert();
      return;
    }
    const answer = await firstValueFrom(this.confirm.open({
      title: `Delete ${draft.question}?`,
      message: `Players lose it from the lab, along with their attempts and the hints they unlocked. `
        + 'Points already scored stay on the scoreboard.',
      confirmLabel: 'Delete question',
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await this.store.deleteSelected();
      this.toast.success(`${draft.question} deleted`);
    } catch (error) {
      this.toast.error(`${draft.question} could not be deleted.`, error);
    }
  }

  protected async dropped(event: CdkDragDrop<unknown>): Promise<void> {
    await this.reorder(event.previousIndex, event.currentIndex);
  }

  /** Arrow keys on a grip move the challenge one place, for people who do not drag. */
  protected async nudge(event: KeyboardEvent, index: number): Promise<void> {
    const step = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0;
    if (!step) {
      return;
    }
    event.preventDefault();
    const grip = event.target as HTMLElement;
    if (await this.reorder(index, index + step)) {
      // The list re-renders in the new order; keep focus on the challenge that moved
      setTimeout(() => (grip.closest('.drop-list')?.querySelectorAll<HTMLElement>('.grip')[index + step])?.focus());
    }
  }

  private async reorder(from: number, to: number): Promise<boolean> {
    try {
      return await this.store.move(from, to);
    } catch (error) {
      this.toast.error('The new order could not be saved. It is back to how it was.', error);
      return false;
    }
  }

  protected async addFacilitator(person: Person): Promise<void> {
    await this.saveFacilitators([...this.store.facilitators().map(p => p.id), person.id], `${person.username} facilitates this lab now`);
  }

  protected async removeFacilitator(person: Person): Promise<void> {
    await this.saveFacilitators(this.store.facilitators().filter(p => p.id !== person.id).map(p => p.id),
      `${person.username} no longer facilitates this lab`);
  }

  private async saveFacilitators(ids: string[], done: string): Promise<void> {
    this.savingFacilitators.set(true);
    try {
      await this.store.setFacilitators(ids);
      this.toast.success(done);
    } catch (error) {
      this.toast.error('The facilitators could not be saved.', error);
    } finally {
      this.savingFacilitators.set(false);
    }
  }

  private changes(): RoomChanges {
    const mode = this.envMode();
    return {
      name: this.roomName().trim(), surroundTag: this.roomTag().trim(), scenario: this.scenario().trim(),
      environmentMode: mode, ...(mode === 'NONE' ? {} : { environmentId: this.envId() }),
      teamCredit: this.teamCredit(),
    };
  }

  protected async saveRoom(quiet = false): Promise<boolean> {
    this.savingRoom.set(true);
    try {
      await this.store.saveRoom(this.changes());
      if (!quiet) this.toast.success('Lab saved');
      if (!quiet && this.fromLab()) this.backToLab();
      return true;
    } catch (error) {
      this.toast.error('The lab could not be saved.', error);
      return false;
    } finally {
      this.savingRoom.set(false);
    }
  }

  /** Moves to a step, saving the lab's settings first. */
  protected async go(step: WizardStep): Promise<void> {
    if (this.roomDirty() && !this.roomProblem() && !(await this.saveRoom(true))) return;
    if (this.store.dirty() && !(await this.leaveDraft())) return;
    this.building.set(false);
    this.router.navigate([], { queryParams: { step }, queryParamsHandling: 'merge', replaceUrl: true });
  }

  /** Opened from the lab page: back to it, on the question just saved. */
  private backToLab(challenge?: string): void {
    this.router.navigate(['/labs', this.roomId()], { queryParams: challenge ? { challenge } : {} });
  }

  protected stepDone(step: WizardStep): boolean {
    const room = this.store.room();
    if (!room || this.isNew()) return false;
    switch (step) {
      case 'scenario': return !!room.scenario.trim();
      case 'environment': return room.environmentMode === 'NONE' || !!room.environmentId;
      case 'questions': return this.store.challenges().length > 0;
      case 'teams': return true;
      case 'publish': return room.published;
    }
  }

  /** The scenario step of a new lab saves it as a draft and moves on. */
  protected async createDraft(): Promise<void> {
    this.savingRoom.set(true);
    try {
      const room = await this.store.createRoom(this.changes());
      this.toast.success(`${room.name} saved as a draft`);
      this.router.navigate(['/build/labs', room.id], { queryParams: { step: 'environment' }, replaceUrl: true });
    } catch (error) {
      this.toast.error('The lab could not be created.', error);
    } finally {
      this.savingRoom.set(false);
    }
  }

  protected async environmentBuilt(lab: { id?: string; name?: string }): Promise<void> {
    await this.store.reloadLabs();
    if (lab.id) this.envId.set(lab.id);
    this.building.set(false);
    this.toast.info(`${lab.name ?? 'The environment'} is picked for this lab. Save to keep it.`);
  }

  protected async setPublished(published: boolean): Promise<void> {
    const name = this.store.room()?.name ?? 'This lab';
    this.savingRoom.set(true);
    try {
      await this.store.saveRoom({ published });
      this.toast.success(published ? `${name} is published. Players see it now.` : `${name} is a draft again.`);
    } catch (error) {
      this.toast.error(published ? 'The lab could not be published.' : 'The lab could not go back to draft.', error);
    } finally {
      this.savingRoom.set(false);
    }
  }

  protected async deleteRoom(): Promise<void> {
    const room = this.store.room();
    if (!room) {
      return;
    }
    const count = this.store.challenges().length;
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
      await this.store.deleteRoom();
      this.toast.success(`${room.name} deleted`);
      this.router.navigate(['/build/labs']);
    } catch (error) {
      this.toast.error(`${room.name} could not be deleted.`, error);
    }
  }

  protected async importFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    try {
      const count = await this.store.importFile(await file.text());
      this.toast.success(`Imported ${count} ${count === 1 ? 'question' : 'questions'} into ${this.store.room()?.name}`);
    } catch (error) {
      this.toast.error(error instanceof Error ? error.message : 'The file could not be imported.', error);
    }
  }

  protected exportFile(): void {
    const room = this.store.room();
    const blob = new Blob([this.store.exportJson()], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${(room?.name ?? 'lab').toLowerCase().replace(/[^a-z0-9]+/g, '-')}-questions.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}

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
import { AuthorChallenge, Person } from './authoring.service';
import { NEW_ID, RoomEditorStore, scheduleLabel } from './room-editor.store';

export const DIFFICULTIES: { value: DifficultyLevel; label: string }[] = [
  { value: DifficultyLevel.BEGINNER, label: 'Beginner' },
  { value: DifficultyLevel.EASY, label: 'Easy' },
  { value: DifficultyLevel.MEDIUM, label: 'Medium' },
  { value: DifficultyLevel.HARD, label: 'Hard' },
  { value: DifficultyLevel.EXPERT, label: 'Expert' },
  { value: DifficultyLevel.IMPOSSIBLE, label: 'Impossible' },
];

/** "CTF{x}" to its prefix and suffix around the placeholder. */
export function wrapperParts(tag: string): { prefix: string; suffix: string; name: string } {
  const match = /^(.*?)(?:x|\?)(\W*)$/.exec(tag);
  const prefix = match ? match[1] : `${tag}{`;
  const suffix = match ? match[2] : '}';
  return { prefix, suffix, name: prefix.replace(/\W+$/, '') || tag };
}

@Component({
  selector: 'app-room-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CdkDropList, CdkDrag, CdkDragHandle, CdkDragPlaceholder, MatMenuModule, IconComponent,
    EmptyStateComponent, SkeletonComponent],
  providers: [RoomEditorStore],
  templateUrl: './room-editor.page.html',
  styleUrl: './room-editor.page.scss',
  host: { class: 'content' },
})
export class RoomEditorPage {
  protected readonly store = inject(RoomEditorStore);
  private readonly session = inject(SessionStore);
  private readonly crumbs = inject(CrumbsService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly roomId = input.required<string>();

  protected readonly difficulties = DIFFICULTIES;
  protected readonly newId = NEW_ID;
  protected readonly scheduled = scheduleLabel;
  protected readonly mode = signal<'edit' | 'preview'>('edit');

  // Room settings, edited in place and saved with Save room
  protected readonly roomName = linkedSignal(() => this.store.room()?.name ?? '');
  protected readonly roomTag = linkedSignal(() => this.store.room()?.surroundTag ?? 'CTF{x}');
  protected readonly roomDirty = computed(() => !!this.store.room()
    && (this.roomName().trim() !== this.store.room()!.name || this.roomTag().trim() !== this.store.room()!.surroundTag));
  protected readonly savingRoom = signal(false);
  protected readonly savingFacilitators = signal(false);

  protected readonly wrapper = computed(() => wrapperParts(this.store.room()?.surroundTag ?? 'CTF{x}'));
  protected readonly summary = computed(() => {
    const count = this.store.challenges().length;
    const players = this.store.players();
    return `${count} ${count === 1 ? 'challenge' : 'challenges'} · ${this.store.totalPoints().toLocaleString('en-US')} pts available · `
      + `${players} ${players === 1 ? 'player' : 'players'} joined`;
  });
  protected readonly visibility = computed(() => {
    const draft = this.store.draft();
    if (!draft) {
      return '';
    }
    if (!draft.visible) {
      return 'Hidden. Only authors can see it.';
    }
    const opens = draft.opens ? new Date(draft.opens) : null;
    if (opens && opens.getTime() > Date.now()) {
      return `Visible from ${opens.toLocaleString('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}`;
    }
    return 'Visible now';
  });

  constructor() {
    effect(() => {
      const id = this.roomId();
      untracked(() => this.store.init(id));
    });
    effect(() => {
      const name = this.store.room()?.name;
      untracked(() => this.crumbs.set([
        { label: 'Build' }, { label: 'Rooms and challenges', link: '/build/rooms' }, { label: name ?? 'Room' },
      ]));
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected number(event: Event): number {
    return Number((event.target as HTMLInputElement).value);
  }

  protected checked(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
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
      message: `Your edits to ${this.store.draft()?.question || 'the new challenge'} are lost.`,
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
      await this.store.save();
      this.toast.success('Saved. Players see the change on their next refresh.');
    } catch (error) {
      this.toast.error(this.store.problem() ?? 'The challenge could not be saved.', error);
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
      message: `Players lose it from the room, along with their attempts and the hints they unlocked. `
        + 'Points already scored stay on the scoreboard.',
      confirmLabel: 'Delete challenge',
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

  protected hasLab(labId: string): boolean {
    return this.store.labs().some(lab => lab.id === labId);
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
    await this.saveFacilitators([...this.store.facilitators().map(p => p.id), person.id], `${person.username} facilitates this room now`);
  }

  protected async removeFacilitator(person: Person): Promise<void> {
    await this.saveFacilitators(this.store.facilitators().filter(p => p.id !== person.id).map(p => p.id),
      `${person.username} no longer facilitates this room`);
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

  protected async saveRoom(): Promise<void> {
    this.savingRoom.set(true);
    try {
      await this.store.saveRoom(this.roomName().trim(), this.roomTag().trim());
      this.toast.success('Room saved');
    } catch (error) {
      this.toast.error('The room could not be saved.', error);
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
      message: `Its ${count} ${count === 1 ? 'challenge' : 'challenges'}, every answer and hint players used, and the list of who joined are deleted. `
        + 'Points scored in this room are lost.',
      confirmLabel: 'Delete room',
      typeToConfirm: room.name,
    }));
    if (answer !== 'confirm') {
      return;
    }
    try {
      await this.store.deleteRoom();
      this.toast.success(`${room.name} deleted`);
      this.router.navigate(['/build/rooms']);
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
      this.toast.success(`Imported ${count} ${count === 1 ? 'challenge' : 'challenges'} into ${this.store.room()?.name}`);
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
    link.download = `${(room?.name ?? 'room').toLowerCase().replace(/[^a-z0-9]+/g, '-')}-challenges.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}

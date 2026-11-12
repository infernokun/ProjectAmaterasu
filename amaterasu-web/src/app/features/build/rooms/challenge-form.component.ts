import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { IconComponent } from '../../../shared/ui/icon.component';
import { DIFFICULTIES, wrapperParts } from './difficulties';
import { RoomEditorStore } from './room-editor.store';

/**
 * The fields of the question being edited in a RoomEditorStore: the full editor's form, and with
 * `quick` the lab page's quick edit (no schedule, files or solution notes).
 */
@Component({
  selector: 'app-challenge-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './challenge-form.component.html',
  styles: `
    :host { display: block; }
    .wide { grid-column: 1 / -1; }
    .toggle-row { min-height: 40px; }
    .seg { flex-wrap: wrap; }
    .ed-row { display: grid; grid-template-columns: minmax(0, 1fr) 90px 120px 40px; gap: 10px; align-items: center; }
    .fl-row { display: grid; grid-template-columns: minmax(0, 1fr) 104px 96px 80px 40px; gap: 12px; align-items: center; }
    .fl-row > :nth-child(2), .fl-row > :nth-child(3) { justify-self: center; }
    /* A narrow pane or phone: the small columns shrink to fit */
    @container (max-width: 520px) {
      .fl-row { grid-template-columns: minmax(0, 1fr) 44px 44px 64px 40px; gap: 8px; }
      .ed-row { grid-template-columns: minmax(0, 1fr) 64px 72px 40px; gap: 8px; }
    }
    .card-body { container-type: inline-size; }
  `,
})
export class ChallengeFormComponent {
  protected readonly store = inject(RoomEditorStore);

  /** Leaves out the schedule, files and solution notes. */
  readonly quick = input(false);

  protected readonly difficulties = DIFFICULTIES;
  protected readonly wrapper = computed(() => wrapperParts(this.store.room()?.surroundTag ?? 'CTF{x}'));
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
  /** Names the question lists that the lab has no file for. */
  protected readonly missingAttachments = computed(() => {
    const names = new Set(this.store.files().map(file => file.name));
    return (this.store.draft()?.attachments ?? []).filter(name => !names.has(name));
  });

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected number(event: Event): number {
    return Number((event.target as HTMLInputElement).value);
  }

  /** An empty attempts box means unlimited. */
  protected attempts(event: Event): number | null {
    const raw = (event.target as HTMLInputElement).value.trim();
    return raw === '' ? null : Number(raw);
  }

  protected checked(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }
}

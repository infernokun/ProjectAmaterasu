import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { IconComponent } from './icon.component';
import { IconName } from './icons';

export interface ConfirmOptions {
  /** A question naming the thing, e.g. "Stop Web Exploitation 101?" */
  title: string;
  /** What is lost, in plain words. */
  message: string;
  /** The action, e.g. "Stop and destroy". */
  confirmLabel: string;
  /** danger (default) for destructive actions, primary otherwise. */
  tone?: 'danger' | 'primary';
  /** A safer choice shown next to the action, e.g. "Pause instead". */
  alternativeLabel?: string;
  cancelLabel?: string;
  /** When set, the action stays disabled until this exact text is typed. */
  typeToConfirm?: string;
  icon?: IconName;
}

export type ConfirmResult = 'confirm' | 'alternative' | 'cancel';

@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="modal" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-message">
      <div class="row head">
        @if (icon()) {
          <div class="feed-ico" [class.s-bad]="danger()"><app-icon [name]="icon()!" /></div>
        }
        <div class="stack tight grow">
          <h2 class="h2" id="confirm-title">{{ data.title }}</h2>
          <p class="dim" id="confirm-message">{{ data.message }}</p>
        </div>
      </div>
      @if (data.typeToConfirm) {
        <label class="field">
          <span class="label">Type {{ data.typeToConfirm }} to confirm</span>
          <input class="input" [placeholder]="data.typeToConfirm" autocomplete="off" spellcheck="false"
                 [value]="typed()" (input)="typed.set($any($event.target).value)" (keydown.enter)="confirmIfReady()">
        </label>
      }
      <div class="row end">
        <button type="button" class="btn ghost" (click)="close('cancel')">{{ data.cancelLabel || 'Cancel' }}</button>
        @if (data.alternativeLabel) {
          <button type="button" class="btn" (click)="close('alternative')">{{ data.alternativeLabel }}</button>
        }
        <button type="button" class="btn" [class.danger]="danger()" [class.solid]="danger()" [class.primary]="!danger()"
                [disabled]="!ready()" (click)="close('confirm')">{{ data.confirmLabel }}</button>
      </div>
    </div>
  `,
  styles: `
    .modal { max-width: none; border: 0; box-shadow: none; }
    .head { gap: 12px; flex-wrap: nowrap; align-items: flex-start; }
    p { margin: 0; }
  `,
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmOptions>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ConfirmDialogComponent, ConfirmResult>>(MatDialogRef);

  protected readonly danger = computed(() => (this.data.tone ?? 'danger') === 'danger');
  protected readonly icon = computed(() => this.data.icon ?? (this.danger() ? 'alert' : undefined));
  protected readonly typed = signal('');
  protected readonly ready = computed(() => !this.data.typeToConfirm || this.typed().trim() === this.data.typeToConfirm);

  protected confirmIfReady(): void {
    if (this.ready()) {
      this.close('confirm');
    }
  }

  protected close(result: ConfirmResult): void {
    this.ref.close(result);
  }
}

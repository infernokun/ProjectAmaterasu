import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CLASSIFICATIONS, Classification } from '../../core/classification';
import { AppInfoStore } from '../../core/stores/app-info.store';
import { SessionStore } from '../../core/stores/session.store';
import { ToastService } from '../../shared/services/toast.service';
import { IconComponent } from '../../shared/ui/icon.component';

/** The classification banner: admins pick the marking for everyone; everyone else sees what is set. */
@Component({
  selector: 'app-classification-setting',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="field">
      <span class="label" id="class-label">Classification banner</span>
      @if (session.isAdmin()) {
        <div class="options" role="radiogroup" aria-labelledby="class-label">
          @for (option of options; track option.value) {
            <button type="button" role="radio" class="option" [class.on]="current().value === option.value"
                    [attr.aria-checked]="current().value === option.value" [disabled]="saving()" (click)="pick(option.value)">
              @if (option.value === 'NONE') {
                <span class="swatch none" aria-hidden="true"></span>
              } @else {
                <span class="swatch" aria-hidden="true" [style.background]="option.background" [style.color]="option.color">Aa</span>
              }
              {{ option.label }}
              @if (saving() === option.value) { <app-icon name="spinner" size="sm" [spin]="true" /> }
            </button>
          }
        </div>
        <span class="help">Shown above and below every page, for everyone signed in or not. Only admins can change it.</span>
      } @else {
        <div class="row">
          <span class="tag">Set by your admin: {{ current().value === 'NONE' ? 'no banner' : current().label }}</span>
        </div>
      }
    </div>
  `,
  styles: `
    .options { display: flex; flex-wrap: wrap; gap: 8px; }
    .option { display: inline-flex; align-items: center; gap: 8px; min-height: var(--seg-h); padding: 0 12px 0 6px;
      border-radius: var(--radius); border: 1px solid var(--border-2); background: var(--surface-2); color: var(--text-2);
      font: inherit; font-size: 13px; font-weight: 500; cursor: pointer; }
    .option:hover { color: var(--text); }
    .option.on { border-color: var(--accent); color: var(--text); box-shadow: inset 0 0 0 1px var(--accent); }
    .swatch { display: inline-grid; place-items: center; width: 28px; height: 20px; border-radius: 4px; font-size: 10px; font-weight: 700; }
    .swatch.none { border: 1px dashed var(--border-2); }
  `,
})
export class ClassificationSettingComponent {
  protected readonly session = inject(SessionStore);
  private readonly info = inject(AppInfoStore);
  private readonly toast = inject(ToastService);

  protected readonly options = CLASSIFICATIONS;
  protected readonly current = this.info.classification;
  protected readonly saving = signal<Classification | null>(null);

  protected async pick(value: Classification): Promise<void> {
    if (value === this.current().value || this.saving()) {
      return;
    }
    this.saving.set(value);
    try {
      await this.info.setClassification(value);
      this.toast.success(value === 'NONE' ? 'The classification banner is off' : `The banner now reads ${this.current().label}`);
    } catch (error) {
      this.toast.error('The classification banner could not be changed.', error);
    } finally {
      this.saving.set(null);
    }
  }
}

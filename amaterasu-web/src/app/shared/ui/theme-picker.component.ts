import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ThemePreference, ThemeStore } from '../../core/theme/theme.store';

/** The theme setting: match the system, light or dark. */
@Component({
  selector: 'app-theme-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field">
      <span class="label" id="theme-label">Theme</span>
      <div class="seg" role="radiogroup" aria-labelledby="theme-label">
        @for (option of options; track option.value) {
          <button type="button" role="radio" [class.on]="theme.preference() === option.value"
                  [attr.aria-checked]="theme.preference() === option.value" (click)="pick(option.value)">
            {{ option.label }}
          </button>
        }
      </div>
      <span class="help">Saved to this browser</span>
    </div>
  `,
  styles: `.seg { align-self: flex-start; }`,
})
export class ThemePickerComponent {
  protected readonly theme = inject(ThemeStore);
  protected readonly options: { value: ThemePreference; label: string }[] = [
    { value: 'system', label: 'Match system' },
    { value: 'light', label: 'Light' },
    { value: 'dark', label: 'Dark' },
  ];

  protected pick(preference: ThemePreference): void {
    this.theme.setPreference(preference);
  }
}

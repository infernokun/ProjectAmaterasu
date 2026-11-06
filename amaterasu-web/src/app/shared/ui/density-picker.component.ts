import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Density, DensityStore } from '../../core/theme/density.store';

/** The density setting: comfortable or compact spacing across the app. */
@Component({
  selector: 'app-density-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field">
      <span class="label" id="density-label">Density</span>
      <div class="seg" role="radiogroup" aria-labelledby="density-label">
        @for (option of options; track option.value) {
          <button type="button" role="radio" [class.on]="store.density() === option.value"
                  [attr.aria-checked]="store.density() === option.value" (click)="store.setDensity(option.value)">
            {{ option.label }}
          </button>
        }
      </div>
      <span class="help">Compact fits more rows on screen. Saved to this browser</span>
    </div>
  `,
  styles: `.seg { align-self: flex-start; }`,
})
export class DensityPickerComponent {
  protected readonly store = inject(DensityStore);
  protected readonly options: { value: Density; label: string }[] = [
    { value: 'comfortable', label: 'Comfortable' },
    { value: 'compact', label: 'Compact' },
  ];
}

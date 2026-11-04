import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { ThemePickerComponent } from '../../shared/ui/theme-picker.component';

/** Until the Account screen lands: the header and the theme setting, so the theme can be changed. */
@Component({
  selector: 'app-account-placeholder',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, ThemePickerComponent],
  template: `
    <app-page-header title="Account" subtitle="Profile, password, sessions and appearance." />
    <section class="card stack" aria-labelledby="ap-h">
      <h2 class="h2" id="ap-h">Appearance</h2>
      <app-theme-picker />
    </section>
  `,
  host: { class: 'content' },
})
export class AccountPlaceholderComponent {}

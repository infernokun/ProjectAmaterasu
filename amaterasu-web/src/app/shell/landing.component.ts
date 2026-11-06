import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AppInfoStore } from '../core/stores/app-info.store';
import { IconComponent } from '../shared/ui/icon.component';
import { AuthDialogsService } from './auth-dialogs.service';

/** What signed-out visitors see: the app's name and a way in. */
@Component({
  selector: 'app-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <main class="landing">
      <section class="card hero" aria-labelledby="landing-title">
        <div class="brand">
          <div class="brand-mark"><app-icon name="brand" /></div>
          <div>
            <div class="brand-name">Amaterasu</div>
            <div class="brand-sub">Labs and CTF</div>
          </div>
        </div>
        <div class="stack tight">
          <h1 class="h1" id="landing-title">{{ info.name() }}</h1>
          <p class="sub">{{ info.description() }}</p>
        </div>
        <ul class="points">
          <li><app-icon name="flask" size="sm" />Start your team's labs and pick up where you left off</li>
          <li><app-icon name="flag" size="sm" />Solve challenges and submit flags in rooms</li>
          <li><app-icon name="trophy" size="sm" />Follow the scoreboard as it moves</li>
        </ul>
        <div class="row actions">
          <button type="button" class="btn primary" (click)="dialogs.signIn()">Sign in</button>
          <button type="button" class="btn" (click)="dialogs.register()">Create an account</button>
        </div>
      </section>
    </main>
  `,
  styles: `
    :host { display: block; flex: 1 1 auto; overflow: auto; }
    .landing { min-height: 100%; display: grid; place-items: center; padding: 32px 16px; }
    .hero { width: 100%; max-width: 480px; display: flex; flex-direction: column; gap: 22px; padding: 28px; }
    .brand { padding: 0; }
    .sub { margin-top: 4px; }
    .points { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; color: var(--text-2); }
    .points li { display: flex; align-items: center; gap: 10px; }
    .points app-icon { color: var(--accent); }
    .actions .btn { flex: 1 1 160px; }
  `,
})
export class LandingComponent {
  protected readonly info = inject(AppInfoStore);
  protected readonly dialogs = inject(AuthDialogsService);
}

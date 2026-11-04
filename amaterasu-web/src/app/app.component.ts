import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { ThemeStore } from './core/theme/theme.store';
import { IconComponent } from './shared/ui/icon.component';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, IconComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  /** The classification banner shown above and below every page. */
  readonly classification = 'UNCLASSIFIED';

  /** False until the first page is ready, while settings and the session load. */
  protected readonly ready = toSignal(
    inject(Router).events.pipe(filter(event => event instanceof NavigationEnd), map(() => true)),
    { initialValue: false },
  );

  constructor() {
    inject(ThemeStore);
  }
}

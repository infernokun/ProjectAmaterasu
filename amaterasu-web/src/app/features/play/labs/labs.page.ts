import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { SessionStore } from '../../../core/stores/session.store';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { LiveNoteComponent } from './live-note.component';
import { TeamLabCardComponent } from './team-lab-card.component';

/** The team's labs; each opens its console. */
@Component({
  selector: 'app-labs-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, IconComponent, EmptyStateComponent, SkeletonComponent, TeamLabCardComponent, LiveNoteComponent],
  template: `
    <app-page-header title="Labs" [eyebrow]="session.team()?.name ?? undefined"
      subtitle="Your team's labs. Open one for its services, logs, files and activity.">
      <a class="btn primary" routerLink="/home" fragment="catalog"><app-icon name="plus" />Start a lab</a>
    </app-page-header>
    <div class="row between">
      <span class="small muted">{{ trackers.runningCount() }} of {{ trackers.teamLabs().length }} running</span>
      <app-live-note [updatedAt]="trackers.updatedAt()" />
    </div>
    @if (trackers.loading() && !trackers.teamLabs().length) {
      <app-skeleton variant="card" [count]="2" label="Loading team labs" />
    } @else {
      <div class="stack loose">
        @for (tracker of trackers.teamLabs(); track tracker.id) {
          <app-team-lab-card [tracker]="tracker" />
        } @empty {
          <section class="card">
            <app-empty-state icon="flask" title="No labs deployed yet" message="Start one from Available labs on Home.">
              <a class="btn sm" routerLink="/home" fragment="catalog">Go to Available labs</a>
            </app-empty-state>
          </section>
        }
      </div>
    }
  `,
  host: { class: 'content' },
})
export class LabsPage {
  protected readonly trackers = inject(LabTrackersStore);
  protected readonly session = inject(SessionStore);
}

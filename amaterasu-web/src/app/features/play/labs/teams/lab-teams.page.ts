import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';

/** Placeholder: the lab's teams. Replaced by the teams-per-lab work. */
@Component({
  selector: 'app-lab-teams-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `<section class="card"><app-empty-state icon="users" title="Teams" message="Teams for this lab show here." /></section>`,
})
export class LabTeamsPage {
  readonly labId = input.required<string>();
}

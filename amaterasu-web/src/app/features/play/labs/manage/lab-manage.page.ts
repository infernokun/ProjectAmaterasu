import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state.component';

/** Placeholder: running the lab. Replaced by the lab management work. */
@Component({
  selector: 'app-lab-manage-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `<section class="card"><app-empty-state icon="layers" title="Manage" message="Tools for running this lab show here." /></section>`,
})
export class LabManagePage {
  readonly labId = input.required<string>();
}

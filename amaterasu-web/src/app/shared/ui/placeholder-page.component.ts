import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { PageData } from '../../core/page';
import { EmptyStateComponent } from './empty-state.component';
import { PageHeaderComponent } from './page-header.component';

/** Stands in for a screen that is not built yet, with that screen's header. */
@Component({
  selector: 'app-placeholder-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, EmptyStateComponent],
  template: `
    <app-page-header [title]="page().title" [eyebrow]="page().section" [subtitle]="page().description" />
    <section class="card">
      <app-empty-state icon="clock" title="This screen is on its way" message="It is being rebuilt. The rest of the app works as before." />
    </section>
  `,
  host: { class: 'content' },
})
export class PlaceholderPageComponent {
  private readonly data = toSignal(inject(ActivatedRoute).data);
  protected readonly page = computed(() => (this.data() ?? { title: '' }) as PageData);
}

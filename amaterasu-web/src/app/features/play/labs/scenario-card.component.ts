import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { EnvironmentView } from '../data/play.models';
import { LabMarkdownComponent } from './lab-markdown.component';

/** The lab's scenario, the first thing players read. */
@Component({
  selector: 'app-scenario-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LabMarkdownComponent],
  template: `
    <section class="card scenario" aria-label="Scenario">
      <app-lab-markdown [source]="scenario()" [environment]="environment()" [canSeeLogin]="canSeeLogin()" />
    </section>
  `,
  styles: `
    :host { display: block; }
    .scenario { font-size: 15px; max-width: 100%; }
  `,
})
export class ScenarioCardComponent {
  readonly scenario = input<string | null>('');
  readonly environment = input<EnvironmentView | null>(null);
  readonly canSeeLogin = input(false);
}

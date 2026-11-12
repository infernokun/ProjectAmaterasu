import { ChangeDetectionStrategy, Component, model } from '@angular/core';
import { MarkdownComponent } from '../../../shared/ui/markdown.component';

let nextId = 0;

/** A lab's scenario: the Markdown source beside how players read it. */
@Component({
  selector: 'app-scenario-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MarkdownComponent],
  template: `
    <div class="field"><span class="label" [id]="labelId">Scenario</span>
      <div class="scenario-edit">
        <textarea class="input mono" rows="14" [attr.aria-labelledby]="labelId" [attr.aria-describedby]="helpId" [value]="value()"
                  (input)="value.set($any($event.target).value)"></textarea>
        <div class="card scenario-preview" aria-label="Scenario preview"><app-markdown [source]="value()" /></div>
      </div>
      <span class="help" [id]="helpId">{{ help }}</span>
    </div>
  `,
  styles: `
    :host { display: block; }
    .scenario-edit { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 12px; align-items: stretch; }
    .scenario-edit textarea { min-height: 280px; resize: vertical; font-size: 13px; }
    .scenario-preview { max-height: 420px; overflow: auto; font-size: 14px; }
  `,
})
export class ScenarioFieldComponent {
  readonly value = model('');

  protected readonly labelId = `scen-label-${++nextId}`;
  protected readonly helpId = `scen-help-${nextId}`;
  protected readonly help = 'Markdown. {{address}}, {{username}}, {{subnet}} and {{password}} fill in from the running environment; the password shows as a masked chip.';
}

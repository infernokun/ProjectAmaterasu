import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CodeModel, MonacoEditorComponent } from '../monaco-editor/monaco-editor.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { ThemeStore } from '../../../core/theme/theme.store';

@Component({
  selector: 'amaterasu-code-block',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './code-block.component.html',
  styleUrls: ['./code-block.component.scss'],
  imports: [IconComponent, MonacoEditorComponent],
})
export class CodeBlockComponent {
  private readonly themeStore = inject(ThemeStore);

  readonly placeholder = input('');
  readonly readonly = input(false);
  readonly fileType = input('json');
  readonly value = input('');

  readonly codeModel = computed<CodeModel>(() => ({
    language: this.fileType(),
    uri: `main.${this.fileType()}`,
    value: this.value(),
  }));
  readonly theme = computed(() => (this.themeStore.theme() === 'dark' ? 'vs-dark' : 'vs'));
  readonly copied = signal(false);
  readonly options = { contextmenu: true, minimap: { enabled: true } };

  private current = '';

  onCodeChanged(value: string) {
    this.current = value;
  }

  copyToClipboard() {
    navigator.clipboard.writeText(this.current || this.value()).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }
}

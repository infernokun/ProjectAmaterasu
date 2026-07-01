import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';

/**
 * Shape of a Monaco editor model. Mirrors the small subset of the old
 * `@ngstack/code-editor` `CodeModel` that the app actually used, so the
 * migration to a direct Monaco wrapper is a drop-in for callers.
 */
export interface CodeModel {
  language: string;
  uri: string;
  value: string;
}

// Monaco is loaded lazily from the copied assets via its AMD loader instead of
// being bundled. `main.ts` already configures `MonacoEnvironment.getWorkerUrl`.
const MONACO_BASE_URL = '/assets/monaco-editor/min';
let monacoLoader: Promise<any> | null = null;

function loadMonaco(): Promise<any> {
  const w = window as any;
  if (w.monaco) {
    return Promise.resolve(w.monaco);
  }
  if (monacoLoader) {
    return monacoLoader;
  }

  monacoLoader = new Promise((resolve, reject) => {
    const onAmdLoaderReady = () => {
      w.require.config({ paths: { vs: `${MONACO_BASE_URL}/vs` } });
      w.require(['vs/editor/editor.main'], () => resolve(w.monaco));
    };

    if (w.require) {
      onAmdLoaderReady();
      return;
    }

    const loaderScript = document.createElement('script');
    loaderScript.type = 'text/javascript';
    loaderScript.src = `${MONACO_BASE_URL}/vs/loader.js`;
    loaderScript.onload = onAmdLoaderReady;
    loaderScript.onerror = reject;
    document.body.appendChild(loaderScript);
  });

  return monacoLoader;
}

@Component({
  selector: 'amaterasu-monaco-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #container class="monaco-editor-host"></div>`,
  styles: [
    `:host { display: block; width: 100%; height: 100%; }
     .monaco-editor-host { width: 100%; height: 100%; min-height: 300px; }`,
  ],
})
export class MonacoEditorComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('container', { static: true })
  private container!: ElementRef<HTMLElement>;

  @Input() codeModel?: CodeModel;
  @Input() theme = 'vs-dark';
  @Input() readOnly = false;
  @Input() options: Record<string, unknown> = {};

  @Output() valueChanged = new EventEmitter<string>();

  private readonly zone = inject(NgZone);
  private monaco: any;
  private editor: any;

  ngAfterViewInit(): void {
    loadMonaco().then((monaco) => {
      this.monaco = monaco;
      // Keep Monaco's own rendering/scroll churn out of Angular's zone.
      this.zone.runOutsideAngular(() => this.createEditor());
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.editor || !this.monaco) {
      return;
    }
    if (changes['codeModel'] && this.codeModel) {
      if (this.codeModel.value !== this.editor.getValue()) {
        this.editor.setValue(this.codeModel.value);
      }
      const model = this.editor.getModel();
      if (model) {
        this.monaco.editor.setModelLanguage(model, this.codeModel.language);
      }
    }
    if (changes['readOnly']) {
      this.editor.updateOptions({ readOnly: this.readOnly });
    }
    if (changes['theme']) {
      this.monaco.editor.setTheme(this.theme);
    }
  }

  ngOnDestroy(): void {
    this.editor?.dispose();
  }

  private createEditor(): void {
    this.editor = this.monaco.editor.create(this.container.nativeElement, {
      value: this.codeModel?.value ?? '',
      language: this.codeModel?.language ?? 'plaintext',
      theme: this.theme,
      readOnly: this.readOnly,
      automaticLayout: true,
      ...this.options,
    });

    this.editor.onDidChangeModelContent(() => {
      const value = this.editor.getValue();
      this.zone.run(() => this.valueChanged.emit(value));
    });
  }
}

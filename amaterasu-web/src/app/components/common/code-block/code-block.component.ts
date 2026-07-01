import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  ChangeDetectorRef,
  signal,
} from '@angular/core';
import { CodeModel, MonacoEditorComponent } from '../monaco-editor/monaco-editor.component';
import { NgIf } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'amaterasu-code-block',
    templateUrl: './code-block.component.html',
    styleUrls: ['./code-block.component.scss'],
    imports: [
        NgIf,
        MatButton,
        MatIcon,
        MonacoEditorComponent,
    ],
})
export class CodeBlockComponent implements OnInit {
  @Input() id: string = '';
  @Input() placeholder: string = '';
  @Input() readonly: boolean = false;
  @Input() versions: number[] = [];
  @Input() fileType: string = '';
  @Input() codeModel: CodeModel | undefined = {
    language: 'json',
    uri: 'main.json',
    value: '',
  };

  @Output() onVersionChange = new EventEmitter<number>();
  @Output() onChange = new EventEmitter<string>();

  isCopied = signal(false);
  theme = 'vs-dark';

  options = {
    contextmenu: true,
    minimap: {
      enabled: true,
    },
  };

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnChanges() {
    if (this.codeModel && this.fileType) {
      this.codeModel.language = this.fileType;
    }
    
    this.cdr.detectChanges();
  }

  ngOnInit() {
    if (this.codeModel) {
      this.codeModel.language = this.fileType;
    }
  }

  onCodeChanged(value: any) {}

  copyToClipboard() {
    navigator.clipboard.writeText(this.codeModel!.value).then(() => {
      this.isCopied.set(true);
      // Hide the check mark after 2 seconds
      setTimeout(() => {
        this.isCopied.set(false);
      }, 2000);
    });
  }
}

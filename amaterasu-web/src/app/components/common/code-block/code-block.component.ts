import { Component, Input, Output, EventEmitter, OnInit, ChangeDetectorRef, ViewChild } from '@angular/core';
import { CodeEditorComponent, CodeModel } from '@ngstack/code-editor';


@Component({
    selector: 'app-code-block',
    templateUrl: './code-block.component.html',
    styleUrls: ['./code-block.component.scss'],
    standalone: false
})
export class CodeBlockComponent implements OnInit {
  @ViewChild(CodeEditorComponent, { static: false }) _codeEditor: CodeEditorComponent | undefined;
  @Input() id: string = '';
  @Input() placeholder: string = '';
  @Input() readonly: boolean = false;
  @Output() onChange = new EventEmitter<string>();
  @Output() onVersionChange = new EventEmitter<number>();
  @Input() versions: number[] = [];
  @Input() codeModel: CodeModel | undefined = {
    language: 'json',
    uri: 'main.json',
    value: ''
  };;

  isCopied = false;
  theme = 'vs-dark';

  options = {
    contextmenu: true,
    minimap: {
      enabled: true
    }
  };

  constructor(private cdr: ChangeDetectorRef) { }

  ngOnChanges() {
    this.cdr.detectChanges();
  }

  ngOnInit() {
    console.log('editor', this._codeEditor);
  }

  onCodeChanged(value: any) {
    console.log('CODE', value);
  }

  copyToClipboard() {
    navigator.clipboard.writeText(this.codeModel!.value).then(() => {
      this.isCopied = true;
      // Hide the check mark after 2 seconds
      setTimeout(() => {
        this.isCopied = false;
      }, 2000);
    });
  }

}

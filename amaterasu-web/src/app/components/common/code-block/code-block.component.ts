import { Component, Input, Output, EventEmitter, OnInit, ChangeDetectorRef } from '@angular/core';
import { CodeModel } from '@ngstack/code-editor';


@Component({
    selector: 'app-code-block',
    templateUrl: './code-block.component.html',
    styleUrls: ['./code-block.component.scss'],
    standalone: false
})
export class CodeBlockComponent implements OnInit {
  @Input() id: string = '';
  @Input() placeholder: string = '';
  @Input() readonly: boolean = false;
  @Input() versions: number[] = [];
  @Output() onChange = new EventEmitter<string>();
  @Output() onVersionChange = new EventEmitter<number>();
  @Input() codeModel: CodeModel | undefined;

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

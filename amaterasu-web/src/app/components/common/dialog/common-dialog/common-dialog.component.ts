import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CodeModel } from '@ngstack/code-editor';


@Component({
    selector: 'app-common-dialog',
    templateUrl: './common-dialog.component.html',
    styleUrls: ['./common-dialog.component.scss'],
    standalone: false
})
export class CommonDialogComponent {
  output: CodeModel;
  isCode: boolean = false;
  isReadOnly: boolean = false;
  fileType: string = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { title: string, isCode: boolean, content: string, fileType: string, isReadOnly: boolean },
    private dialogRef: MatDialogRef<CommonDialogComponent>
  ) {
    this.fileType = data.fileType;

    this.output = {
      language: data.fileType,
      uri: 'main.' + data.fileType,
      value: data.content,
    };
    this.isCode = data.isCode;
    this.isReadOnly = data.isReadOnly;
  }

  onCodeChange(newCode: string) {
    console.log('Updated Code:', newCode);
  }

  onVersionSelected(version: number) {
    console.log('Selected Version:', version);
  }

  close() {
    this.dialogRef.close();
  }
}

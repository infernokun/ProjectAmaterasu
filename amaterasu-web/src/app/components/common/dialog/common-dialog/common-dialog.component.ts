import { Component, Inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogTitle, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { CodeModel } from '../../monaco-editor/monaco-editor.component';
import { QuestionBase } from '../../../../models/simple-form-data.model';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatSelectChange, MatSelect, MatOption } from '@angular/material/select';
import { ApiResponse } from '../../../../models/api-response.model';
import { LabTracker } from '../../../../models/lab/lab-tracker.model';
import { NgIf, NgFor } from '@angular/common';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { CodeBlockComponent } from '../../code-block/code-block.component';
import { MatButton } from '@angular/material/button';

@Component({
    selector: 'amaterasu-common-dialog',
    templateUrl: './common-dialog.component.html',
    styleUrls: ['./common-dialog.component.scss'],
    imports: [MatDialogTitle, NgIf, NgFor, MatFormField, MatLabel, MatSelect, ReactiveFormsModule, MatOption, CdkScrollable, MatDialogContent, CodeBlockComponent, MatDialogActions, MatButton]
})
export class CommonDialogComponent {
  output: CodeModel;
  isCode = signal(false);
  isReadOnly = signal(false);
  fileType: string = '';
  options: { questions: QuestionBase[], current: string, async: Function, labTracker: LabTracker };

  formControl: FormControl = new FormControl('');

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { title: string, isCode: boolean, content: string, fileType: string, isReadOnly: boolean, options: { questions: QuestionBase[], current: string, async: Function, labTracker: LabTracker } },
    private dialogRef: MatDialogRef<CommonDialogComponent>
  ) {
    this.fileType = data.fileType;

    this.output = {
      language: data.fileType,
      uri: 'main.' + data.fileType,
      value: data.content,
    };
    this.isCode.set(data.isCode);
    this.isReadOnly.set(data.isReadOnly);
    this.options = data.options;

    if (this.options) {
      this.formControl.setValue(this.options.current);
    }
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

  handleMatSelect(event: MatSelectChange) {
    if (this.options.async) {
      this.options.async(this.options.labTracker.id, this.options.labTracker.remoteServer?.id, event.value).subscribe((res: ApiResponse<any>) => {
        this.output.value = res.data;
      })
    }
  }
}

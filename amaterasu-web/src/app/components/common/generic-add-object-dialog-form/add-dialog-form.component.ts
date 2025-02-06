import {
  AfterViewInit,
  Component,
  Inject,
  OnInit,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MessageService } from '../../../services/message/message.service';
import { SimpleFormData } from '../../../models/simple-form-data.model';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DialogQuestionComponent } from '../generic-dialog-question/dialog-question.component';

@Component({
  selector: 'app-add-dialog-form',
  templateUrl: './add-dialog-form.component.html',
  styleUrls: ['./add-dialog-form.component.scss'],
})
export class AddDialogFormComponent implements OnInit, AfterViewInit {
  @ViewChildren(DialogQuestionComponent) questionComponents: QueryList<DialogQuestionComponent> | undefined;

  dynamicForm: FormGroup | undefined;

  cache: any;

  constructor(
    public dialogRef: MatDialogRef<AddDialogFormComponent>,
    @Inject(MAT_DIALOG_DATA) public data: SimpleFormData,
    private fb: FormBuilder,
    private messageService: MessageService
  ) { }

  ngOnInit(): void {
    this.data.questions.forEach((q) => {
      q.cb = (k: string, v: string) => {
        // console.log(k, v);
        this.updateMap(k, v);
      };

      if (this.data.preFilledData?.has(q.key)) {
        q.value = this.data.preFilledData?.get(q.key);
      }
    });

    this.dynamicForm = this.fb.group({});
  }

  ngAfterViewInit() {
    this.questionComponents?.forEach(
      (questionComponent: DialogQuestionComponent) => {
        this.dynamicForm?.addControl(
          questionComponent.question.key,
          questionComponent.formControl
        );
      }
    );
  }

  // updateResult(k: string, e: any) {
  //   console.log(e);
  //   this.question.cb(k, e.target.value);
  // }

  hideUploadBox(event: boolean) {
    console.log('huh', event)

    this.questionComponents!.forEach((q) => {
      if (q.question.key == 'dockerFile') {
        q.setUploadBoxVisibility(event);
      }
    })
  }

  onSubmit() {
    if (this.dynamicForm!.valid) {
      this.dialogRef.close(this.data);
    } else {
      const invalidControls: string[] = [];

      Object.keys(this.dynamicForm!.controls).forEach((key) => {
        const control = this.dynamicForm!.get(key);
        if (control && !control.valid) {
          invalidControls.push(this.getQuestionLabelByKey(key));
        }
      });

      if (invalidControls.length > 0) {
        const invalidControlsMessage = `The following values are not valid: ${invalidControls.join(', ')}`;
        this.messageService.snackbar(invalidControlsMessage);
      }
    }
  }

  getQuestionLabelByKey(key: string): string {
    const question = this.data.questions.find(q => q.key === key);
    return question ? question.label : key;
  }

  onCancel() {
    this.dialogRef.close(undefined);
  }

  updateMap(input: string, val: string) {
    // console.log(val);
    // console.log(this.data);
    this.data.result.set(input, val);
  }

  formatTitle(title: string): string {
    if (title == 'subIndicator') {
      return 'Sub-Indicator';
    }
    return title.charAt(0).toUpperCase() + title.substring(1, title.length);
  }
}

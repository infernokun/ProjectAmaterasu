import {
  AfterViewInit,
  Component,
  Inject,
  OnInit,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MessageService } from '../../../services/message.service';
import { SimpleFormData } from '../../../models/simple-form-data.model';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DialogQuestionComponent } from '../dialog-question/dialog-question.component';
import { RemoteServer } from '../../../models/remote-server.model';
import { Team } from '../../../models/team.model';
import { take } from 'rxjs';

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

        if (questionComponent.question.asyncData) {
          questionComponent.question.asyncData.pipe(take(1)).subscribe((data: any[]) => {
            if (!data || data.length === 0) return;

            switch (questionComponent.question.key) {
              case 'remoteServer':
                console.log('async data!!! -> remoteServer');
                questionComponent.question.options = data
                  .filter(r => r.name && r.id)
                  .map(r => ({ key: r.name, value: r.id }));
                break;
              case 'team':
                console.log('async data!!! -> teams');
                questionComponent.question.options = data
                  .filter(t => t.name && t.id)
                  .map(t => ({ key: t.name, value: t.id }));
                break;
              default:
                return;
            }
            if (questionComponent.question.options.length > 0) {
              questionComponent.formControl.setValue(questionComponent.question.options[0].value);
            }
          });
        }
      }
    );
  }

  // updateResult(k: string, e: any) {
  //   console.log(e);
  //   this.question.cb(k, e.target.value);
  // }

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

  checkboxSelectionChanged(event: any) {
    this.questionComponents?.forEach((questionComponent: DialogQuestionComponent) => {
      if (!questionComponent.question.neededEnum) return;

      const selectedEnum = event.value;

      if (questionComponent.question.neededEnum.value == selectedEnum) {
        questionComponent.isHidden = !questionComponent.isHidden;
        console.log('question', questionComponent.question.key, "needs", questionComponent.question.neededEnum.value)
        questionComponent.question.asyncData?.subscribe((data) => {
          questionComponent.question.options2 = data.map((o: any) => ({ key: o.vmid, value: o.name }));
        })
      } else {
        questionComponent.isHidden = true

      }

    })

    const hidden = this.questionComponents?.map((q) => q.isHidden);
    console.log(hidden);
  }
}

import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { QuestionBase } from '../../../models/simple-form-data.model';
import { LabType } from '../../../enums/lab-type.enum';

@Component({
  selector: 'app-dialog-question',
  templateUrl: './dialog-question.component.html',
  styleUrls: ['./dialog-question.component.scss'],
})
export class DialogQuestionComponent implements OnInit {
  @Input() question!: QuestionBase;

  @ViewChild('dropdownMenu') dropdownMenu!: ElementRef;
  @ViewChild('textInput') textInput!: ElementRef;

  formControl: FormControl = new FormControl('');
  @Output() labType = new EventEmitter<boolean>();
  showUploadBox: boolean = false;

  required: string[] = [
    'title',
    'rank',
    'definition',
    'status',
    'statusNarrative',
    'information',
    'source',
    'link',
  ];

  file: File | undefined;

  constructor(private dialog: MatDialog) { }

  ngOnInit(): void {
    this.formControl.valueChanges.subscribe((value) => {
      this.question.cb(this.question.key, value);
      // Emit the labType event if the question key is 'labType'
      if (this.question.key === 'labType') {
        this.labType.emit(value === LabType.DOCKER_COMPOSE);
        this.setUploadBoxVisibility(value === LabType.DOCKER_COMPOSE);
      }
    });

    if (this.question.dependentQuestions) {
      for (const ent of this.question.dependentQuestions.entries()) {
        ent[1].cb = this.question.cb;
      }
    }

    if (this.required.includes(this.question.key)) {
      this.formControl.setValidators([Validators.required]);
    }

    if (this.question.value) {
      this.formControl.setValue(this.question.value.toString());
      return;
    }
    if (this.question.type === 'dropdown') {
      this.question.cb(this.question.key, this.question.options[0].value);
    }
    if (this.question.type === 'number') {
      this.formControl.addValidators(Validators.pattern(/^[0-9]*$/));
    }
  }

  handleAction(): void {
    switch (this.question.key) {
      case 'test':
        const config = new MatDialogConfig();
        config.autoFocus = false;
        config.minWidth = '50vw';
        config.minHeight = '10vw';
        config.data = {
          title: 'test',
        };
        if (this.question.action) {
          // Call the action if defined
          this.question.action();
        }
        break;
    }
  }

  setUploadBoxVisibility(isVisible: boolean) {
    this.showUploadBox = isVisible;
    console.log(this.question)
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.addFile(input.files[0]);
    }
  }

  onFileDropped(file: File) {
    this.addFile(file);
  }

  addFile(file: File) {
    this.file = file;
    this.readFileContent(file);
  }

  readFileContent(file: File) {
    const reader = new FileReader();
    reader.onload = (e) => {
      const fileContent = e.target?.result as string;
      this.formControl.setValue({
        file: this.file,
        content: fileContent,
      });
    };
    reader.readAsText(file);
  }

  removeFile() {
    this.file = undefined;
    this.formControl.setValue({ file: undefined, content: undefined });
  }

  validateYaml() {
    // Your validation logic here
    console.log('Validating YAML file:', this.file);
  }
}

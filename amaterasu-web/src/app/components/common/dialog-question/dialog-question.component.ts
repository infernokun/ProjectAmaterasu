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
import { MatDialog } from '@angular/material/dialog';
import { QuestionBase } from '../../../models/simple-form-data.model';
import { MatRadioChange } from '@angular/material/radio';
import { MatSelectChange } from '@angular/material/select';

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
  @Output() checkboxSelectionChanged = new EventEmitter<{ key: string, value: any }>();
  @Output() buttonClicked = new EventEmitter<{ key: string, value: any }>();
  isHidden: boolean = false;
  isDisabled: boolean = false;
  isValidated: boolean = false;

  required: string[] = [
    'name',
    'description',
    'labType',
    'username',
    'password',
    'apiToken',
    'ipAddress',
    'nodeName'
  ];

  file: File | undefined;

  constructor(private dialog: MatDialog) { }

  ngOnInit(): void {
    this.formControl.valueChanges.subscribe((value) => {
      this.question.cb(this.question.key, value);
      // Emit the labType event if the question key is 'labType'
      /*if (this.question.key === 'labType') {
        this.labType.emit(value === LabType.DOCKER_COMPOSE);
        this.setUploadBoxVisibility(value === LabType.DOCKER_COMPOSE);
      }*/
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
      if (!this.question.options) return;
      if (this.question.options[0] == undefined) return;

      this.question.cb(this.question.key, this.question.options[0].value);
      this.formControl.setValue(this.question.options[0].value);
    }

    if (this.question.type === 'number') {
      this.formControl.addValidators(Validators.pattern(/^[0-9]*$/));
    }

    if (this.question.type === 'radio') {
      if (!this.question.options) return;
      if (this.question.options[0] == undefined) return;
      if (this.question.options[0].value == undefined) return;

      this.formControl.setValue(this.question.options[0].value);
    }

    if (this.question.type === 'checkbox') {
      this.formControl = new FormControl(this.question.value || []);
    }


    this.isHidden = this.question.isHiddenByDefault!;
  }

  handleAction(event: Event, value?: string): void {
  }

  handleMatSelect(event: MatSelectChange) {
    console.log(event.value);
  }

  handleMatRadioSelect(event: MatRadioChange, value?: string): void {
    if (!value) return;
    this.formControl.setValue(event.value);
    this.checkboxSelectionChanged.emit({ key: this.question.key, value: this.formControl.value });
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

  isQuestionVisible(question: QuestionBase): boolean {
    if (!question.neededEnum) {
      return true; // No condition, always show
    }

    const relatedControl = this.formControl.get(question.neededEnum.key)!;
    return relatedControl && relatedControl.value === question.neededEnum.value;
  }

  checkboxChange(event: any, value: string) {
    const isChecked = event.checked;
    const currentValue: string[] = this.formControl.value || []; // Get current value

    if (isChecked) {
      // Add the key to the array
      this.formControl.setValue([...currentValue, value]);
    } else {
      // Remove the key from the array
      this.formControl.setValue(currentValue.filter(k => k !== value));
    }

    console.log('Selected values:', this.formControl.value);
    this.question.cb(this.question.key, this.formControl.value); // Call the callback
  }

  clickedButtonQuestion(question: QuestionBase) {
    this.buttonClicked.emit(question);
  }
}

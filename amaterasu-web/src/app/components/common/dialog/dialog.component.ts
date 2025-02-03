import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-dialog',
  templateUrl: './dialog.component.html',
  styleUrls: ['./dialog.component.scss'],
})
export class DialogComponent {
  output: string;
  applyColor: boolean;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: { output: string, applyColor: boolean },
    private dialogRef: MatDialogRef<DialogComponent>
  ) {
    this.output = data.output;
    this.applyColor = data.applyColor;
  }

  close() {
    this.dialogRef.close();
  }
}

import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogContent, MatDialogActions, MatDialogClose } from '@angular/material/dialog';
import { NgClass, NgIf } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { MatButton } from '@angular/material/button';

export interface ConfirmationDialogData {
  title: string;
  message: string;
  confirmButtonText: string;
  cancelButtonText: string;
  confirmButtonColor?: 'primary' | 'accent' | 'warn';
}

@Component({
    selector: 'amaterasu-confirmation-dialog',
    templateUrl: './confirmation-dialog.component.html',
    styleUrl: './confirmation-dialog.component.scss',
    imports: [NgClass, MatIcon, CdkScrollable, MatDialogContent, MatDialogActions, MatButton, MatDialogClose, NgIf]
})
export class ConfirmationDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ConfirmationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmationDialogData
  ) {}
  
  getHeaderIcon(): string {
    if (this.data.confirmButtonColor === 'warn') {
      return 'warning';
    } else if (this.data.confirmButtonColor === 'accent') {
      return 'help';
    } else {
      return 'info';
    }
  }
}
import {
  Component,
  OnInit,
  ViewEncapsulation,
  ChangeDetectionStrategy,
} from '@angular/core';

import { ICellRendererParams } from 'ag-grid-community';
import { ICellRendererAngularComp } from 'ag-grid-angular';

export interface AdminActionRendererParams extends ICellRendererParams {
  viewClick: (data: any) => void;
  editClick: (data: any) => void;
  deleteClick: (data: any) => void;
}

@Component({
  selector: 'app-admin-action',
  template: `
    <span class="h-100 row center">
    <button *ngIf="!params || !params.data || (!!params.data && !!params.viewClick)" mat-icon-button aria-label="View" class="table-action" color="primary" matToolTip="View" (click)="view()">
      <mat-icon class="sm-icon" fontSet="material-symbols-outlined">visibility</mat-icon>
    </button>
    <button *ngIf="!params || !params.data || (!!params.data && !!params.editClick)" mat-icon-button aria-label="Edit" class="table-action" color="primary" matToolTip="Edit" (click)="edit()">
      <mat-icon class="sm-icon" fontSet="material-symbols-outlined">edit</mat-icon>
    </button>
    <button *ngIf="!params || !params.data || (!!params.data && !!params.deleteClick)" mat-icon-button aria-label="Delete" class="table-action" color="primary" matToolTip="Delete" (click)="delete()">
      <mat-icon class="sm-icon" fontSet="material-symbols-outlined">delete</mat-icon>
    </button>
    </span>
  `,
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false,
})
export class AdminActionsComponent implements ICellRendererAngularComp {
    params?: AdminActionRendererParams;
    constructor() {}

  agInit(params: AdminActionRendererParams): void {
      this.params = params;
  }

  refresh(params: AdminActionRendererParams): boolean {
      this.params = params;
      return true;
  }

  view() {
    this.params?.viewClick(this.params?.data);
  }

  edit() {
    this.params?.editClick(this.params?.data);
  }

  delete() {
    this.params?.deleteClick(this.params?.data);
  }
}

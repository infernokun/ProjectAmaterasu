import { Component, Injectable } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { SimpleFormData } from '../../models/simple-form-data.model';
import { map, Observable } from 'rxjs';
import { StoredObject } from '../../models/stored-object.model';
import { AddDialogFormComponent } from '../../components/common/add-dialog-form/add-dialog-form.component';
import { LoginComponent } from '../../components/common/login/login.component';
import { RegisterComponent } from '../../components/common/register/register.component';
import { ComponentType } from '@angular/cdk/overlay';

@Injectable({
  providedIn: 'root'
})
export class EditDialogService {
  constructor(private dialog: MatDialog) { }

  openForm(formData: SimpleFormData | undefined, component?: ComponentType<any>): Observable<any> {
    const config = new MatDialogConfig();
    config.disableClose = true;
    config.autoFocus = true;
    config.data = formData;
    config.minWidth = "50vw";
    return this.dialog
      .open(component ? component : AddDialogFormComponent, config)
      .afterClosed();
  }

  openDialog<T extends StoredObject>(
    formData: SimpleFormData,
    cb: Function
  ): Observable<any> {
    return this.openForm(formData).pipe(
      map((res) => {
        if (res instanceof SimpleFormData) {
          const temp = new Object() as T;
          res.fillObject(temp);
          cb(temp);
        }
      })
    );
  }
  
  openLoginDialog(): Observable<any> {
    return this.openForm(undefined, LoginComponent);
  }

  openRegisterDialog(): Observable<any> {
    return this.openForm(undefined, RegisterComponent);
  }
}

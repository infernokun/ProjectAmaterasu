import { Injectable } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { SimpleFormData } from '../../models/simple-form-data.model';
import { map, Observable } from 'rxjs';
import { StoredObject } from '../../models/stored-object.model';
import { AddDialogFormComponent } from '../../components/common/add-dialog-form/add-dialog-form.component';

@Injectable({
  providedIn: 'root'
})
export class EditDialogService {
  constructor(private dialog: MatDialog) { }

  openForm(formData: SimpleFormData): Observable<any> {
    const config = new MatDialogConfig();
    config.disableClose = true;
    config.autoFocus = true;
    config.data = formData;
    config.minWidth = "50vw";
    return this.dialog
      .open(AddDialogFormComponent, config)
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
}

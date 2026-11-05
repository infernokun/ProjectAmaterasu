import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';
import { ConfirmDialogComponent, ConfirmOptions, ConfirmResult } from '../ui/confirm-dialog.component';
import { DIALOG_DEFAULTS } from '../ui/dialog';

/**
 * Confirmation for destructive or costly actions. Resolves to 'confirm', 'alternative' (the
 * safer choice, when offered) or 'cancel' (also when dismissed).
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(MatDialog);

  open(options: ConfirmOptions): Observable<ConfirmResult> {
    return this.dialog
      .open<ConfirmDialogComponent, ConfirmOptions, ConfirmResult>(ConfirmDialogComponent, {
        ...DIALOG_DEFAULTS,
        data: options,
        role: 'alertdialog',
        autoFocus: options.typeToConfirm ? 'input' : 'first-tabbable',
      })
      .afterClosed()
      .pipe(map(result => result ?? 'cancel'));
  }
}

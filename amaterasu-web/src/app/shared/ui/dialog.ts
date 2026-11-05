import { MatDialogConfig } from '@angular/material/dialog';

/** Dialog options that match the design system modal. Spread into MatDialog.open(). */
export const DIALOG_DEFAULTS: MatDialogConfig = {
  panelClass: 'ama-dialog',
  backdropClass: 'ama-backdrop',
  width: '480px',
  maxWidth: 'calc(100vw - 32px)',
};

import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { LoginComponent, LoginDialogData, LoginDialogResult } from '../components/common/login/login.component';
import { RegisterComponent, RegisterDialogResult } from '../components/common/register/register.component';
import { DIALOG_DEFAULTS } from '../shared/ui/dialog';

/** Opens the sign in and create account dialogs, and moves between them. */
@Injectable({ providedIn: 'root' })
export class AuthDialogsService {
  private readonly dialog = inject(MatDialog);

  signIn(data: LoginDialogData = {}): void {
    this.dialog
      .open<LoginComponent, LoginDialogData, LoginDialogResult>(LoginComponent, { ...DIALOG_DEFAULTS, width: '420px', data })
      .afterClosed()
      .subscribe(result => {
        if (result === 'register') {
          this.register();
        }
      });
  }

  register(): void {
    this.dialog
      .open<RegisterComponent, void, RegisterDialogResult>(RegisterComponent, { ...DIALOG_DEFAULTS, width: '440px' })
      .afterClosed()
      .subscribe(result => {
        if (result === 'login') {
          this.signIn();
        } else if (result) {
          this.signIn({ username: result.registered, notice: 'Account created. Sign in to continue.' });
        }
      });
  }
}

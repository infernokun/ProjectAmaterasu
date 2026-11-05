import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { readStorage, writeStorage } from '../../../core/storage';
import { takeRedirectUrl } from '../../../guards/auth.guard';
import { User } from '../../../models/user.model';
import { AuthService } from '../../../services/auth.service';
import { LoginService } from '../../../services/login.service';
import { IconComponent } from '../../../shared/ui/icon.component';

export interface LoginDialogData {
  username?: string;
  /** Shown above the form, e.g. after creating an account. */
  notice?: string;
}

/** Closes with true when signed in, or 'register' to switch to creating an account. */
export type LoginDialogResult = true | 'register' | undefined;

const REMEMBERED_USER_KEY = 'rememberedUser';

@Component({
  selector: 'amaterasu-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  imports: [ReactiveFormsModule, IconComponent],
})
export class LoginComponent {
  private readonly loginService = inject(LoginService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly dialogRef = inject<MatDialogRef<LoginComponent, LoginDialogResult>>(MatDialogRef);
  protected readonly data = inject<LoginDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  private readonly remembered = readStorage(REMEMBERED_USER_KEY);

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: [this.data?.username ?? this.remembered ?? '', [Validators.required]],
    password: ['', [Validators.required]],
    remember: [!!this.remembered],
  });

  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly showPassword = signal(false);

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) {
      return;
    }
    const { username, password, remember } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set('');

    this.loginService.login(username.trim(), password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: response => {
          const accessToken = response.data?.accessToken;
          if (!accessToken || !response.data.user) {
            this.error.set('Sign in failed. Try again.');
            return;
          }
          writeStorage(REMEMBERED_USER_KEY, remember ? username.trim() : null);
          // The refresh token arrives as an HttpOnly cookie with this response
          this.authService.setPayload(new User(response.data.user), accessToken);
          this.dialogRef.close(true);
          this.router.navigateByUrl(takeRedirectUrl());
        },
        error: error => {
          if (error?.status === 401) {
            this.error.set('That username and password do not match.');
          } else if (error?.status === 429) {
            this.error.set(error.error?.message || 'Too many attempts. Wait a minute and try again.');
          } else if (error?.status === 0) {
            this.error.set('The server could not be reached.');
          } else {
            this.error.set(error?.error?.message || 'Sign in failed. Try again.');
          }
        },
      });
  }

  protected register(): void {
    this.dialogRef.close('register');
  }

  protected close(): void {
    this.dialogRef.close();
  }

  protected invalid(name: 'username' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}

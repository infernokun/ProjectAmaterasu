import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { errorMessage } from '../../../core/http-error';
import { LoginService } from '../../../services/login.service';
import { IconComponent } from '../../../shared/ui/icon.component';

/** Closes with the new username once the account exists, or 'login' to switch to signing in. */
export type RegisterDialogResult = { registered: string } | 'login' | undefined;

const PASSWORD_RULES = [
  { text: '8 to 72 characters', test: (value: string) => value.length >= 8 && value.length <= 72 },
  { text: 'An uppercase letter', test: (value: string) => /[A-Z]/.test(value) },
  { text: 'A lowercase letter', test: (value: string) => /[a-z]/.test(value) },
  { text: 'A number', test: (value: string) => /[0-9]/.test(value) },
  { text: 'A symbol', test: (value: string) => /[^A-Za-z0-9]/.test(value) },
];

function passwordRules(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  return PASSWORD_RULES.every(rule => rule.test(value)) ? null : { passwordRules: true };
}

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return !confirm || password === confirm ? null : { passwordMismatch: true };
}

@Component({
  selector: 'amaterasu-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
  imports: [ReactiveFormsModule, IconComponent],
})
export class RegisterComponent {
  private readonly loginService = inject(LoginService);
  private readonly dialogRef = inject<MatDialogRef<RegisterComponent, RegisterDialogResult>>(MatDialogRef);

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(32), Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    password: ['', [Validators.required, passwordRules]],
    confirmPassword: ['', [Validators.required]],
  }, { validators: passwordsMatch });

  protected readonly busy = signal(false);
  protected readonly error = signal('');

  private readonly password = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });
  protected readonly rules = computed(() => PASSWORD_RULES.map(rule => ({ text: rule.text, met: rule.test(this.password()) })));

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) {
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.loginService.register(username, password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.dialogRef.close({ registered: username }),
        error: error => this.error.set(errorMessage(error) ?? 'The account could not be created. Try again.'),
      });
  }

  protected signIn(): void {
    this.dialogRef.close('login');
  }

  protected close(): void {
    this.dialogRef.close();
  }

  protected invalid(name: 'username' | 'password' | 'confirmPassword'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  protected usernameError(): string {
    const errors = this.form.controls.username.errors ?? {};
    if (errors['required']) return 'Choose a username';
    if (errors['minlength']) return 'Use at least 3 characters';
    if (errors['maxlength']) return 'Use at most 32 characters';
    return 'Use letters, numbers, dots, dashes and underscores only';
  }
}

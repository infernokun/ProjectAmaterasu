// register.component.ts
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LoginService } from '../../../services/login.service';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
  standalone: false
})
export class RegisterComponent implements OnInit {
  registerForm: FormGroup = new FormGroup({});
  busy = false;
  hidePassword = true;
  hideConfirmPassword = true;
  formSubmitted = false;
  
  constructor(
    private loginService: LoginService,
    private dialogRef: MatDialogRef<RegisterComponent>,
    private snackBar: MatSnackBar,
    private fb: FormBuilder
  ) { }

  ngOnInit(): void {
    this.registerForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [
        Validators.required, 
        Validators.minLength(8),
        this.passwordStrengthValidator
      ]],
      confirmPassword: ['', Validators.required]
    }, { 
      validators: this.passwordMatchValidator 
    });
  }

  // Custom validator for password strength
  passwordStrengthValidator(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    if (!value) {
      return null;
    }

    const hasUpperCase = /[A-Z]+/.test(value);
    const hasLowerCase = /[a-z]+/.test(value);
    const hasNumeric = /[0-9]+/.test(value);
    const hasSpecialChar = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]+/.test(value);

    const passwordValid = hasUpperCase && hasLowerCase && hasNumeric && hasSpecialChar;

    return !passwordValid ? { passwordStrength: true } : null;
  }

  // Custom validator for password matching
  passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
    const password = control.get('password');
    const confirmPassword = control.get('confirmPassword');

    if (!password || !confirmPassword) {
      return null;
    }

    return password.value === confirmPassword.value ? null : { passwordMismatch: true };
  }

  // Helper method to check if passwords match
  passwordsMatch(): boolean {
    const password = this.registerForm.get('password')?.value;
    const confirmPassword = this.registerForm.get('confirmPassword')?.value;
    return !!password && !!confirmPassword && password === confirmPassword;
  }

  // Submit form
  public registerClick(): void {
    this.formSubmitted = true;
    
    // Mark all fields as touched to trigger validation
    Object.keys(this.registerForm.controls).forEach(key => {
      const control = this.registerForm.get(key);
      control?.markAsTouched();
    });
    
    if (this.registerForm.invalid) {
      return;
    }
    
    this.busy = true;
    const { username, password, email } = this.registerForm.value;
    
    this.loginService.register(username, password, email)
      .pipe(finalize(() => this.busy = false))
      .subscribe({
        next: (res) => {
          console.log('register response', res);
          this.dialogRef.close(true);
          this.snackBar.open('Registration successful! You can now log in.', 'Close', { 
            duration: 5000,
            panelClass: 'success-snackbar'
          });
        },
        error: (error) => {
          const errorMessage = error?.error?.message || 'An unexpected error occurred';
          this.snackBar.open('Registration failed: ' + errorMessage, 'Close', { 
            duration: 5000,
            panelClass: 'error-snackbar'
          });
        }
      });
  }
  
  // Helper getter methods for template access
  get usernameControl() { return this.registerForm.get('username'); }
  get emailControl() { return this.registerForm.get('email'); }
  get passwordControl() { return this.registerForm.get('password'); }
  get confirmPasswordControl() { return this.registerForm.get('confirmPassword'); }
  
  // Password strength indicators
  getPasswordStrength(): { text: string, color: string } {
    const password = this.passwordControl?.value;
    
    if (!password) {
      return { text: 'No password', color: '#6b7280' };
    }
    
    const hasUpperCase = /[A-Z]+/.test(password);
    const hasLowerCase = /[a-z]+/.test(password);
    const hasNumeric = /[0-9]+/.test(password);
    const hasSpecialChar = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]+/.test(password);
    
    const score = [hasUpperCase, hasLowerCase, hasNumeric, hasSpecialChar]
      .filter(Boolean).length;
    
    if (password.length < 6) {
      return { text: 'Very weak', color: '#ef4444' };
    }
    
    switch (score) {
      case 0:
      case 1:
        return { text: 'Weak', color: '#ef4444' };
      case 2:
        return { text: 'Fair', color: '#f59e0b' };
      case 3:
        return { text: 'Good', color: '#10b981' };
      case 4:
        return { text: 'Strong', color: '#3b82f6' };
      default:
        return { text: 'Unknown', color: '#6b7280' };
    }
  }
}
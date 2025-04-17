import { Component, OnInit } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LoginService } from '../../../services/login.service';
@Component({
    selector: 'app-register',
    templateUrl: './register.component.html',
    styleUrl: './register.component.scss',
    standalone: false
})
export class RegisterComponent implements OnInit {
  username: string = '';
  password: string = '';
  confirmPassword: string = '';
  email: string = '';

  busy: boolean = false;

  constructor(
    private loginService: LoginService,
    private dialogRef: MatDialogRef<RegisterComponent>,
    private snackBar: MatSnackBar
  ) { }

  ngOnInit(): void {
  }

  public registerClick(): void {
    if (!this.username || !this.password || !this.passwordMatches()) {
      return;
    }

    this.busy = true;
    console.log('Register button clicked', this.username);

    this.loginService.register(this.username, this.password, this.email).subscribe(
      (res) => {
        console.log('register response', res);
        this.busy = false;
        this.dialogRef.close();
        this.snackBar.open('Registration successful', 'Close', { duration: 2000 });
      },
      (error) => {
        this.busy = false;
        const errorMessage = error?.error?.message || 'An unexpected error occurred';
        this.snackBar.open('Error: ' + errorMessage, 'Close', { duration: 2000 });
      }
    );
  }

  public passwordMatches(): boolean {
    return !!this.password && !!this.confirmPassword && this.password === this.confirmPassword;
  }
}

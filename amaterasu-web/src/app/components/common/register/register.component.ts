import { Component, OnInit } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LoginService } from '../../../services/login.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent implements OnInit {
  username: string = 'infernokun';
  password: string = 'password';
  confirmPassword: string = 'password';
  email: string = 'infernokun';

  busy: boolean = false;

  constructor(
    private loginService: LoginService,
    private authService: AuthService,
    private router: Router,
    private dialogRef: MatDialogRef<RegisterComponent>,
    private snackBar: MatSnackBar
  ) { }

  ngOnInit(): void {
  }

  public registerClick(): void {
    if (this.username === '' || this.password === '' || this.email === '' || !this.passwordMatches()) {
      return;
    }

    this.busy = true;
    console.log('Register button clicked', this.username, this.password);
    this.loginService.register(this.username, this.password, this.email).subscribe((res) => {
      console.log('register response', res);
      this.busy = false;
      this.dialogRef.close();
      this.snackBar.open('ok', 'close', { duration: 2000 });
     },
     (error) => {
        this.busy = false;
        this.snackBar.open('error: ' + error.error, 'close', { duration: 2000 });
     });
  }

  public passwordMatches(): boolean {
    if (this.confirmPassword === '') return true;
    
    return this.password === this.confirmPassword;
  }
}

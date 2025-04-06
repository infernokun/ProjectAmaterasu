import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialogRef } from '@angular/material/dialog';
import { LoginService } from '../../../services/login.service';
import { AuthService } from '../../../services/auth.service';
import { ApiResponse } from '../../../models/api-response.model';
import { LoginResponseDTO } from '../../../models/dto/login-response.dto.model';
import { UserService } from '../../../services/user.service';
import { User } from '../../../models/user.model';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrl: './login.component.scss',
    standalone: false
})
export class LoginComponent implements OnInit {
  username: string = '';
  password: string = '';
  wrongPassword: boolean = false;

  busy: boolean = false;

  constructor(
    private loginService: LoginService,
    private authService: AuthService,
    private router: Router,
    private dialogRef: MatDialogRef<LoginComponent>,
    private userService: UserService
  ) { }

  ngOnInit(): void {
  }

  public loginClick(): void {
    // Early return if credentials are empty
    if (!this.username || !this.password) {
      return;
    }
    
    this.busy = true;
    
    this.loginService.login(this.username, this.password).subscribe({
      next: (response: ApiResponse<LoginResponseDTO>) => {
        if (!response.data?.jwt) {
          this.busy = false;
          return;
        }
        
        try {
          const tokenPayload = JSON.parse(atob(response.data.jwt.split('.')[1]));
          
          // Store token and reset state
          localStorage.setItem('jwt', response.data.jwt);
          this.wrongPassword = false;
          
          if (!response.data.user) {
            this.busy = false;
            return;
          }
          
          // Set user data and navigate
          const user = new User(response.data.user);
          this.authService.setPayload(user, tokenPayload);
          this.router.navigate(['/home']);
          this.dialogRef.close();
        } catch (e) {
          console.error('Failed to process JWT token', e);
        } finally {
          this.busy = false;
        }
      },
      error: (error) => {
        this.wrongPassword = error.status === 401;
        console.warn(
          error.status === 401 
            ? 'Invalid username or password' 
            : `Error: ${error}`
        );
        this.busy = false;
      }
    });
  }
}

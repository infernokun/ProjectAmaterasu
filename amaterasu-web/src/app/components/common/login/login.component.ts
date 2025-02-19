import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialogRef } from '@angular/material/dialog';
import { LoginService } from '../../../services/login.service';
import { AuthService } from '../../../services/auth.service';
import { ApiResponse } from '../../../models/api-response.model';
import { LoginResponseDTO } from '../../../models/dto/login-response.dto.model';
import { UserService } from '../../../services/user.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
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
    if (this.username === '' || this.password === '') {
      return;
    }

    this.busy = true;
    console.log('Login button clicked', this.username, this.password);
    this.loginService.login(this.username, this.password).subscribe((response: ApiResponse<LoginResponseDTO>) => {
      if (response.data.jwt) {
        const tokenDecode = atob(response.data.jwt.split('.')[1]);
        if (tokenDecode) {
          console.log('Token decode: ', JSON.parse(tokenDecode));
          localStorage.setItem('jwt', response.data.jwt);
          this.wrongPassword = false;
          this.busy = false;
          this.authService.setPayload(response.data.user!, JSON.parse(tokenDecode));
          this.router.navigate(['/home']);
          this.dialogRef.close();
        }
      }
    },
      (error) => {
        if (error.status === 401) {
          console.log('Invalid username or password');
          this.wrongPassword = true;
        } else {
          console.log('Error: ', error);
        }
        this.busy = false;
      });
  }
}

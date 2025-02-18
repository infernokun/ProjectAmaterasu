import { Injectable } from '@angular/core';
import { BehaviorSubject, catchError, filter, map, Observable, of, switchMap, take } from 'rxjs';
import { CanActivate, Router } from '@angular/router';
import { LoginService } from './login.service';

export interface UserPayload {
  username: string;
  role: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  public payloadSubject: BehaviorSubject<UserPayload | undefined> = new BehaviorSubject<UserPayload | undefined>(undefined);
  public payload$: Observable<UserPayload | undefined> = this.payloadSubject.asObservable();

  public loadingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(true);
  public loading$: Observable<boolean> = this.loadingSubject.asObservable();

  constructor(private loginService: LoginService, private router: Router) { }

  isAuthenticated(): Observable<boolean> {
    const token = localStorage.getItem('jwt');

    if (!token) {
      // No token found, return false immediately
      return of(false);
    }

    const decodedToken = this.decodeToken(token);
    if (!decodedToken || !decodedToken.exp || decodedToken.exp * 1000 <= Date.now()) {
      // Token expired, attempt revalidation
      console.log('Token found and expired, revalidating...');
      return this.revalidateToken(token).pipe(
        catchError(() => of(false)) // If revalidation fails, return false
      );
    }

    console.log('Token found and active');

    // Token is still valid
    const payload: UserPayload = {
      username: decodedToken.sub,
      role: decodedToken.roles
    };

    // Check if token is still valid from the server
    return this.checkTokenValidity(token, payload);
  }

  setPayload(username: string, role: string): void {
    const payload: UserPayload = {
      username: username,
      role: role
    };
    this.payloadSubject.next(payload);
  }

  private decodeToken(token: string): any {
    try {
      // Decode the JWT token
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload;
    } catch (error) {
      return null;
    }
  }

  public revalidateToken(token: string): Observable<boolean> {
    const decodedToken = this.decodeToken(token);
    if (decodedToken && decodedToken.exp && decodedToken.exp * 1000 < Date.now()) {
      console.log('Token expired, revalidating...');
      return this.loginService.loginWithToken(token).pipe(
        map((response: any) => {
          if (response) {
            const payload: UserPayload = {
              username: decodedToken.sub,
              role: decodedToken.roles
            };
            this.payloadSubject.next(payload);
            localStorage.setItem('jwt', response.jwt);
            return true; // Token revalidation successful
          } else {
            return false; // Token revalidation failed
          }
        }),
        catchError(() => of(false)) // Handle error from loginWithToken
      );
    } else {
      return of(false); // Token is still valid, no revalidation needed
    }
  }

  private checkTokenValidity(token: string, payload: UserPayload): Observable<boolean> {
    return this.loginService.checkToken(token).pipe(
      switchMap(answer => {
        if (answer) {
          this.payloadSubject.next(payload);
          console.log('Token database check complete');
          return of(true); // Token is valid
        } else {
          return of(false); // Token is invalid
        }
      }),
      catchError(() => of(false)) // If checking token validity fails, return false
    );
  }

  logout(): void {
    this.payload$
      .pipe(
        take(1),
        filter((payload: UserPayload | undefined) => !!payload)
      )
      .subscribe((payload: UserPayload | undefined) => {
        if (!payload) {
          return;
        }
        console.log('Logging out user: ', payload.username);
        this.loginService.logout(payload.username).subscribe(
          () => {
            localStorage.removeItem('jwt');
            this.payloadSubject.next(undefined);
            this.router.navigate(['/']);
            console.log('Logout successful');
          },
          error => {
            console.error('Logout failed:', error);
          }
        );
      });
  }
}

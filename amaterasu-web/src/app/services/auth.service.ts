import { Injectable } from '@angular/core';
import { BehaviorSubject, catchError, concatMap, filter, map, Observable, of, switchMap, take } from 'rxjs';
import { CanActivate, Router } from '@angular/router';
import { LoginService } from './login.service';
import { User } from '../models/user.model';
import { LoginResponseDTO } from '../models/dto/login-response.dto.model';
import { ApiResponse } from '../models/api-response.model';
import { UserService } from './user.service';

export interface UserPayload {
  user: User;
  token: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private payloadSubject: BehaviorSubject<UserPayload | undefined> = new BehaviorSubject<UserPayload | undefined>(undefined);
  public payload$: Observable<UserPayload | undefined> = this.payloadSubject.asObservable();

  private userSubject: BehaviorSubject<User | undefined> = new BehaviorSubject<User | undefined>(undefined);
  public user$: Observable<User | undefined> = this.userSubject.asObservable();

  private loadingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(true);
  public loading$: Observable<boolean> = this.loadingSubject.asObservable();

  constructor(private loginService: LoginService, private router: Router, private userService: UserService) { }

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

    // Fetch user details from the server
    return this.userService.getUserById(decodedToken.sub).pipe(
      switchMap((user: User | undefined) => {
        if (!user) {
          return of(false);
        }

        const payload: UserPayload = {
          user: user,
          token: token
        };

        // Check if token is still valid from the server
        return this.checkTokenValidity(token, payload);
      }),
      catchError(() => of(false)) // Catch any errors and return false
    );
  }

  setPayload(user: User, jwt: string): void {
    const payload: Readonly<UserPayload> = {
      user: user,
      token: jwt
    };
    this.payloadSubject.next(payload);
    this.userSubject.next(user);
  }

  setLoading(loading: boolean): void {
    this.loadingSubject.next(loading);
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

    if (!decodedToken || !decodedToken.exp || decodedToken.exp * 1000 >= Date.now()) {
      return of(false); // Token is still valid, no revalidation needed
    }

    console.log('Token expired, revalidating...');

    return this.loginService.loginWithToken(token).pipe(
      concatMap((response: ApiResponse<LoginResponseDTO>) => {
        if (!response || !response.data?.jwt) {
          console.error('Token revalidation failed at loginWithToken');
          localStorage.removeItem('jwt');
          return of(false);
        }

        const newToken = response.data.jwt;
        const newDecodedToken = this.decodeToken(newToken);

        if (!newDecodedToken || !newDecodedToken.sub) {
          console.error('Invalid token after revalidation');
          localStorage.removeItem('jwt');
          return of(false);
        }

        // Fetch updated user data
        return this.userService.getUserById(newDecodedToken.sub).pipe(
          map((user: User | undefined) => {
            if (!user) {
              console.error('Token revalidation failed at getUserById');
              localStorage.removeItem('jwt');
              return false;
            }

            const payload: UserPayload = {
              user: user,
              token: newToken // Use new token from response
            };

            this.payloadSubject.next(payload);
            this.userSubject.next(user);
            localStorage.setItem('jwt', newToken);
            return true; // Token revalidation successful
          }),
          catchError(() => {
            localStorage.removeItem('jwt');
            return of(false);
          })
        );
      }),
      catchError(() => {
        localStorage.removeItem('jwt');
        return of(false);
      })
    );
  }


  private checkTokenValidity(token: string, payload: UserPayload): Observable<boolean> {
    return this.loginService.checkToken(token).pipe(
      switchMap(answer => {
        if (answer) {
          this.payloadSubject.next(payload);
          this.userSubject.next(payload.user);

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
        console.log('Logging out user: ', payload);
        this.loginService.logout(payload.user.id!).subscribe(
          () => {
            localStorage.removeItem('jwt');
            this.payloadSubject.next(undefined);
            this.userSubject.next(undefined);
            this.router.navigate(['/']);
            console.log('Logout successful');
          },
          error => {
            console.error('Logout failed:', error);
          }
        );
      });
  }

  setUser(user: User) {
    if (this.userSubject.value?.id == user.id) {
      this.userSubject.next(user);
    }
  }
}

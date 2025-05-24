import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor() { }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = localStorage.getItem('jwt');

    if (token && this.isValidToken(token)) {
      req = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }
    return next.handle(req);
  }

  private isValidToken(token: string): boolean {
    const tokenDecode = atob(token.split('.')[1]);
    if (tokenDecode) {
      const tokenObject = JSON.parse(tokenDecode);
      if (tokenObject.exp) {
        const expirationDate = new Date(tokenObject.exp * 1000);
        return expirationDate > new Date();
      }
    }
    return false;
  }
}

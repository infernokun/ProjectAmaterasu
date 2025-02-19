import { Injectable } from "@angular/core";
import { ActivatedRoute, CanActivate, Router } from "@angular/router";
import { Observable, map } from "rxjs";
import { AppInitService } from "../services/app-init.service";

@Injectable({
  providedIn: 'root'
})
export class AppInitGuard implements CanActivate {

  constructor(private appInitService: AppInitService,
    private router: Router, private activatedRoute: ActivatedRoute) { }

  canActivate(): Observable<boolean> {
    return this.appInitService.isInitialized().pipe(
      map((initialized: boolean) => {
        if (initialized) {
          // If the app is initialized, allow access to the route
          return true;
        } else {
          // If not initialized, redirect to the app initialization page
          this.router.navigate(['/init']);
          return false;
        }
      })
    );
  }
}

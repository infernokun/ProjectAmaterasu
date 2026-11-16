import { Injectable } from "@angular/core";
import { ActivatedRoute, CanActivate, Router } from "@angular/router";
import { Observable, of, switchMap, take } from "rxjs";
import { AppInitService } from "../services/app-init.service";

@Injectable({
  providedIn: 'root'
})
export class AppInitGuard implements CanActivate {

  constructor(private appInitService: AppInitService,
    private router: Router, private activatedRoute: ActivatedRoute) { }

  canActivate(): Observable<boolean> {
    // Wait until the initialization process is complete
    return this.appInitService.initializationComplete$.pipe(
      take(1),
      switchMap(() => {
        return this.appInitService.isInitialized().pipe(
          take(1),
          switchMap((initialized: boolean) => {
            if (initialized) {
              return of(true);
            } else {
              this.router.navigate(['/init']);
              return of(false);
            }
          })
        );
      })
    );
  }
}
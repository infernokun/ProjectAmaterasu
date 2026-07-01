import { Component, OnInit, OnDestroy, ChangeDetectionStrategy, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Subject, of, takeUntil, map, startWith, combineLatest, distinctUntilChanged, shareReplay } from 'rxjs';
import { AuthService } from './services/auth.service';
import { EditDialogService } from './services/edit-dialog.service';
import { Role } from './enums/role.enum';
import { AppInitService } from './services/app-init.service';
import { RouterLink, RouterOutlet } from '@angular/router';
import { APP_VERSION } from './version';
import { NgIf } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIconButton } from '@angular/material/button';
import { MatMenuTrigger, MatMenu, MatMenuItem } from '@angular/material/menu';
import { MatIcon } from '@angular/material/icon';
import { MatDivider } from '@angular/material/divider';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrl: './app.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgIf, MatToolbar, RouterLink, MatIconButton, MatMenuTrigger, MatIcon, MatMenu, MatMenuItem, MatDivider, RouterOutlet, MatProgressSpinner]
})
export class AppComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private dialogService = inject(EditDialogService);
  private appInitService = inject(AppInitService);

  title = 'Project Amaterasu';
  header: string = 'UNCLASSIFIED';
  footer: string = 'UNCLASSIFIED';
  appVersion = APP_VERSION;
  bannerDisplayStyle: string = 'green-white';
  Role = Role;

  private readonly initializationComplete$ = this.appInitService.initializationComplete$ ?? of(false);

  // Auth loading state - true while checking authentication or before init completes
  private readonly isAuthLoading$ = combineLatest([
    this.authService.loading$,
    this.appInitService.isInitialized()
  ]).pipe(
    map(([authLoading, appInitialized]) => authLoading || !appInitialized),
    startWith(true)
  );

  // App ready when initialization is complete AND auth check is done
  private readonly isAppReady$ = combineLatest([
    this.initializationComplete$.pipe(
      map(() => true),
      distinctUntilChanged(),
      shareReplay(1),
      startWith(false)
    ),
    this.isAuthLoading$
  ]).pipe(
    map(([initComplete, authLoading]) => initComplete && !authLoading)
  );

  // Reactive state surfaced to the template as signals (replaces the async pipe).
  protected readonly loggedInUser = toSignal(this.authService.user$, { initialValue: undefined });
  protected readonly initializationComplete = toSignal(this.initializationComplete$, { initialValue: false });
  protected readonly isAppReady = toSignal(this.isAppReady$, { initialValue: false });

  private unsubscribe$ = new Subject<void>();

  ngOnInit(): void {
    // Remove the explicit checkAuthentication() call
    // Let the AuthService handle authentication automatically
    console.log('AppComponent initialized - AuthService will handle authentication');

    this.authService.user$.pipe(
      takeUntil(this.unsubscribe$)
    ).subscribe((user) => {
      if (user) {
        console.log('User logged in:', user.username);
      } else {
        console.log('No user logged in', user);
      }
    });
  }

  ngOnDestroy() {
    this.unsubscribe$.next();
    this.unsubscribe$.complete();
  }

  openLoginModal(): void {
    this.dialogService.openLoginDialog().subscribe((res: any) => {
      // Handle login result if needed
    });
  }

  openRegisterModal(): void {
    this.dialogService.openRegisterDialog().subscribe((res: any) => {
      // Handle register result if needed
    });
  }

  openProfileModal() {
    console.log("WIP");
  }

  openUserSettingsModal() {
    console.log("WIP");
  }

  logoutButton(): void {
    this.authService.logout();
  }
}

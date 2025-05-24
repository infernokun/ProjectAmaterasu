import { Component } from '@angular/core';
import { User } from './models/user.model';
import { UserService } from './services/user.service';
import { Observable, Subject, takeUntil, of, map, startWith } from 'rxjs';
import { AuthService } from './services/auth.service';
import { EditDialogService } from './services/edit-dialog.service';

import { Role } from './enums/role.enum';
import { AppInitService } from './services/app-init.service';
import { RemoteServerService } from './services/lab/remote-server.service';
import { FormControl } from '@angular/forms';
import { RemoteServer } from './models/lab/remote-server.model';

declare var require: any;
const { version: appVersion } = require('../../package.json');

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  standalone: false
})
export class AppComponent {
  title = 'Project Amaterasu';
  header: string = 'UNCLASSIFIED';
  footer: string = 'UNCLASSIFIED';
  appVersion: any = appVersion;
  bannerDisplayStyle: string = 'green-white';

  loadingUser$: Observable<boolean> = of(false);
  loggedInUser$: Observable<User | undefined> | undefined;
  isInitialized$: Observable<boolean | undefined> = of(undefined);
  initializationComplete$: Observable<boolean | undefined> = of(undefined);
  appReady$: Observable<boolean> | undefined;

  Role = Role;

  private unsubscribe$ = new Subject<void>();

  constructor(private userService: UserService,
    private authService: AuthService, private dialogService: EditDialogService,
    private appInitService: AppInitService, private remoteServerService: RemoteServerService,
  ) {

  }

  ngOnInit(): void {
    this.appReady$ = this.appInitService.initializationComplete$.pipe(
      // Convert void emission to true
      map(() => true),
      // Start with false until we get a value
      startWith(false)
    );

    this.isInitialized$ = this.appInitService.isInitialized();
    this.initializationComplete$ = this.appInitService.initializationComplete$;
    this.loggedInUser$ = this.authService.user$;
    this.loadingUser$ = this.authService.loading$;

    this.checkAuthentication();
  }

  ngOnDestroy() {
    this.unsubscribe$.next();
    this.unsubscribe$.complete();
  }

  private checkAuthentication() {
    this.authService
      .isAuthenticated()
      .pipe(takeUntil(this.unsubscribe$))
      .subscribe(authenticated => {
        console.log(authenticated ? 'Authenticated' : 'Not authenticated');
        this.authService.setLoading(false);
      });
  }


  openLoginModal(): void {
    this.dialogService.openLoginDialog().subscribe((res: any) => {
    });
  }

  openRegisterModal(): void {
    this.dialogService.openRegisterDialog().subscribe((res: any) => {
    });
  }

  openProfileModal() { }

  openUserSettingsModal() { }

  logoutButton(): void {
    this.authService.logout();
  }
}

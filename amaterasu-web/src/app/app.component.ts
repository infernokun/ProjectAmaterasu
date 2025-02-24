import { Component } from '@angular/core';
import { User } from './models/user.model';
import { UserService } from './services/user.service';
import { Observable, BehaviorSubject, filter, Subject, switchMap, takeUntil, of } from 'rxjs';
import { AuthService } from './services/auth.service';
import { EditDialogService } from './services/edit-dialog.service';

import { Role } from './enums/role.enum';
import { AppInitService } from './services/app-init.service';
import { RemoteServerService } from './services/remote-server.service';
import { RemoteServer } from './models/remote-server.model';
import { FormControl } from '@angular/forms';

declare var require: any;
const { version: appVersion } = require('../../package.json');

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'Project Amaterasu';
  header: string = 'UNCLASSIFIED';
  footer: string = 'UNCLASSIFIED';
  appVersion: any = appVersion;
  bannerDisplayStyle: string = 'green-white';

  loadingUser$: Observable<boolean>;
  loggedInUser$: Observable<User | undefined> | undefined;
  isInitialized: Observable<boolean> = of(false);

  Role = Role;
  protected users: User[] = [];
  remoteServers: RemoteServer[] = [];

  remoteServerControl: FormControl = new FormControl('');

  private unsubscribe$ = new Subject<void>();

  constructor(private userService: UserService,
    private authService: AuthService, private dialogService: EditDialogService,
    private appInitService: AppInitService, private remoteServerService: RemoteServerService
  ) {
    this.isInitialized = this.appInitService.isInitialized();
    this.initializeApp();
    this.checkAuthentication();

    this.loggedInUser$ = this.authService.userSubject;
    this.loadingUser$ = this.authService.loading$;
  }

  ngOnDestroy() {
    this.unsubscribe$.next();
    this.unsubscribe$.complete();
  }

  private initializeApp() {
    this.isInitialized.pipe(
      filter(initialized => initialized),
      switchMap(() => this.remoteServerService.getAllServers()),
      takeUntil(this.unsubscribe$)
    )
      .subscribe(remoteServers => {
        if (remoteServers.length === 0) return;

        this.remoteServerService.setRemoteServers(remoteServers);
        this.remoteServers = remoteServers;
        this.remoteServerControl.setValue(remoteServers[0].id);
      });
  }

  private checkAuthentication() {
    this.authService
      .isAuthenticated()
      .pipe(takeUntil(this.unsubscribe$))
      .subscribe(authenticated => {
        console.log(authenticated ? 'Authenticated' : 'Not authenticated');
        this.authService.loadingSubject.next(false);
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

  logoutButton(): void {
    this.authService.logout();
  }

  selectedRemoteServerChange(remoteServerId: string): void {
    this.remoteServerService.setSelectedRemoteServer(this.remoteServers.find(s => s.id == remoteServerId)!);
  }
}

import { Component } from '@angular/core';
import { User } from './models/user.model';
import { UserService } from './services/user.service';
import { Observable, BehaviorSubject } from 'rxjs';
import { AuthService } from './services/auth.service';
import { EditDialogService } from './services/edit-dialog.service';

import { Role } from './enums/role.enum';
import { AppInitService } from './services/app-init.service';

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

  appVersion: any;
  bannerDisplayStyle: string = 'green-white';

  protected users: User[] = [];
  loggedInUser$: Observable<User | undefined> | undefined;
  loadingUser$: Observable<boolean>;

  isInitialized?: Observable<boolean>;

  Role = Role;

  constructor(private userService: UserService,
    private authService: AuthService, private dialogService: EditDialogService,
    private appInitService: AppInitService
  ) {
    this.appVersion = appVersion;

    this.isInitialized = this.appInitService.isInitialized();

    this.isInitialized.subscribe((initialized: boolean) => {
      if (initialized) {
      }
    });

    this.authService.isAuthenticated().subscribe((authenticated: any) => {
      if (authenticated) {
        console.log('Authenticated');
      } else {
        console.log('Not authenticated');
      }
      this.authService.loadingSubject.next(false);
    });

    this.loggedInUser$ = this.authService.userSubject;
    this.loadingUser$ = this.authService.loading$;
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
}

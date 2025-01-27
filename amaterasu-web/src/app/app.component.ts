import { Component } from '@angular/core';
import { User } from './models/user.model';
import { UserService } from './services/user/user.service';
import { Observable } from 'rxjs';

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

  constructor(private userService: UserService) {
    this.appVersion = appVersion;

    this.userService.getAllUsers().subscribe((users) => {
      if (users.length > 0) {
        this.userService.setLoggedInUser(users[0]); 
      }
    });

    this.loggedInUser$ = this.userService.getLoggedInUser();
  }

  logoutButton() {
    throw new Error('Method not implemented.');
  }
}

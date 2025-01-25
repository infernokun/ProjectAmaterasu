import { Component } from '@angular/core';

declare var require: any;
//const { version: appVersion } = require('../../package.json');

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

  constructor() {
    //this.appVersion = appVersion;
  }

  logoutButton() {
    throw new Error('Method not implemented.');
  }
}

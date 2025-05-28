import { Component } from '@angular/core';
import { AuthService } from '../../../../services/auth.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'amaterasu-ctf-main',
  templateUrl: './ctf-main.component.html',
  styleUrl: './ctf-main.component.scss',
  standalone: false
})
export class CTFMainComponent {
  authenticated: boolean = false;

  loading$: Observable<boolean> | undefined;


  constructor(private authService: AuthService) {
    this.loading$ = this.authService.loading$;
  }


  ngOnInit(): void {
    /*this.authService.isAuthenticated().subscribe((authenticated) => {
      this.authenticated = authenticated;
    });*/
  }
}

import { Component, OnInit } from '@angular/core';
import { User, UserFormData } from '../../models/user.model';
import { UserService } from '../../services/user.service';
import { TeamService } from '../../services/team.service';
import { EditDialogService } from '../../services/edit-dialog.service';
import { BehaviorSubject, Observable } from 'rxjs';
import { Team } from '../../models/team.model';
import { MessageService } from '../../services/message.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-users',
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss'
})
export class UsersComponent implements OnInit {
  users: User[] = [];
  busy: boolean = false;
  private loggedInUserSubject: BehaviorSubject<User | undefined> = new BehaviorSubject<User | undefined>(undefined);

  loggedInUser$: Observable<User | undefined> = this.loggedInUserSubject.asObservable();

  constructor(private userService: UserService,
    private teamService: TeamService, private dialog: EditDialogService,
    private messageService: MessageService, private authService: AuthService) { }

  ngOnInit(): void {
    this.loggedInUser$ = this.authService.user$;

    this.userService.getAllUsers().subscribe((users: User[]) => {
      console.log(users);
      this.users = users;
    });
  }

  editUsers() {
    const teams$: Observable<Team[]> = this.teamService.getAllTeams();

    const userFormData = new UserFormData(
      this.users,
      (k: any, v: any) => { },
      {
        'teams': teams$
      }
    );

    this.dialog.openDialog<any>(userFormData, (data: { user: string, team: string }) => {
      this.busy = true;
      if (!data) return;

      console.log(data.user, data.team);
      this.userService.setUserTeam(data.user, data.team).subscribe((updatedUser: User) => {
        this.users = this.users.map(u => u.id === updatedUser.id ? updatedUser : u);
        console.log(updatedUser)
        this.messageService.snackbar(`User ${updatedUser.username} updated team to ${updatedUser.team!.name ?? ''}`);
      });
    }).subscribe((res: any) => {
    });
  }
}

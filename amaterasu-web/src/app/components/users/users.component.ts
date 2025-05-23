import { Component, OnInit } from '@angular/core';
import { User, UserFormData } from '../../models/user.model';
import { UserService } from '../../services/user.service';
import { TeamService } from '../../services/team.service';
import { EditDialogService } from '../../services/edit-dialog.service';
import { Observable } from 'rxjs';
import { Team } from '../../models/team.model';
import { MessageService } from '../../services/message.service';
import { AuthService } from '../../services/auth.service';
import {
  AllCommunityModule,
  ColDef,
  FirstDataRenderedEvent,
  GridApi,
  GridOptions,
  GridReadyEvent,
  ModuleRegistry,
} from 'ag-grid-community';
import { AdminActionsComponent } from '../../admin/admin-actions.component';

ModuleRegistry.registerModules([ AllCommunityModule ]);

@Component({
  selector: 'app-users',
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss',
  standalone: false,
})
export class UsersComponent implements OnInit {
  users: User[] = [];
  busy: boolean = false;

  gridOptions?: GridOptions<User>;

  defaultColDef: ColDef = {
    sortable: true,
    floatingFilter: true,
    resizable: true,
    filter: 'agTextColumnFilter',
    filterParams: { buttons: ['clear'] },
  };

  gridApi?: GridApi;

  loggedInUser$: Observable<User | undefined> | undefined;

  constructor(
    private userService: UserService,
    private teamService: TeamService,
    private dialog: EditDialogService,
    private messageService: MessageService,
    private authService: AuthService
  ) {
    this.gridOptions = {
      animateRows: false,
      cellFlashDuration: 0,
      pagination: false,
      columnDefs: this.getColumnDefs(),
      rowData: this.users,
      onGridReady: (params: GridReadyEvent) => this.onGridReady(params),
      onFirstDataRendered: (params: FirstDataRenderedEvent) =>
        this.onFirstDataRendered(params),
    };
  }

  ngOnInit(): void {
    this.loggedInUser$ = this.authService.user$;

    this.userService.getAllUsers().subscribe((users: User[]) => {
      console.log(users);
      this.users = users;
      this.gridApi?.sizeColumnsToFit();
    });
  }

  onGridReady(params: GridReadyEvent) {
    this.gridApi = params.api;
  }

  onFirstDataRendered(params: FirstDataRenderedEvent) {
    params.api.sizeColumnsToFit();
  }

  getColumnDefs(): ColDef[] {
    let columns: ColDef[] = [
      {
        headerName: '',
        width: 100,
        suppressAutoSize: true,
        cellRenderer: AdminActionsComponent,
        cellRendererParams: {
          viewClick: (data: any) => console.log('viewClick', data),
          editClick: (data: any) => console.log('editClick', data),
          deleteClick: (data: any) => console.log('deleteClick', data),
        },
      },
      { headerName: 'id', field: 'id', sort: 'asc' },
      { headerName: 'Name', field: 'username' },
      { headerName: 'Team', field: 'username' },
      { headerName: 'Role', field: 'role' },
    ];

    return columns;
  }

  editUsers() {
    const teams$: Observable<Team[]> = this.teamService.getAllTeams();

    const userFormData = new UserFormData(this.users, (k: any, v: any) => {}, {
      teams: teams$,
    });

    this.dialog
      .openDialog<any>(userFormData, (data: { user: string; team: string }) => {
        this.busy = true;
        if (!data) return;

        this.userService
          .setUserTeam(data.user, data.team)
          .subscribe((updatedUser: User) => {
            this.users = this.users.map((u) =>
              u.id === updatedUser.id ? updatedUser : u
            );
            this.authService.setUser(updatedUser);
            this.messageService.snackbar(
              `User ${updatedUser.username} updated team to ${
                updatedUser.team!.name ?? ''
              }`
            );
          });
      })
      .subscribe((res: any) => {});
  }

  importUserJson($event: Event) {
    throw new Error('Method not implemented.');
  }
  exportUsers() {
    throw new Error('Method not implemented.');
  }
  addUser() {
    throw new Error('Method not implemented.');
  }
}

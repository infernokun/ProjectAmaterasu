import { Component, OnDestroy, OnInit } from '@angular/core';
import { RemoteServerService } from '../../../services/lab/remote-server.service';
import { EditDialogService } from '../../../services/edit-dialog.service';
import { AuthService } from '../../../services/auth.service';
import { ApiResponse } from '../../../models/api-response.model';
import { BehaviorSubject, Observable, Subject, take, takeUntil } from 'rxjs';
import { User } from '../../../models/user.model';
import { RemoteServer, RemoteServerFormData } from '../../../models/lab/remote-server.model';

@Component({
  selector: 'amaterasu-remote-server',
  templateUrl: './remote-server.component.html',
  styleUrls: ['./remote-server.component.scss'],
  standalone: false
})
export class RemoteServerComponent implements OnInit, OnDestroy {
  remoteServers: RemoteServer[] = [];
  selectedServer?: RemoteServer;

  private loggedInUserSubject: BehaviorSubject<User | undefined> = new BehaviorSubject<User | undefined>(undefined);

  loggedInUser$: Observable<User | undefined> = this.loggedInUserSubject.asObservable();

  private destroy$ = new Subject<void>();

  constructor(private remoteServerService: RemoteServerService,
    private editDialogService: EditDialogService,
    private authService: AuthService) { }

  ngOnInit(): void {
    this.loadRemoteServers();
    this.loggedInUser$ = this.authService.user$;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadRemoteServers(): void {
    this.remoteServerService.getAllServers().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.remoteServers = data;
        // Optionally select first item
        if (this.remoteServers.length > 0) {
          this.selectServer(this.remoteServers[0]);
        }
      },
      error: err => console.error('Error loading remote servers', err)
    });
  }

  onSelectServer(event: Event): void {
    const input = event.target as HTMLInputElement;
    const server = this.remoteServers.find(server => server.id === input.value);
    if (server) {
      this.selectServer(server);
    }
  }

  addRemoteServer(): void {
    const remoteServerFormData = new RemoteServerFormData();

    this.editDialogService
      .openDialog<RemoteServer>(remoteServerFormData, (remoteServer: RemoteServer) => {
        this.authService.user$.pipe(take(1), takeUntil(this.destroy$)).subscribe(user => {
          if (!user) return;

          remoteServer.createdBy = user.username;

          this.remoteServerService.addServer(remoteServer).pipe(takeUntil(this.destroy$))
            .subscribe((response: ApiResponse<RemoteServer>) => {
              if (!response.data) return;

              this.remoteServers.push(response.data);
              this.selectServer(response.data);
            });
        });
      }).pipe(takeUntil(this.destroy$)).subscribe();
  }

  deleteRemoteServer(id: string): void {
    this.remoteServerService.deleteServer(id).pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.remoteServers = this.remoteServers.filter(server => server.id !== id);
      if (this.remoteServers.length > 0) {
        this.selectServer(this.remoteServers[0]);
      } else {
        this.selectedServer = undefined;
      }
    });
  }

  // Keep the local selection and the shared service selection in sync so that lab
  // creation (createNewLab) and the VM builder see the server the user actually picked.
  private selectServer(server: RemoteServer): void {
    this.selectedServer = server;
    this.remoteServerService.setSelectedRemoteServer(server);
  }
}

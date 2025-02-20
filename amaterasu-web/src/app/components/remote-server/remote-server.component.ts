import { Component, OnInit } from '@angular/core';
import { RemoteServer, RemoteServerFormData } from '../../models/remote-server.model';
import { RemoteServerService } from '../../services/remote-server.service';
import { EditDialogService } from '../../services/edit-dialog.service';
import { AuthService } from '../../services/auth.service';
import { ApiResponse } from '../../models/api-response.model';

@Component({
  selector: 'app-remote-server',
  templateUrl: './remote-server.component.html',
  styleUrls: ['./remote-server.component.scss']
})
export class RemoteServerComponent implements OnInit {
  remoteServers: RemoteServer[] = [];
  selectedServer?: RemoteServer;

  constructor(private remoteServerService: RemoteServerService,
    private editDialogService: EditDialogService,
    private authService: AuthService) { }

  ngOnInit(): void {
    this.loadRemoteServers();
  }

  loadRemoteServers(): void {
    this.remoteServerService.getAllServers().subscribe({
      next: data => {
        this.remoteServers = data;
        // Optionally select first item
        if (this.remoteServers.length > 0) {
          this.selectedServer = this.remoteServers[0];
        }
      },
      error: err => console.error('Error loading remote servers', err)
    });
  }

  onSelectServer(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedServer = this.remoteServers.find(server => server.id === input.value);
  }

  addRemoteServer(): void {

    const remoteServerFormData = new RemoteServerFormData();

    this.editDialogService.openDialog<RemoteServer>(remoteServerFormData, (remoteServer: RemoteServer) => {
      remoteServer = new RemoteServer(remoteServer);

      remoteServer.createdBy = this.authService.userSubject.value?.username;

      console.log('remoteServerFormData', remoteServerFormData);
      console.log('remoteServer', remoteServer);

      this.remoteServerService.addServer(remoteServer).subscribe((response: ApiResponse<RemoteServer>) => {
        this.remoteServers.push(response.data);
        this.selectedServer = response.data;
      });
    });
  }
}

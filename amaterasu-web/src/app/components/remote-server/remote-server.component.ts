import { Component, OnInit } from '@angular/core';
import { RemoteServer } from '../../models/remote-server.model';
import { RemoteServerService } from '../../services/remote-server.service';

@Component({
  selector: 'app-remote-server',
  templateUrl: './remote-server.component.html',
  styleUrls: ['./remote-server.component.scss']
})
export class RemoteServerComponent implements OnInit {
  remoteServers: RemoteServer[] = [];
  selectedServer?: RemoteServer;

  constructor(private remoteServerService: RemoteServerService) { }

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
}

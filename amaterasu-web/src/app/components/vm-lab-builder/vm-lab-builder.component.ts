import { Component, OnInit } from '@angular/core';
import { ProxmoxService } from '../../services/lab/proxmox.service';
import { RemoteServerService } from '../../services/lab/remote-server.service';
import { ServerType } from '../../enums/server-type.enum';
import { EditDialogService } from '../../services/edit-dialog.service';
import { ProxmoxVM } from '../../models/lab/proxmox-vm.model';

@Component({
  selector: 'app-vm-lab-builder',
  templateUrl: './vm-lab-builder.component.html',
  styleUrl: './vm-lab-builder.component.scss',
  standalone: false
})
export class VMLabBuilderComponent implements OnInit {
  vms: ProxmoxVM[] = [];
  selectedVMs: ProxmoxVM[] = [];

  constructor(private proxmoxService: ProxmoxService, private remoteServerService: RemoteServerService,
    private dialog: EditDialogService
  ) { }

  ngOnInit(): void {
    this.remoteServerService.selectedRemoteServer$.subscribe((selectedServer) => {
      if (!selectedServer) return;

      if (selectedServer.serverType !== ServerType.PROXMOX) { this.vms = []; return; }

      this.proxmoxService.getVMTemplates(selectedServer.id!).subscribe((vms: ProxmoxVM[]) => {
        this.vms = vms;
      });
    });
  }

  toggleSelection(vm: ProxmoxVM): void {
    const index = this.selectedVMs.indexOf(vm);
    if (index > -1) {
      this.selectedVMs.splice(index, 1); // Deselect if already selected
    } else {
      this.selectedVMs.push(vm); // Select if not already selected
    }
  }
}

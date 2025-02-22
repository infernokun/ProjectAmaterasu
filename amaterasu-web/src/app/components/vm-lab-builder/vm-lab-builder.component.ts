import { Component, OnInit } from '@angular/core';
import { ProxmoxService } from '../../services/proxmox.service';
import { ProxmoxVM } from '../../models/proxmox-vm.model';

@Component({
  selector: 'app-vm-lab-builder',
  templateUrl: './vm-lab-builder.component.html',
  styleUrl: './vm-lab-builder.component.scss'
})
export class VMLabBuilderComponent implements OnInit {
  vms: ProxmoxVM[] = [];

  constructor(private proxmoxService: ProxmoxService) {

  }

  ngOnInit(): void {
    this.proxmoxService.getVMTemplates().subscribe((vms: ProxmoxVM[]) => {
      this.vms = vms;
    })
  }
}

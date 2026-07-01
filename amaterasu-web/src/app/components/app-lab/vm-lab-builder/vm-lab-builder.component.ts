import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, of, Subject } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';
import { ServerType } from '../../../enums/server-type.enum';
import { ProxmoxVM } from '../../../models/lab/proxmox-vm.model';
import { EditDialogService } from '../../../services/edit-dialog.service';
import { ProxmoxService } from '../../../services/lab/proxmox.service';
import { RemoteServerService } from '../../../services/lab/remote-server.service';
import { NgFor, NgClass, DecimalPipe } from '@angular/common';

@Component({
    selector: 'amaterasu-vm-lab-builder',
    templateUrl: './vm-lab-builder.component.html',
    styleUrl: './vm-lab-builder.component.scss',
    imports: [NgFor, NgClass, DecimalPipe]
})
export class VMLabBuilderComponent implements OnInit, OnDestroy {
  vms: ProxmoxVM[] = [];
  selectedVMs: ProxmoxVM[] = [];

  private destroy$ = new Subject<void>();

  constructor(private proxmoxService: ProxmoxService, private remoteServerService: RemoteServerService,
    private dialog: EditDialogService
  ) { }

  ngOnInit(): void {
    // switchMap cancels an in-flight template fetch when the selected server changes,
    // preventing a slow response for server A from overwriting server B's templates.
    // takeUntil tears everything down on destroy so navigation doesn't leak subscriptions.
    this.remoteServerService.selectedRemoteServer$.pipe(
      switchMap((selectedServer): Observable<ProxmoxVM[]> => {
        if (!selectedServer || selectedServer.serverType !== ServerType.PROXMOX) {
          return of([]);
        }
        return this.proxmoxService.getVMTemplates(selectedServer.id!);
      }),
      takeUntil(this.destroy$)
    ).subscribe((vms: ProxmoxVM[]) => {
      this.vms = vms;
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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

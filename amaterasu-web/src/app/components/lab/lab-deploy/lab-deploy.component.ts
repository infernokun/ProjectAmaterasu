import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { User } from '../../../models/user.model';
import { LabTracker } from '../../../models/lab-tracker.model';
import { LabStatus } from '../../../enums/lab-status.enum';
import { Observable } from 'rxjs';
import { LabTrackerService } from '../../../services/lab-tracker.service';
import { LabRequest } from '../../../models/dto/lab-request.model';
import { EditDialogService } from '../../../services/edit-dialog.service';
import {
  DropDownQuestion,
  ObservableMap,
  SimpleFormData,
} from '../../../models/simple-form-data.model';
import { LabType } from '../../../enums/lab-type.enum';
import { RemoteServer } from '../../../models/remote-server.model';
import { RemoteServerService } from '../../../services/remote-server.service';
import { Lab } from '../../../models/lab.model';
import { getServerType } from '../../../utils/server-lab-type';
import { LabService } from '../../../services/lab.service';
import { ApiResponse } from '../../../models/api-response.model';
import { LabActionResult } from '../../../models/lab-action-result.model';
import { MatDialog } from '@angular/material/dialog';
import { CommonDialogComponent } from '../../common/dialog/common-dialog/common-dialog.component';
import { HttpErrorResponse } from '@angular/common/http';

export class RemoteServerSelectData extends SimpleFormData {
  constructor(observables?: ObservableMap) {
    super('remoteServerSelect');

    this.questions.push(
      new DropDownQuestion({
        label: 'Remote Server',
        key: 'remoteServer',
        options: [],
        asyncData:
          observables && observables['remoteServer']
            ? observables['remoteServer']
            : undefined,
      })
    );
  }
}

@Component({
  selector: 'app-lab-deploy',
  standalone: false,
  templateUrl: './lab-deploy.component.html',
  styleUrl: './lab-deploy.component.scss',
})
export class LabDeployComponent implements OnInit {
  labTrackers: LabTracker[] = [];

  labsLoading: Set<string> = new Set<string>();
  labTrackers$: Observable<LabTracker[] | undefined> | undefined;

  activeLabTrackers: LabTracker[] = [];

  @Input() user: User | undefined;
  @Output() deployLabActionEmitter: EventEmitter<string> = new EventEmitter<string>();
  @Output() deployLabFinishEmitter: EventEmitter<ApiResponse<LabActionResult>> = new EventEmitter<ApiResponse<LabActionResult>>();

  constructor(
    private labTrackerService: LabTrackerService,
    private editDialogService: EditDialogService,
    private remoteServerService: RemoteServerService,
    private labService: LabService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.labTrackerService.getLabTrackersByTeam(this.user?.team?.id!);
    this.labTrackerService.labTrackersByTeam$.subscribe(
      (labTrackers: LabTracker[]) => {
        this.labTrackers = labTrackers;

        this.activeLabTrackers = this.labTrackers.filter(
          (labTracker: LabTracker) => labTracker.labStatus !== LabStatus.DELETED
        );
      }
    );
  }

  deployLab(lab: Lab, user: User): void {
    this.deployLabActionEmitter.emit(lab.id);
    const remoteServers$: Observable<RemoteServer[]> =
      this.remoteServerService.getRemoteServerByServerType(
        getServerType(lab.labType!)
      );

    const remoteServerSelectFormData = new RemoteServerSelectData({
      remoteServer: remoteServers$,
    });

    this.editDialogService
      .openDialog<any>(remoteServerSelectFormData, (response: any) => {
        this.labsLoading.add(lab.id!);

        const teamLabTrackerIds: string[] =
          this.user?.team?.teamActiveLabs ?? [];

        const filteredTrackedLabs: LabTracker[] = this.labTrackers.filter(
          (labTracker: LabTracker) =>
            teamLabTrackerIds.includes(labTracker.id!) &&
            labTracker.labStatus !== LabStatus.DELETED &&
            labTracker.labStarted?.id === lab.id
        );

        const latestLabTracker: LabTracker | undefined =
          filteredTrackedLabs.sort(
            (a, b) =>
              (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
          )[0];

        const labRequest: LabRequest = {
          labId: lab.id,
          userId: user?.id,
          labTrackerId: latestLabTracker?.id || '',
          remoteServerId: response.remoteServer
        };

        this.sendStartRequest(labRequest);
      })
      .subscribe();
  }

  sendStartRequest(labRequest: LabRequest): void {
    this.labService.startLab(labRequest).subscribe({
      next: (response: ApiResponse<LabActionResult>) => {
        if (!response.data) return;

        const newTrackedLab = new LabTracker(response.data.labTracker);

        this.activeLabTrackers.push(newTrackedLab);

        this.user?.setTeam({
          ...this.user.team,
          teamActiveLabs: [...(this.user.team?.teamActiveLabs ?? []), newTrackedLab.id!]
        });

        if (response.data.output) {
          this.dialog.open(CommonDialogComponent, {
            data: {
              title: 'Lab Start',
              content: JSON.stringify(response.data, null, 2),
              isCode: true,
              isReadOnly: true,
              fileType: 'json'
            },
            width: '75rem',
            height: '50rem',
            disableClose: true
          });
        }
        this.deployLabFinishEmitter.emit(response);
      },
      error: (err: HttpErrorResponse) => {
        console.error(`Failed to start lab ${labRequest.labId}:`, err);

        // Ensure proper extraction of LabActionResult from API response
        let errorContent: string;

        try {
          const apiResponse: ApiResponse<LabActionResult> = err.error;
          errorContent = JSON.stringify(apiResponse, null, 2); // Pretty-print JSON
          this.deployLabFinishEmitter.emit(apiResponse);
        } catch (e) {
          errorContent = JSON.stringify({ error: 'Unexpected error format', details: err.message }, null, 2);
        }

        this.dialog.open(CommonDialogComponent, {
          data: {
            title: 'Lab Start',
            content: errorContent,
            isCode: true,
            isReadOnly: true,
            fileType: 'json' // Change to JSON since it's structured data
          },
          width: '75rem',
          height: '50rem',
          disableClose: true
        });

        this.labsLoading.delete(labRequest.labId!);
      },
      complete: () => {
        // Remove the labId from the loadingLabs set
        this.labsLoading.delete(labRequest.labId!);
      },
    })
  }

  formatLabName(name: string): string {
    return name.toLowerCase().replace(/\s+/g, '-');
  }

  isLabLoading(labId?: string): boolean {
    return this.labsLoading.has(labId!);
  }
}

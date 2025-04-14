import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { User } from '../../../models/user.model';
import { LabTracker } from '../../../models/lab-tracker.model';
import { LabStatus } from '../../../enums/lab-status.enum';
import { BehaviorSubject, Observable, of, Subject, takeUntil } from 'rxjs';
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
import { LabDeploymentService } from '../../../services/lab-deployment.service';

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
  private isLoadingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
  private destroy$ = new Subject<void>();

  labTrackers: LabTracker[] = [];

  labsLoading: Set<string> = new Set<string>();
  //labTrackers$: Observable<LabTracker[] | undefined> | undefined;

  isLoading$: Observable<boolean> = this.isLoadingSubject.asObservable();

  console = console;
  LabType = LabType;

  @Input() user: User | undefined;
  @Input() labTrackers$: Observable<LabTracker[]> = of([]);

  constructor(
    private labTrackerService: LabTrackerService,
    private editDialogService: EditDialogService,
    private remoteServerService: RemoteServerService,
    private labService: LabService,
    private dialog: MatDialog,
    private labDeploymentService: LabDeploymentService
  ) {}

  ngOnInit(): void {
    this.isLoadingSubject.next(true);

    this.labDeploymentService.deployLab$
    .pipe(takeUntil(this.destroy$))
    .subscribe((lab: Lab) => {
      this.deployLab(lab);
    });
    
    this.labTrackers$ = this.labTrackerService.labTrackersByTeam$;
    this.labTrackerService.labTrackersByTeam$.subscribe(
      (labTrackers: LabTracker[]) => {
        this.labTrackers = labTrackers;
      }
    );

    this.isLoadingSubject.next(false);
  }

  deployLab(lab: Lab): void {
    const remoteServers$: Observable<RemoteServer[]> =
      this.remoteServerService.getRemoteServerByServerType(
        getServerType(lab.labType!)
      );

    const remoteServerSelectFormData = new RemoteServerSelectData({
      remoteServer: remoteServers$,
    });

    let cancel = true;

    this.editDialogService
      .openDialog<any>(remoteServerSelectFormData, (response: any) => {
        cancel = false;

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
          userId: this.user?.id,
          labTrackerId: latestLabTracker?.id || '',
          remoteServerId: response.remoteServer,
        };

        this.sendStartRequest(labRequest);
      })
      .subscribe((res) => {
        if (cancel) {
          this.console.log(res, "is res ok")
          this.labsLoading.delete(lab.id!);

          this.labDeploymentService.updateLabsLoading(this.labsLoading);
        }
      });
  }

  sendStartRequest(labRequest: LabRequest): void {
    this.labService.startLab(labRequest).subscribe({
      next: (response: ApiResponse<LabActionResult>) => {
        if (!response.data) return;

        const newTrackedLab = new LabTracker(response.data.labTracker);

        this.labTrackers.push(newTrackedLab);
        this.labTrackerService.setLabTrackersByTeam(this.labTrackers);

        if (response.data.output) {
          this.dialog.open(CommonDialogComponent, {
            data: {
              title: 'Lab Start',
              content: JSON.stringify(response.data, null, 2),
              isCode: true,
              isReadOnly: true,
              fileType: 'json',
            },
            width: '75rem',
            height: '50rem',
            disableClose: true,
          });
        }
        this.labDeploymentService.startLabDeploymentFinish(response);
      },
      error: (err: HttpErrorResponse) => {
        console.error(`Failed to start lab ${labRequest.labId}:`, err);

        // Ensure proper extraction of LabActionResult from API response
        let errorContent: string;

        try {
          const apiResponse: ApiResponse<LabActionResult> = err.error;
          errorContent = JSON.stringify(apiResponse, null, 2); // Pretty-print JSON
          this.labDeploymentService.startLabDeploymentFinish(apiResponse);

        } catch (e) {
          errorContent = JSON.stringify(
            { error: 'Unexpected error format', details: err.message },
            null,
            2
          );
        }

        this.dialog.open(CommonDialogComponent, {
          data: {
            title: 'Lab Start',
            content: errorContent,
            isCode: true,
            isReadOnly: true,
            fileType: 'json', // Change to JSON since it's structured data
          },
          width: '75rem',
          height: '50rem',
          disableClose: true,
        });

        this.labsLoading.delete(labRequest.labId!);
        this.labDeploymentService.updateLabsLoading(this.labsLoading);
      },
      complete: () => {
        // Remove the labId from the loadingLabs set
        this.labsLoading.delete(labRequest.labId!);
        this.labDeploymentService.updateLabsLoading(this.labsLoading);
      },
    });
  }

  redeployLab(labTracker: LabTracker) {
    this.labsLoading.add(labTracker.id!);
    this.labDeploymentService.updateLabsLoading(this.labsLoading);

    const labRequest: LabRequest = {
      labId: labTracker.labStarted?.id,
      userId: this.user?.id,
      labTrackerId: labTracker.id,
      remoteServerId: labTracker.remoteServer?.id,
    };

    this.labService.startLab(labRequest).subscribe({
      next: (response: ApiResponse<LabActionResult>) => {
        if (!response.data) return;

        const updatedLabTracker = new LabTracker(response.data.labTracker);
        const index = this.labTrackers.indexOf(labTracker);

        this.labTrackers[index] = updatedLabTracker;

        this.labTrackerService.setLabTrackersByTeam(this.labTrackers);

        if (response.data.output) {
          this.dialog.open(CommonDialogComponent, {
            data: {
              title: 'Lab Start',
              content: JSON.stringify(response.data, null, 2),
              isCode: true,
              isReadOnly: true,
              fileType: 'json',
            },
            width: '75rem',
            height: '50rem',
            disableClose: true,
          });
        }
        this.labDeploymentService.startLabDeploymentFinish(response);
      },
      error: (err: HttpErrorResponse) => {
        console.error(`Failed to start lab ${labRequest.labId}:`, err);

        // Ensure proper extraction of LabActionResult from API response
        let errorContent: string;

        try {
          const apiResponse: ApiResponse<LabActionResult> = err.error;
          errorContent = JSON.stringify(apiResponse, null, 2); // Pretty-print JSON
          this.labDeploymentService.startLabDeploymentFinish(apiResponse);

        } catch (e) {
          errorContent = JSON.stringify(
            { error: 'Unexpected error format', details: err.message },
            null,
            2
          );
        }

        this.dialog.open(CommonDialogComponent, {
          data: {
            title: 'Lab Start',
            content: errorContent,
            isCode: true,
            isReadOnly: true,
            fileType: 'json', // Change to JSON since it's structured data
          },
          width: '75rem',
          height: '50rem',
          disableClose: true,
        });

        this.labsLoading.delete(labTracker.id!);
        this.labDeploymentService.updateLabsLoading(this.labsLoading);
      },
      complete: () => {
        // Remove the labId from the loadingLabs set
        this.labsLoading.delete(labTracker.id!);
        this.labDeploymentService.updateLabsLoading(this.labsLoading);
      },
    });
  }

  stopLab(labTracker: LabTracker) {
    this.labsLoading.add(labTracker.id!);
    this.labDeploymentService.updateLabsLoading(this.labsLoading);

    const labRequest: LabRequest = {
      labId: labTracker.labStarted?.id,
      userId: this.user?.id,
      labTrackerId: labTracker.id,
      remoteServerId: labTracker.remoteServer?.id,
    };

    this.labService.stopLab(labRequest).subscribe({
      next: (response: ApiResponse<LabActionResult | undefined>) => {
        if (!response.data) return;

        const stoppedLabTracker = new LabTracker(response.data.labTracker);
        const index = this.labTrackers.indexOf(labTracker);

        this.labTrackers[index] = stoppedLabTracker;

        this.labTrackerService.setLabTrackersByTeam(this.labTrackers);
        if (response.data.output) {
          this.dialog.open(CommonDialogComponent, {
            data: {
              title: 'Lab Start',
              content: response.data.output,
              isCode: true,
              isReadOnly: true,
              fileType: 'bash',
            },
            width: '75rem',
            height: '50rem',
            disableClose: true,
          });
        }
      },
      error: (err) => {
        console.error(`Failed to stop lab ${labTracker.labStarted?.id!}:`, err);
        this.labsLoading.delete(labTracker.id!);
      },
      complete: () => {
        this.labsLoading.delete(labTracker.id!);
      },
    });
  }

  deleteLab(labTracker: LabTracker) {
    this.labsLoading.add(labTracker.id!);

    const labRequest: LabRequest = {
      labId: labTracker.labStarted?.id,
      userId: this.user?.id,
      labTrackerId: labTracker.id,
      remoteServerId: labTracker.remoteServer?.id,
    };

    this.labService.deleteLab(labRequest).subscribe({
      next: (response: ApiResponse<LabActionResult | undefined>) => {
        if (!response.data) return;

        const deletedLabTracker = new LabTracker(response.data.labTracker);

        if (response.data.output) {
          this.dialog.open(CommonDialogComponent, {
            data: {
              title: 'Lab Start',
              content: response.data.output,
              isCode: true,
              isReadOnly: true,
              fileType: 'bash',
            },
            width: '75rem',
            height: '50rem',
            disableClose: true,
          });
        }

        const index = this.labTrackers.findIndex(
          (tracker) => tracker.id === deletedLabTracker.id
        );
        if (index !== -1) {
          this.labTrackers = this.labTrackers.filter(tracker => tracker != labTracker);
          this.labTrackerService.setLabTrackersByTeam(this.labTrackers);
        } else {
          console.warn(
            `Lab tracker not found in trackedLabs: ${deletedLabTracker.id}`
          );
        }
      },
      error: (err) => {
        console.error(`Failed to delete lab ${labTracker.labStarted?.id}:`, err);
        this.labsLoading.delete(labTracker.id!);
      },
      complete: () => {
        this.labsLoading.delete(labTracker.id!);
      },
    });
  }

  getSettings(labTracker: LabTracker) {
    this.labService.getSettings(labTracker.labStarted?.id!, labTracker.remoteServer?.id!).subscribe((res: ApiResponse<any>) => {
      if (!res.data || !res.data.yml) {
        console.error('No YAML data found in response!');
        return;
      }
      
      const dockerComposeData = res.data;
      this.dialog.open(CommonDialogComponent, {
        data: {
          title: 'Lab Output',
          isCode: true,
          content: dockerComposeData.yml,
          fileType: 'yaml',
          isReadOnly: false,
        },
        width: '50rem',
        height: '50rem',
      });
    });
  }

  viewLogs(arg0: string | undefined) {
    throw new Error('Method not implemented.');
  }
  formatLabName(name: string): string {
    return name.toLowerCase().replace(/\s+/g, '-');
  }

  isLabLoading(labId?: string): boolean {
    return this.labsLoading.has(labId!);
  }

  formatDate(date: Date): string {
    if (!date) return '';
    const options: Intl.DateTimeFormatOptions = {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
    };

    return date.toLocaleString('en-US', options).replace(
      /,/g,
      (
        (count = 0) =>
        (match: any) => {
          count++;
          return count === 2 ? ' @' : match;
        }
      )()
    );
  }
}

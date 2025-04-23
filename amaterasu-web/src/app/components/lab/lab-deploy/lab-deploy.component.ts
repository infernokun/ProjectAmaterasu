import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { User } from '../../../models/user.model';
import { LabTracker } from '../../../models/lab-tracker.model';
import { LabStatus } from '../../../enums/lab-status.enum';
import { BehaviorSubject, catchError, finalize, Observable, of, Subject, takeUntil } from 'rxjs';
import { LabTrackerService } from '../../../services/lab-tracker.service';
import { LabRequest } from '../../../models/dto/lab-request.model';
import { EditDialogService } from '../../../services/edit-dialog.service';
import { LabType } from '../../../enums/lab-type.enum';
import { RemoteServer, RemoteServerSelectData } from '../../../models/remote-server.model';
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
import { DateUtils } from '../../../utils/date-utils';
import { FADE_ANIMATION } from '../../../utils/animations';

@Component({
  selector: 'app-lab-deploy',
  standalone: false,
  templateUrl: './lab-deploy.component.html',
  styleUrl: './lab-deploy.component.scss',
  animations: [FADE_ANIMATION],
})
export class LabDeployComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly isLoading$ = new BehaviorSubject<boolean>(false);

  readonly isLoadingState$ = this.isLoading$.asObservable();

  labTrackers: LabTracker[] = [];
  labsLoading: Set<string> = new Set<string>();
  
  // Enums exposed to template
  readonly LabType = LabType;
  readonly LabStatus = LabStatus;

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
    this.initializeComponent();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeComponent(): void {
    this.isLoading$.next(true);

    // Subscribe to lab deployment service
    this.labDeploymentService.deployLab$
      .pipe(takeUntil(this.destroy$))
      .subscribe((lab: Lab) => {
        this.deployLab(lab);
      });
    
    // Subscribe to lab trackers from service
    this.labTrackers$ = this.labTrackerService.labTrackersByTeam$;
    this.labTrackerService.labTrackersByTeam$
      .pipe(takeUntil(this.destroy$))
      .subscribe((labTrackers: LabTracker[]) => {
        this.labTrackers = labTrackers;
      });

    this.isLoading$.next(false);
  }

  deployLab(lab: Lab): void {
    if (!lab.id) {
      console.error('Cannot deploy lab without ID');
      return;
    }

    // Mark lab as loading
    this.labsLoading.add(lab.id);
    this.labDeploymentService.updateLabsLoading(this.labsLoading);

    const remoteServers$: Observable<RemoteServer[]> =
      this.remoteServerService.getRemoteServerByServerType(
        getServerType(lab.labType!)
      );

    const remoteServerSelectFormData = new RemoteServerSelectData({
      remoteServer: remoteServers$,
    });

    let dialogCancelled = true;

    this.editDialogService
      .openDialog<any>(remoteServerSelectFormData, (response: any) => {
        if (!response?.remoteServer) {
          console.error('No remote server selected');
          return;
        }

        dialogCancelled = false;
        const labRequest = this.createLabRequest(lab, response.remoteServer);
        this.sendStartRequest(labRequest);
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (dialogCancelled && lab.id) {
          this.labsLoading.delete(lab.id);
          this.labDeploymentService.updateLabsLoading(this.labsLoading);
        }
      });
  }

  private createLabRequest(lab: Lab, remoteServerId: string): LabRequest {
    const teamLabTrackerIds: string[] = this.user?.team?.teamActiveLabs ?? [];

    // Find the most recent lab tracker for this lab
    const filteredTrackedLabs: LabTracker[] = this.labTrackers.filter(
      (labTracker: LabTracker) =>
        teamLabTrackerIds.includes(labTracker.id!) &&
        labTracker.labStatus !== LabStatus.DELETED &&
        labTracker.labStarted?.id === lab.id);

    const latestLabTracker: LabTracker | undefined =
      filteredTrackedLabs.sort(
        (a, b) =>
          (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
      )[0];

    return {
      labId: lab.id,
      userId: this.user?.id,
      labTrackerId: latestLabTracker?.id || '',
      remoteServerId: remoteServerId,
    };
  }

  sendStartRequest(labRequest: LabRequest): void {
    if (!labRequest.labId) {
      console.error('Cannot start lab without labId');
      return;
    }
    
    this.labService.startLab(labRequest)
      .pipe(
        takeUntil(this.destroy$),
        catchError((err: HttpErrorResponse) => this.handleLabActionError(err, labRequest.labId!)),
        finalize(() => {
          if (labRequest.labId) {
            this.labsLoading.delete(labRequest.labId);
            this.labDeploymentService.updateLabsLoading(this.labsLoading);
          }
        })
      )
      .subscribe((response: ApiResponse<LabActionResult>) => {
        this.handleLabActionResponse(response);
      });
  }

  redeployLab(labTracker: LabTracker): void {
    if (!labTracker.id || !labTracker.labStarted?.id) {
      console.error('Cannot redeploy lab without required IDs');
      return;
    }
    
    this.labsLoading.add(labTracker.id);
    this.labDeploymentService.updateLabsLoading(this.labsLoading);

    const labRequest: LabRequest = {
      labId: labTracker.labStarted.id,
      userId: this.user?.id,
      labTrackerId: labTracker.id,
      remoteServerId: labTracker.remoteServer?.id,
    };

    this.labService.startLab(labRequest)
      .pipe(
        takeUntil(this.destroy$),
        catchError((err: HttpErrorResponse) => this.handleLabActionError(err, labTracker.id!)),
        finalize(() => {
          if (labTracker.id) {
            this.labsLoading.delete(labTracker.id);
            this.labDeploymentService.updateLabsLoading(this.labsLoading);
          }
        })
      )
      .subscribe((response: ApiResponse<LabActionResult>) => {
        this.handleLabRedeployResponse(response, labTracker);
      });
  }

  stopLab(labTracker: LabTracker): void {
    if (!labTracker.id || !labTracker.labStarted?.id) {
      console.error('Cannot stop lab without required IDs');
      return;
    }
    
    this.labsLoading.add(labTracker.id);
    this.labDeploymentService.updateLabsLoading(this.labsLoading);

    const labRequest: LabRequest = {
      labId: labTracker.labStarted.id,
      userId: this.user?.id,
      labTrackerId: labTracker.id,
      remoteServerId: labTracker.remoteServer?.id,
    };

    this.labService.stopLab(labRequest)
      .pipe(
        takeUntil(this.destroy$),
        catchError((err: HttpErrorResponse) => {
          console.error(`Failed to stop lab ${labTracker.labStarted?.id}:`, err);
          return of({ code: 404, data: {}, message: 'Failed to stop lab' });
        }),
        finalize(() => {
          if (labTracker.id) {
            this.labsLoading.delete(labTracker.id);
            this.labDeploymentService.updateLabsLoading(this.labsLoading);
          }
        })
      )
      .subscribe((response: ApiResponse<LabActionResult>) => {
        if (!response.data) return;

        const stoppedLabTracker = new LabTracker(response.data.labTracker);
        this.updateLabTrackerInArray(labTracker, stoppedLabTracker);

        if (response.data.output) {
          this.showOutputDialog('Lab Stop', response.data.output, 'bash');
        }
      });
  } 

  deleteLab(labTracker: LabTracker): void {
    if (!labTracker.id || !labTracker.labStarted?.id) {
      console.error('Cannot delete lab without required IDs');
      return;
    }
    
    this.labsLoading.add(labTracker.id);
    this.labDeploymentService.updateLabsLoading(this.labsLoading);

    const labRequest: LabRequest = {
      labId: labTracker.labStarted.id,
      userId: this.user?.id,
      labTrackerId: labTracker.id,
      remoteServerId: labTracker.remoteServer?.id,
    };

    this.labService.deleteLab(labRequest)
      .pipe(
        takeUntil(this.destroy$),
        catchError((err: HttpErrorResponse) => {
          console.error(`Failed to delete lab ${labTracker.labStarted?.id}:`, err);
          return of({ code: 404, data: {}, message: 'Failed to delete lab' });
        }),
        finalize(() => {
          if (labTracker.id) {
            this.labsLoading.delete(labTracker.id);
            this.labDeploymentService.updateLabsLoading(this.labsLoading);
          }
        })
      )
      .subscribe((response: ApiResponse<LabActionResult>) => {
        if (!response.data) return;

        if (response.data.output) {
          this.showOutputDialog('Lab Delete', response.data.output, 'bash');
        }

        // Remove the lab tracker from the array
        this.labTrackers = this.labTrackers.filter(tracker => tracker.id !== labTracker.id);
        this.labTrackerService.setLabTrackersByTeam(this.labTrackers);
      });
  }

  getSettings(labTracker: LabTracker): void {
    if (!labTracker.id || !labTracker.remoteServer?.id) {
      console.error('Cannot get settings without required IDs');
      return;
    }
    
    this.labTrackerService.getSettings(labTracker.id, labTracker.remoteServer.id)
      .pipe(
        takeUntil(this.destroy$),
        catchError(error => {
          console.error('Failed to get lab settings:', error);
          return of({ code: 404, data: {}, message: 'Failed to fetch settings' });
        })
      )
      .subscribe((res: ApiResponse<any>) => {
        if (!res.data || !res.data.yml) {
          console.error('No YAML data found in response!');
          return;
        }
        
        this.showOutputDialog('Lab Settings', res.data.yml, 'yaml', false);
      });
  }

  getLogs(labTracker: LabTracker): void {
    if (!labTracker.id || !labTracker.remoteServer?.id) {
      console.error('Cannot get settings without required IDs');
      return;
    }
    
    this.labTrackerService.getLogs(labTracker.id, labTracker.remoteServer.id)
      .pipe(
        takeUntil(this.destroy$),
        catchError(error => {
          console.error('Failed to get lab logs:', error);
          return of({ code: 404, data: {}, message: 'Failed to fetch logs' });
        })
      )
      .subscribe((res: ApiResponse<any>) => {

        if (!res.data) {
          console.error('No data found in response!');
          return;
        }

        
        this.showOutputDialog('Lab Settings', res.data, 'bash', false);
      });
  }

  private showOutputDialog(title: string, content: string | object, fileType: string, isReadOnly: boolean = true): void {
    const dialogContent = typeof content === 'object' ? JSON.stringify(content, null, 2) : content;
    
    this.dialog.open(CommonDialogComponent, {
      data: {
        title: title,
        content: dialogContent,
        isCode: true,
        isReadOnly: isReadOnly,
        fileType: fileType,
      },
      width: '75rem',
      height: '50rem',
      disableClose: true,
    });
  }

  private handleLabActionResponse(response: ApiResponse<LabActionResult>): void {
    if (!response.data) return;

    const newTrackedLab = new LabTracker(response.data.labTracker);
    this.labTrackers.push(newTrackedLab);
    this.labTrackerService.setLabTrackersByTeam(this.labTrackers);

    if (response.data.output) {
      this.showOutputDialog('Lab Start', JSON.stringify(response.data, null, 2), 'json');
    }
    
    this.labDeploymentService.startLabDeploymentFinish(response);
  }

  private handleLabRedeployResponse(response: ApiResponse<LabActionResult>, originalLabTracker: LabTracker): void {
    if (!response.data) return;

    const updatedLabTracker = new LabTracker(response.data.labTracker);
    this.updateLabTrackerInArray(originalLabTracker, updatedLabTracker);

    if (response.data.output) {
      this.showOutputDialog('Lab Start', JSON.stringify(response.data, null, 2), 'json');
    }
    
    this.labDeploymentService.startLabDeploymentFinish(response);
  }

  private updateLabTrackerInArray(original: LabTracker, updated: LabTracker): void {
    const index = this.labTrackers.findIndex(tracker => tracker.id === original.id);
    
    if (index !== -1) {
      this.labTrackers = [
        ...this.labTrackers.slice(0, index),
        updated,
        ...this.labTrackers.slice(index + 1)
      ];
      this.labTrackerService.setLabTrackersByTeam(this.labTrackers);
    } else {
      console.warn(`Lab tracker not found in trackedLabs: ${original.id}`);
    }
  }

  private handleLabActionError(err: HttpErrorResponse, labId: string): Observable<ApiResponse<LabActionResult>> {
    console.error(`Failed to process lab ${labId}:`, err);

    let errorContent: string;
    let apiResponse: ApiResponse<LabActionResult>;

    try {
      apiResponse = err.error;
      errorContent = JSON.stringify(apiResponse, null, 2);
      this.labDeploymentService.startLabDeploymentFinish(apiResponse);
    } catch (e) {
      errorContent = JSON.stringify(
        { error: 'Unexpected error format', details: err.message },
        null, 
        2
      );
      apiResponse = { 
        code: 404, 
        data: {}, 
        message: 'Failed to process lab action'
      };
    }
    return of(apiResponse);
  }

  viewLogs(id: string | undefined): void {
    console.warn('viewLogs method not implemented yet');
  }

  formatLabName(name: string): string {
    return name.toLowerCase().replace(/\s+/g, '-');
  }

  isLabLoading(labId?: string): boolean {
    return !!labId && this.labsLoading.has(labId);
  }

  formatDate(date: Date): string {
    if (!date) return '';
    return DateUtils.formatDateWithTime(date);
  }
}

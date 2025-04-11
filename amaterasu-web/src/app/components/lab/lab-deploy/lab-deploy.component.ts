import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { User } from '../../../models/user.model';
import { LabTracker } from '../../../models/lab-tracker.model';
import { LabStatus } from '../../../enums/lab-status.enum';
import { BehaviorSubject, Observable, of } from 'rxjs';
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
  private isLoadingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);

  labTrackers: LabTracker[] = [];

  labsLoading: Set<string> = new Set<string>();
  //labTrackers$: Observable<LabTracker[] | undefined> | undefined;

  activeLabTrackers: LabTracker[] = [];

  isLoading$: Observable<boolean> = this.isLoadingSubject.asObservable();


  console = console;
  LabType = LabType;

  @Input() user: User | undefined;
  @Input() labTrackers$: Observable<LabTracker[]> = of([]);
  @Output() deployLabActionEmitter: EventEmitter<string> =
    new EventEmitter<string>();
  @Output() deployLabFinishEmitter: EventEmitter<ApiResponse<LabActionResult>> =
    new EventEmitter<ApiResponse<LabActionResult>>();

  constructor(
    private labTrackerService: LabTrackerService,
    private editDialogService: EditDialogService,
    private remoteServerService: RemoteServerService,
    private labService: LabService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    /*this.isLoadingSubject.next(true);
    this.labTrackerService.getLabTrackersByTeam(this.user?.team?.id!);
    this.labTrackerService.labTrackersByTeam$.subscribe(
      (labTrackers: LabTracker[]) => {
        this.labTrackers = labTrackers;

        this.activeLabTrackers = this.labTrackers.filter(
          (labTracker: LabTracker) => labTracker.labStatus !== LabStatus.DELETED
        );
      }
    );)

    this.isLoadingSubject.next(false);*/

    this.labTrackerService.labTrackersByTeam$.subscribe((labTrackers: LabTracker[]) => {
      this.labTrackers = labTrackers;
      this.activeLabTrackers = labTrackers;
      this.console.log(this.labTrackers);
    })
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
          remoteServerId: response.remoteServer,
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
          teamActiveLabs: [
            ...(this.user.team?.teamActiveLabs ?? []),
            newTrackedLab.id!,
          ],
        });

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
      },
      complete: () => {
        // Remove the labId from the loadingLabs set
        this.labsLoading.delete(labRequest.labId!);
      },
    });
  }

  deleteLab(arg0: string | undefined) {
    throw new Error('Method not implemented.');
  }
  getSettings(arg0: string | undefined, arg1: User | undefined) {
    throw new Error('Method not implemented.');
  }
  viewLogs(arg0: string | undefined) {
    throw new Error('Method not implemented.');
  }
  stopLab(arg0: any, arg1: any) {
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

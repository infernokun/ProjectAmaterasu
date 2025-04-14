import { trigger, transition, style, animate } from '@angular/animations';
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Lab } from '../../../models/lab.model';
import {
  BehaviorSubject,
  combineLatest,
  distinctUntilChanged,
  filter,
  Observable,
  of,
  switchMap,
  take,
} from 'rxjs';
import { LabType } from '../../../enums/lab-type.enum';
import { User } from '../../../models/user.model';
import { LabTracker } from '../../../models/lab-tracker.model';
import { LabTrackerService } from '../../../services/lab-tracker.service';
import { LabService } from '../../../services/lab.service';
import { AuthService } from '../../../services/auth.service';
import { Team } from '../../../models/team.model';
import { TeamService } from '../../../services/team.service';
import { MatDialog } from '@angular/material/dialog';
import { RemoteServerService } from '../../../services/remote-server.service';
import { ApiResponse } from '../../../models/api-response.model';
import { LabActionResult } from '../../../models/lab-action-result.model';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabDeploymentService } from '../../../services/lab-deployment.service';

@Component({
  selector: 'app-lab-main',
  standalone: false,
  templateUrl: './lab-main.component.html',
  styleUrl: './lab-main.component.scss',
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(10px)' }),
        animate(
          '300ms ease-out',
          style({ opacity: 1, transform: 'translateY(0)' })
        ),
      ]),
    ]),
  ],
})
export class LabMainComponent implements OnInit {
  private loggedInUserSubject: BehaviorSubject<User | undefined> =
    new BehaviorSubject<User | undefined>(undefined);
  private userTeamSubject: BehaviorSubject<Team | undefined> =
    new BehaviorSubject<Team | undefined>(undefined);
  private isLoadingSubject: BehaviorSubject<boolean> =
    new BehaviorSubject<boolean>(false);
  labsLoading = new Set<string>();

  isLoading$: Observable<boolean> = this.isLoadingSubject.asObservable();
  labTrackers$: Observable<LabTracker[] | undefined> | undefined;

  labs: Lab[] = [];
  labTrackers: LabTracker[] = [];
  team: Team | undefined;

  labs$: Observable<Lab[] | undefined> | undefined;
  loggedInUser: User | undefined;

  isAlreadyDeployedByTeam: boolean = false;
  isHovered: boolean = false;

  LabType = LabType;

  @Output() deployLabStartEmitter: EventEmitter<Lab> = new EventEmitter<Lab>();
  @Input() user: User | undefined;

  constructor(
    private labService: LabService,
    private teamService: TeamService,
    private labTrackerService: LabTrackerService,
    private dialog: MatDialog,
    private remoteServerService: RemoteServerService,
    private authService: AuthService,
    private labDeploymentService: LabDeploymentService
  ) {}

  ngOnInit(): void {
    this.isLoadingSubject.next(true);

    this.labs$ = this.labService.labs$;

    this.labTrackerService.labTrackersByTeam$.subscribe(
      (labTrackers: LabTracker[]) => {
        this.labTrackers = labTrackers;
      }
    );
  }

  onMouseEnter(): void {
    this.isHovered = true;
  }

  onMouseLeave(): void {
    this.isHovered = false;
  }

  isInTrackedLabs(labId?: string): boolean {
    if (!labId) return false; // Early return if labId is not provided

    // Check if the labId exists in trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    return this.labTrackers.some(
      (labTracker: LabTracker) =>
        this.user?.team?.teamActiveLabs!.includes(labTracker.id!) &&
        labTracker.labStatus !== LabStatus.DELETED &&
        labTracker.labStarted?.id === labId
    );
  }

  deployLabAction(labId: string) {
    this.labsLoading.add(labId);
  }

  deployLabFinish(response: ApiResponse<LabActionResult>) {
    this.labsLoading.delete(response.data.labTracker?.labStarted?.id!);

    console.log('deployLabFinish', response);

    if (response.data.labTracker?.id) {
      this.labTrackers.push(response.data.labTracker!);
    }
  }

  deployLabStart(lab: Lab): void {
    this.deployLabStartEmitter.emit(lab);
    //this.labDeploymentService.startLabDeployment(lab);
    /*  if (!labId) return; // Early return if labId is not provided

    this.loadingLabs.add(labId);

    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];

    // Filter and sort trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    const filteredTrackedLabs: LabTracker[] = this.trackedLabs.filter(
      (tracker) =>
        teamLabTrackerIds.includes(tracker.id!) &&
        tracker.labStatus !== LabStatus.DELETED &&
        tracker.labStarted?.id === labId
    );

    const latestLabTracker: LabTracker | undefined = filteredTrackedLabs.sort(
      (a, b) => (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
    )[0];

    const labRequest: LabRequest = {
      labId: labId,
      userId: this.loggedInUser?.id,
      labTrackerId: latestLabTracker?.id || '',
      remoteServerId: this.remoteServerService.getSelectedRemoteServer().id,
    };

    this.labService.startLab(labRequest).subscribe({
      next: (response: ApiResponse<LabActionResult | undefined>) => {
        if (!response.data) return;

        const newTrackedLab = new LabTracker(response.data.labTracker);

        if (latestLabTracker) {
          const index = this.trackedLabs.indexOf(latestLabTracker);
          // Handle existing lab tracker
          if (latestLabTracker.labStatus === LabStatus.DELETED) {
            this.trackedLabs.splice(index, 1);
            this.trackedLabs.push(newTrackedLab);
          } else if (
            latestLabTracker.labStatus === LabStatus.STOPPED ||
            latestLabTracker.labStatus === LabStatus.FAILED
          ) {
            this.trackedLabs[index] = newTrackedLab;
          }
        } else {
          // Create a new lab tracker
          this.trackedLabs.push(newTrackedLab);
        }

        // Update team active labs without mutating the original array
        this.team = {
          ...this.team,
          teamActiveLabs: [
            ...(this.team?.teamActiveLabs ?? []),
            newTrackedLab.id!,
          ],
        };

        // Emit updated subjects
        this.userTeamSubject.next({ ...this.team });
        this.labTrackerService.setLabTrackers([...this.trackedLabs]);

        if (response.data.output) {
          this.dialog.open(DialogComponent, {
            data: {
              title: 'Lab Start',
              content: JSON.stringify(response.data.output),
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
      error: (err: HttpErrorResponse) => {
        console.error(`Failed to start lab ${labId}:`, err);

        // Ensure proper extraction of LabActionResult from API response
        let errorContent: string;

        try {
          const apiResponse: ApiResponse<LabActionResult> = err.error;
          errorContent = JSON.stringify(apiResponse.data, null, 2); // Pretty-print JSON
        } catch (e) {
          errorContent = JSON.stringify(
            { error: 'Unexpected error format', details: err.message },
            null,
            2
          );
        }

        this.dialog.open(DialogComponent, {
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

        this.loadingLabs.delete(labId);
      },
      complete: () => {
        // Remove the labId from the loadingLabs set
        this.loadingLabs.delete(labId);
      },
    });*/
  }
}

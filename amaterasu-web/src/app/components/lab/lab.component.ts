import { Component, OnInit } from '@angular/core';
import { LabService } from '../../services/lab.service';
import { Lab, LabDTO, LabFormData } from '../../models/lab.model';
import { UserService } from '../../services/user.service';
import { Observable, BehaviorSubject, switchMap, combineLatest, of, distinctUntilChanged, take, filter } from 'rxjs';
import { User } from '../../models/user.model';
import { ApiResponse } from '../../models/api-response.model';
import { LabTrackerService } from '../../services/lab-tracker.service';
import { LabTracker } from '../../models/lab-tracker.model';
import { TeamService } from '../../services/team.service';
import { Team } from '../../models/team.model';
import { LabStatus } from '../../enums/lab-status.enum';
import { LabActionResult } from '../../models/lab-action-result.model';
import { MatDialog } from '@angular/material/dialog';
import { DialogComponent } from '../common/dialog/dialog.component';
import { EditDialogService } from '../../services/edit-dialog.service';
import { LabType } from '../../enums/lab-type.enum';
import { ProxmoxService } from '../../services/proxmox.service';
import { RemoteServer } from '../../models/remote-server.model';
import { RemoteServerService } from '../../services/remote-server.service';
import { FormControl } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { LabRequest } from '../../models/dto/lab-request.model';
import { HttpErrorResponse } from '@angular/common/http';
import { trigger, transition, style, animate } from '@angular/animations';

@Component({
    selector: 'app-lab',
    templateUrl: './lab.component.html',
    styleUrl: './lab.component.scss',
    animations: [
        trigger('fadeIn', [
            transition(':enter', [
                style({ opacity: 0, transform: 'translateY(10px)' }),
                animate('300ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
            ])
        ])
    ],
    standalone: false
})
export class LabComponent implements OnInit {
  private loggedInUserSubject: BehaviorSubject<User | undefined> = new BehaviorSubject<User | undefined>(undefined);
  private userTeamSubject: BehaviorSubject<Team | undefined> = new BehaviorSubject<Team | undefined>(undefined);
  private isLoadingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
  private loadingLabs = new Set<string>();

  labs: Lab[] = [];
  team: Team | undefined;
  trackedLabs: LabTracker[] = [];
  loggedInUser: User | undefined;

  remoteServerControl: FormControl = new FormControl('');

  isHovered: boolean = false;
  busy: boolean = false;
  dockerComposeData: any;

  LabType = LabType;

  labs$: Observable<Lab[] | undefined> | undefined;
  loggedInUser$: Observable<User | undefined> = this.loggedInUserSubject.asObservable();
  trackedLabs$: Observable<LabTracker[] | undefined> | undefined;
  userTeam$: Observable<Team | undefined> = this.userTeamSubject.asObservable();
  isLoading$: Observable<boolean> = this.isLoadingSubject.asObservable();

  constructor(
    private labService: LabService, private userService: UserService,
    private teamService: TeamService, private labTrackerService: LabTrackerService,
    private dialog: MatDialog, private editDialogService: EditDialogService,
    private proxmoxService: ProxmoxService, private remoteServerService: RemoteServerService, private authService: AuthService) { }

  ngOnInit(): void {
    this.isLoadingSubject.next(true);

    this.labService.fetchLabs();
    this.labTrackerService.fetchLabTrackers();

    combineLatest([
      this.labService.labs$,
      this.authService.user$,
      this.labTrackerService.labTrackers$
    ]).pipe(
      // Make sure user exists and labs and labsTracked are arrays (empty arrays are valid)
      filter(([labs, user, trackedLabs]) =>
        !!user && Array.isArray(labs) && Array.isArray(trackedLabs)
      ),
      // Only react when the team ID changes, not on every emission
      distinctUntilChanged(([_, prevUser, __], [___, currUser, ____]) =>
        prevUser?.team?.id === currUser?.team?.id
      ),
      switchMap(([labs, user, labsTracked]) => {
        // Store values locally
        this.labs = labs!;
        this.labs$ = this.labService.labs$;
        this.loggedInUser = user;
        this.loggedInUserSubject.next(this.loggedInUser);

        // Handle empty labsTracked array as a valid case
        this.trackedLabs = labsTracked!;
        this.trackedLabs$ = this.labTrackerService.labTrackers$;

        const teamId = user?.team?.id;
        if (!teamId) {
          console.error("Logged in user does not have a team.");
          return of(null);
        }

        // Check if we already have the team data with this ID
        if (this.team && this.team.id === teamId) {
          return of(this.team); // Reuse existing team data
        }

        // Only fetch team data if necessary
        return this.teamService.getTeamById(teamId).pipe(take(1));
      })
    ).subscribe({
      next: (team) => {
        if (team) {
          this.team = team;
          this.userTeamSubject.next(this.team);
        } else {
          console.warn("No team data available.");
        }

        this.isLoadingSubject.next(false);
      },
      error: (err) => {
        console.error("Error in data loading process:", err);
        this.isLoadingSubject.next(false);
      }
    });
  }

  addLab(): void {
    const labFormData: LabFormData = new LabFormData();
    const vmTemplates = this.proxmoxService.getVMTemplates.bind(this.proxmoxService);
    const remoteServers$: Observable<RemoteServer[]> = this.remoteServerService.getAllServers();

    const labFormDataWithVMs = new LabFormData(
      (k: any, v: any) => { },
      {
        'remoteServer': remoteServers$,
        //'vms': vmTemplates$
      },
      {
        'vms': vmTemplates
      }
    );

    this.editDialogService.openDialog<Lab>(labFormDataWithVMs, (labDTO: LabDTO) => {
      if (!labDTO) return;
      this.busy = true;

      labDTO = new LabDTO(labDTO);
      labDTO.createdBy = this.loggedInUser?.username;

      this.labService.createNewLab(labDTO, this.remoteServerService.getSelectedRemoteServer().id!).subscribe((labResp: ApiResponse<Lab>) => {
        this.busy = false;

        if (!labResp.data) return;
        this.labs.push(new Lab(labResp.data));
      });
    }).subscribe((res: any) => {

    });
  }

  startLab(labId?: string, user?: User): void {
    if (!labId) return; // Early return if labId is not provided

    this.loadingLabs.add(labId);

    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];

    // Filter and sort trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    const filteredTrackedLabs: LabTracker[] = this.trackedLabs.filter(
      tracker => teamLabTrackerIds.includes(tracker.id!) &&
        tracker.labStatus !== LabStatus.DELETED && tracker.labStarted?.id === labId
    );

    const latestLabTracker: LabTracker | undefined = filteredTrackedLabs.sort((a, b) =>
      (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
    )[0];

    const labRequest: LabRequest = {
      labId: labId,
      userId: user?.id,
      labTrackerId: latestLabTracker?.id || "",
      remoteServerId: this.remoteServerService.getSelectedRemoteServer().id
    }

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
          } else if (latestLabTracker.labStatus === LabStatus.STOPPED || latestLabTracker.labStatus === LabStatus.FAILED) {
            this.trackedLabs[index] = newTrackedLab;
          }
        } else {
          // Create a new lab tracker
          this.trackedLabs.push(newTrackedLab);
        }

        // Update team active labs without mutating the original array
        this.team = {
          ...this.team,
          teamActiveLabs: [...(this.team?.teamActiveLabs ?? []), newTrackedLab.id!]
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
              fileType: 'bash'
            },
            width: '75rem',
            height: '50rem',
            disableClose: true
          });
        }
      },
      error: (err: HttpErrorResponse) => {
        console.error(`Failed to start lab ${labId}:`, err);

        // Ensure proper extraction of LabActionResult from API response
        let errorContent: string;

        try {
          const apiResponse: ApiResponse<LabActionResult> = err.error;
          errorContent = JSON.stringify(apiResponse, null, 2); // Pretty-print JSON
        } catch (e) {
          errorContent = JSON.stringify({ error: 'Unexpected error format', details: err.message }, null, 2);
        }

        this.dialog.open(DialogComponent, {
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

        this.loadingLabs.delete(labId);
      },
      complete: () => {
        // Remove the labId from the loadingLabs set
        this.loadingLabs.delete(labId);
      },
    });
  }

  stopLab(labId?: string, user?: User): void {
    if (!labId) return; // Early return if labId is not provided

    this.loadingLabs.add(labId);

    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];

    // Filter trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    const filteredTrackedLabs: LabTracker[] = this.trackedLabs.filter(
      tracker => teamLabTrackerIds.includes(tracker.id!) &&
        tracker.labStatus !== LabStatus.DELETED && tracker.labStarted?.id === labId
    );

    const latestLabTracker: LabTracker | undefined = filteredTrackedLabs.sort((a, b) =>
      (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
    )[0];

    if (!latestLabTracker) {
      console.warn(`No active lab found for labId: ${labId}`);
      return;
    }

    const labRequest: LabRequest = {
      labId: labId,
      userId: user?.id,
      labTrackerId: latestLabTracker?.id || "",
      remoteServerId: this.remoteServerService.getSelectedRemoteServer().id
    }

    this.labService.stopLab(labRequest).subscribe({
      next: (response: ApiResponse<LabActionResult | undefined>) => {
        if (!response.data) return;

        const stoppedLabTracker = new LabTracker(response.data.labTracker);
        const index = this.trackedLabs.findIndex(tracker => tracker.id === stoppedLabTracker.id);

        if (latestLabTracker) {
          const index = this.trackedLabs.indexOf(latestLabTracker);
          // Handle existing lab tracker
          if (latestLabTracker.labStatus === LabStatus.DELETED) {
            this.trackedLabs.splice(index, 1);
            this.trackedLabs.push(stoppedLabTracker);
          } else if (latestLabTracker.labStatus === LabStatus.ACTIVE || latestLabTracker.labStatus === LabStatus.FAILED) {
            this.trackedLabs[index] = stoppedLabTracker;
          }
        } else {
          // Create a new lab tracker
          this.trackedLabs.push(stoppedLabTracker);
        }

        this.labTrackerService.setLabTrackers([...this.trackedLabs]);

        if (response.data.output) {
          this.dialog.open(DialogComponent, {
            data: {
              title: 'Lab Start',
              content: response.data.output,
              isCode: true,
              isReadOnly: true,
              fileType: 'bash'
            },
            width: '75rem',
            height: '50rem',
            disableClose: true
          });
        }
      },
      error: (err) => {
        console.error(`Failed to stop lab ${labId}:`, err);
        this.loadingLabs.delete(labId);
      },
      complete: () => {
        this.loadingLabs.delete(labId);
      },
    });
  }

  deleteLab(labId?: string): void {
    if (!labId) return; // Early return if labId is not provided

    this.loadingLabs.add(labId);

    // Find all labs that match the labId and are stopped
    const matchingLabs = this.trackedLabs.filter(tracker =>
      (tracker.labStatus === LabStatus.STOPPED || tracker.labStatus === LabStatus.FAILED) && tracker.labStarted?.id === labId
    );

    // Sort the matching labs by updatedAt in descending order to get the latest one
    const latestLabTracker = matchingLabs.sort((a, b) =>
      (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
    )[0];

    if (!latestLabTracker) {
      console.warn(`No stopped lab found for labId: ${labId}`);
      return;
    }

    const labRequest: LabRequest = {
      labId: labId,
      userId: this.loggedInUser!.id,
      labTrackerId: latestLabTracker?.id || "",
      remoteServerId: this.remoteServerService.getSelectedRemoteServer().id
    }

    this.labService.deleteLab(labRequest).subscribe({
      next: (response: ApiResponse<LabActionResult | undefined>) => {
        if (!response.data) return;

        const deletedLabTracker = new LabTracker(response.data.labTracker);

        if (response.data.output) {
          this.dialog.open(DialogComponent, {
            data: {
              title: 'Lab Start',
              content: response.data.output,
              isCode: true,
              isReadOnly: true,
              fileType: 'bash'
            },
            width: '75rem',
            height: '50rem',
            disableClose: true
          });
        }

        const index = this.trackedLabs.findIndex(tracker => tracker.id === deletedLabTracker.id);
        if (index !== -1) {
          this.trackedLabs[index] = deletedLabTracker;
          this.labTrackerService.setLabTrackers(this.trackedLabs);
        } else {
          console.warn(`Lab tracker not found in trackedLabs: ${deletedLabTracker.id}`);
        }
      },
      error: (err) => {
        console.error(`Failed to delete lab ${labId}:`, err);
        this.loadingLabs.delete(labId);
      },
      complete: () => {
        this.loadingLabs.delete(labId);
      },
    });
  }

  isInTrackedLabs(labId?: string): boolean {
    if (!labId) return false; // Early return if labId is not provided

    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];

    // Check if the labId exists in trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    return this.trackedLabs.some(tracker =>
      teamLabTrackerIds.includes(tracker.id!) &&
      tracker.labStatus !== LabStatus.DELETED &&
      tracker.labStarted?.id === labId
    );
  }

  getLabStatus(labId?: string): string | undefined {
    if (!labId) return LabStatus.NONE; // Early return if labId is not provided
    if (this.trackedLabs.length === 0) return LabStatus.NONE;

    // Get all tracked labs that are active for the team
    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];

    // Filter trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    const filteredTrackedLabs: LabTracker[] = this.trackedLabs.filter(
      tracker => teamLabTrackerIds.includes(tracker.id!) &&
        tracker.labStatus !== LabStatus.DELETED
    );

    // Find the specific lab tracker
    const labTracker = filteredTrackedLabs.find(tracker => tracker.labStarted?.id === labId);

    // Return the lab status or a default value
    return labTracker?.labStatus ?? LabStatus.NONE;
  }

  isLabLoading(labId?: string): boolean {
    return this.loadingLabs.has(labId!);
  }

  getLabTracker(labId?: string): LabTracker | undefined {
    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];

    // Filter and sort trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    const filteredTrackedLabs: LabTracker[] = this.trackedLabs.filter(
      tracker => teamLabTrackerIds.includes(tracker.id!) &&
        tracker.labStatus !== LabStatus.DELETED && tracker.labStarted?.id === labId
    );

    return filteredTrackedLabs.sort((a, b) =>
      (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
    )[0];
  }

  getSettings(labId?: string, user?: User): void {
    this.labService.getSettings(labId!).subscribe((res: ApiResponse<any>) => {
      if (!res.data || !res.data.yml) {
        console.error('No YAML data found in response!');
        return;
      }
      this.dockerComposeData = res.data;
      this.dialog.open(DialogComponent, {
        data: {
          title: 'Lab Output',
          isCode: true,
          content: this.dockerComposeData.yml,
          fileType: 'yaml',
          isReadOnly: false
        },
        width: '50rem',
        height: '50rem'
      });
    });
  }

  viewLogs(labId?: string): void {

  }

  formatDate(date: Date): string {
    if (!date) return '';
    const options: Intl.DateTimeFormatOptions = {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    };

    return date.toLocaleString('en-US', options).replace(/,/g, (
      (count = 0) => (match: any) => {
        count++;
        return count === 2 ? ' @' : match;
      })()
    );
  }

  dockerComposeUpload(event: Event, labId: string): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }
    const file = input.files[0];
    const reader = new FileReader();

    reader.onload = (e: any) => {
      const content = e.target.result;
      // Now, post the content to the backend
      this.labService.uploadDockerComposeFile(labId, content).subscribe((res: ApiResponse<string>) => {
      })
    };
    reader.readAsText(file);
  }

  onMouseEnter(): void {
    this.isHovered = true;
  }

  onMouseLeave(): void {
    this.isHovered = false;
  }

  clear(): void {
    if (!this.loggedInUser?.team?.id) {
      console.error("Team ID is missing");
      return;
    }

    this.labService.clear(this.loggedInUser.team.id).subscribe({
      next: () => {
        window.location.reload();
      },
      error: (err) => {
        console.error("Failed to clear labs:", err);
      }
    });
  }

  formatLabName(name: string): string {
    return name.toLowerCase().replace(/\s+/g, '-');
  }
}

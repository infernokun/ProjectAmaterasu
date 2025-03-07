import { Component, OnInit } from '@angular/core';
import { LabService } from '../../services/lab.service';
import { Lab, LabDTO, LabFormData } from '../../models/lab.model';
import { UserService } from '../../services/user.service';
import { Observable, BehaviorSubject, switchMap, combineLatest, finalize, of, catchError, map } from 'rxjs';
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
import { ProxmoxVM } from '../../models/proxmox-vm.model';
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
  ]
})
export class LabComponent implements OnInit {
  labs: Lab[] = [];
  loggedInUser: User | undefined;
  trackedLabs: LabTracker[] = [];
  team: Team | undefined;

  private isLoadingSubject = new BehaviorSubject<boolean>(false);
  isLoading$ = this.isLoadingSubject.asObservable();
  private loadingLabs = new Set<string>();

  remoteServerControl: FormControl = new FormControl('');

  private labsSubject: BehaviorSubject<Lab[]> = new BehaviorSubject<Lab[]>([]);
  private loggedInUserSubject: BehaviorSubject<User | undefined> = new BehaviorSubject<User | undefined>(undefined);
  private trackedLabsSubject: BehaviorSubject<LabTracker[]> = new BehaviorSubject<LabTracker[]>([]);
  private userTeamSubject: BehaviorSubject<Team | undefined> = new BehaviorSubject<Team | undefined>(undefined);

  labs$: Observable<Lab[]> = this.labsSubject.asObservable();
  loggedInUser$: Observable<User | undefined> = this.loggedInUserSubject.asObservable();
  trackedLabs$: Observable<LabTracker[]> = this.trackedLabsSubject.asObservable();
  userTeam$: Observable<Team | undefined> = this.userTeamSubject.asObservable();

  isHovered = false;
  busy = false;
  dockerComposeData: any;

  LabType = LabType;

  constructor(
    private labService: LabService, private userService: UserService,
    private teamService: TeamService, private labTrackerService: LabTrackerService,
    private dialog: MatDialog, private editDialogService: EditDialogService,
    private proxmoxService: ProxmoxService, private remoteServerService: RemoteServerService, private authService: AuthService) { }

  ngOnInit(): void {
    combineLatest([
      this.labService.getAllLabs(),
      this.authService.user$,
      this.labTrackerService.getAllLabTrackers()
    ]).pipe(
      switchMap(([labs, user, labsTracked]) => {
        this.labs = labs
          .map(lab => new Lab(lab))
          .sort((a, b) => a.name!.localeCompare(b.name!));

        this.labsSubject.next(this.labs);
        this.loggedInUser = user;
        this.loggedInUserSubject.next(this.loggedInUser);
        this.trackedLabs = labsTracked.map(tracker => new LabTracker(tracker));
        this.trackedLabsSubject.next(this.trackedLabs);

        // Ensure loggedInUser and its team are defined before proceeding
        const teamId = this.loggedInUser?.team?.id;
        if (!teamId) {
          console.warn("Logged in user does not have a team.");
          return of(null); // Return an observable with null if no team
        }

        return this.teamService.getTeamById(teamId);
      }),
      finalize(() => {
        this.isLoadingSubject.next(false); // Loading ends
      })
    ).subscribe((team) => {
      if (team) {
        this.team = team;
        this.userTeamSubject.next(this.team);
      } else {
        console.warn("No team data available.");
      }
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
    if (this.trackedLabsSubject.value.length === 0) return LabStatus.NONE;

    // Get all tracked labs that are active for the team
    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];

    // Filter trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    const filteredTrackedLabs: LabTracker[] = this.trackedLabsSubject.value.filter(
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

  addLab() {
    const labFormData: LabFormData = new LabFormData();
    const vmTemplates$: Observable<ProxmoxVM[]> = this.proxmoxService.getVMTemplates(this.remoteServerService.getSelectedRemoteServer().id!);
    const remoteServers$: Observable<RemoteServer[]> = this.remoteServerService.getAllServers();

    const labFormDataWithVMs = new LabFormData(
      (k: any, v: any) => { },
      {
        'remoteServer': remoteServers$,
        'vms': vmTemplates$
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
        this.trackedLabsSubject.next([...this.trackedLabs]);

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
          errorContent = JSON.stringify(apiResponse.data, null, 2); // Pretty-print JSON
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

        this.trackedLabsSubject.next([...this.trackedLabs]);

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
          this.trackedLabsSubject.next(this.trackedLabs);
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

  onMouseEnter() {
    this.isHovered = true;
  }

  onMouseLeave() {
    this.isHovered = false;
  }

  clear() {
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

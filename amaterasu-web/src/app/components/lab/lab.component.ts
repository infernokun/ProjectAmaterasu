import { Component, OnInit } from '@angular/core';
import { LabService } from '../../services/lab/lab.service';
import { Lab } from '../../models/lab.model';
import { UserService } from '../../services/user/user.service';
import { Observable, BehaviorSubject, switchMap, combineLatest, finalize, of, catchError, map } from 'rxjs';
import { User } from '../../models/user.model';
import { ApiResponse } from '../../models/api-response.model';
import { LabTrackerService } from '../../services/lab-tracker/lab-tracker.service';
import { LabTracker } from '../../models/lab-tracker.model';
import { TeamService } from '../../services/team/team.service';
import { Team } from '../../models/team.model';
import { LabStatus } from '../../enums/lab-status.enum';
import { LabActionResult } from '../../models/lab-action-result.model';
import { MatDialog } from '@angular/material/dialog';
import { DialogComponent } from '../common/dialog/dialog.component';

@Component({
  selector: 'app-lab',
  templateUrl: './lab.component.html',
  styleUrl: './lab.component.scss'
})
export class LabComponent implements OnInit {
  labs: Lab[] = [];
  loggedInUser: User | undefined;
  trackedLabs: LabTracker[] = [];
  team: Team | undefined;

  private isLoadingSubject = new BehaviorSubject<boolean>(false);
  isLoading$ = this.isLoadingSubject.asObservable();
  private loadingLabs = new Set<string>();

  private labsSubject = new BehaviorSubject<Lab[]>([]);
  private loggedInUserSubject = new BehaviorSubject<User | undefined>(undefined);
  private trackedLabsSubject = new BehaviorSubject<LabTracker[]>([]);
  private userTeamSubject = new BehaviorSubject<Team | undefined>(undefined);

  labsObservable$: Observable<Lab[]> = this.labsSubject.asObservable();
  loggedInUserObservable$: Observable<User | undefined> = this.loggedInUserSubject.asObservable();
  trackedLabsObservable$: Observable<LabTracker[]> = this.trackedLabsSubject.asObservable();
  userTeamObservable$: Observable<Team | undefined> = this.userTeamSubject.asObservable();

  isHovered = false;

  dockerComposeData: any;

  constructor(
    private labService: LabService, private userService: UserService,
    private teamService: TeamService, private labTrackerService: LabTrackerService,
    private dialog: MatDialog) { }

  ngOnInit(): void {
    combineLatest([
      this.labService.getAllLabs(),
      this.userService.getLoggedInUser(),
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
        console.log("Starter tracked labs:", this.trackedLabs);
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
    if (!labId) return; // Early return if labId is not provided

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

  startLab(labId?: string, user?: User): void {
    if (!labId) return; // Early return if labId is not provided

    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];

    // Filter and sort trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    const filteredTrackedLabs: LabTracker[] = this.trackedLabs.filter(
      tracker => teamLabTrackerIds.includes(tracker.id!) &&
        tracker.labStatus !== LabStatus.DELETED && tracker.labStarted?.id === labId
    );

    const latestLabTracker: LabTracker | undefined = filteredTrackedLabs.sort((a, b) =>
      (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
    )[0];

    // Add the labId to the loadingLabs set
    this.loadingLabs.add(labId);

    this.labService.startLab(labId, user?.id, latestLabTracker?.id || "").subscribe({
      next: (response: ApiResponse<LabActionResult | undefined>) => {
        if (!response.data) return;

        const newTrackedLab = new LabTracker(response.data.labTracker);

        if (latestLabTracker) {
          const index = this.trackedLabs.indexOf(latestLabTracker);
          // Handle existing lab tracker
          if (latestLabTracker.labStatus === LabStatus.DELETED) {
            this.trackedLabs.splice(index, 1);
            this.trackedLabs.push(newTrackedLab);
            console.log("Found a lab tracker, but it was DELETED", this.trackedLabs);
          } else if (latestLabTracker.labStatus === LabStatus.STOPPED || latestLabTracker.labStatus === LabStatus.FAILED) {
            this.trackedLabs[index] = newTrackedLab;
            console.log("Found a lab tracker, but it was STOPPED at index", index);
          }
        } else {
          // Create a new lab tracker
          this.trackedLabs.push(newTrackedLab);
          console.log("Created a new lab tracker:", newTrackedLab);
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
              output: response.data.output,
              applyColor: true
            },
            width: '400px'
          });
        }

        console.log("Started lab:", response);
      },
      error: (err) => {
        console.error(`Failed to start lab ${labId}:`, err);
      },
      complete: () => {
        // Remove the labId from the loadingLabs set
        this.loadingLabs.delete(labId);
      },
    });
  }

  stopLab(labId?: string, user?: User): void {
    if (!labId) return; // Early return if labId is not provided

    const teamLabTrackerIds: string[] = this.team?.teamActiveLabs ?? [];
    console.log("Team active labs:", teamLabTrackerIds);

    // Filter trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    const filteredTrackedLabs: LabTracker[] = this.trackedLabs.filter(
      tracker => teamLabTrackerIds.includes(tracker.id!) &&
        tracker.labStatus !== LabStatus.DELETED && tracker.labStarted?.id === labId
    );


    // Sort the matching labs by updatedAt in descending order to get the latest one
    const latestLabTracker: LabTracker | undefined = filteredTrackedLabs.sort((a, b) =>
      (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0)
    )[0];

    if (!latestLabTracker) {
      console.warn(`No active lab found for labId: ${labId}`);
      return;
    }

    this.loadingLabs.add(labId);

    this.labService.stopLab(labId, user?.id, latestLabTracker.id).subscribe({
      next: ({ data }: ApiResponse<LabTracker | undefined>) => {
        if (!data) return;

        const stoppedLabTracker = new LabTracker(data);
        const index = this.trackedLabs.findIndex(tracker => tracker.id === stoppedLabTracker.id);

        if (index !== -1) {
          this.trackedLabs[index] = stoppedLabTracker;
          this.trackedLabsSubject.next(this.trackedLabs);
          console.log("Updated tracked labs after stopping:", this.trackedLabs);
        } else {
          console.warn(`Lab tracker not found in trackedLabs: ${stoppedLabTracker.id}`);
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

    this.labService.deleteLab(labId, this.loggedInUser?.id, latestLabTracker.id)
      .subscribe({
        next: ({ data }: ApiResponse<LabTracker | undefined>) => {
          if (!data) return;

          const deletedLabTracker = new LabTracker(data);
          console.log("Deleted lab", deletedLabTracker);

          const index = this.trackedLabs.findIndex(tracker => tracker.id === deletedLabTracker.id);
          if (index !== -1) {
            this.trackedLabs[index] = deletedLabTracker;
            this.trackedLabsSubject.next(this.trackedLabs);
            console.log("Updated tracked labs after deletion", this.trackedLabs);
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
      console.log('API Response:', res); // Debugging
      if (!res.data || !res.data.yml) {
        console.error('No YAML data found in response!');
        return;
      }

      this.dockerComposeData = res.data;
      console.log('Opening Dialog with YAML:', res.data.yml);

      this.dialog.open(DialogComponent, {
        data: {
          isCode: true,
          content: res.data.yml, // YAML Data
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
    console.log('Uploaded file:', file);

    const reader = new FileReader();
    reader.onload = (e: any) => {
      const content = e.target.result;
      console.log('File content:', content);
      // Now, post the content to the backend
      this.labService.uploadDockerComposeFile(labId, content).subscribe((res: ApiResponse<string>) => {
        console.log('Upload response:', res);
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
}

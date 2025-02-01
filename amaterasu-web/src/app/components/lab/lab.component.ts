import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { LabService } from '../../services/lab/lab.service';
import { Lab } from '../../models/lab.model';
import { UserService } from '../../services/user/user.service';
import { Observable, BehaviorSubject, switchMap, combineLatest, finalize } from 'rxjs';
import { User } from '../../models/user.model';
import { ApiResponse } from '../../models/api-response.model';
import { LabTrackerService } from '../../services/lab-tracker/lab-tracker.service';
import { LabTracker } from '../../models/lab-tracker.model';
import { TeamService } from '../../services/team/team.service';
import { Team } from '../../models/team.model';
import { LabStatus } from '../../enums/lab-status.enum';

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

  private memoizedTrackedLabs: LabTracker[] = [];

  private labsSubject = new BehaviorSubject<Lab[]>([]);
  private loggedInUserSubject = new BehaviorSubject<User | undefined>(undefined);
  private trackedLabsSubject = new BehaviorSubject<LabTracker[]>([]);
  private userTeamSubject = new BehaviorSubject<Team | undefined>(undefined);

  labsObservable$: Observable<Lab[]> = this.labsSubject.asObservable();
  loggedInUserObservable$: Observable<User | undefined> = this.loggedInUserSubject.asObservable();
  trackedLabsObservable$: Observable<LabTracker[]> = this.trackedLabsSubject.asObservable();
  userTeamObservable$: Observable<Team | undefined> = this.userTeamSubject.asObservable();

  isHovered = false;

  constructor(
    private labService: LabService,
    private userService: UserService,
    private teamService: TeamService,
    private labTrackerService: LabTrackerService,
    private cdRef: ChangeDetectorRef
  ) {
    this.userTeamSubject.subscribe((team) => {
      if (!team) return;
      this.team = team;
    });
  }

  ngOnInit(): void {
    combineLatest([
      this.labService.getAllLabs(),
      this.userService.getLoggedInUser(),
      this.labTrackerService.getAllLabTrackers()
    ]).pipe(
      switchMap(([labs, user, labsTracked]) => {
        this.labs = labs.map(lab => new Lab(lab));
        this.labsSubject.next(this.labs);

        this.loggedInUser = user;
        this.loggedInUserSubject.next(this.loggedInUser);

        this.trackedLabs = labsTracked.map((tracker) => new LabTracker(tracker));
        this.trackedLabsSubject.next(this.trackedLabs);

        // Return the trackedLabs after both labs and loggedInUser are available
        return this.teamService.getTeamById(this.loggedInUser!.team!.id!);
      }),
      finalize(() => {
        this.isLoadingSubject.next(false); // Loading ends
      })
    ).subscribe((team) => {
      this.team = team;
      this.memoizedTrackedLabs = this.trackedLabs.filter(tracker =>
        this.team?.teamActiveLabs?.includes(tracker.labStarted?.id ?? '')
      );
      console.log("Starter tracked labs", this.trackedLabs);
      this.userTeamSubject.next(this.team);

      //this.filterTrackedLabs();
    });
  }

  filterTrackedLabs(): void {
    // Ensure both labs and trackedLabs are populated before filtering
    if (!this.loggedInUser || this.trackedLabsSubject.value.length === 0) {
      return;
    }

    // Filter the trackedLabs by the user's team
    const allTrackedLabsByUser: LabTracker[] = this.trackedLabsSubject.value.filter((tracker: LabTracker) => {
      return tracker.labOwner?.id === this.loggedInUser?.team?.id;
    });

    let teamLabsInTracker: LabTracker[] = [];

    this.trackedLabs.forEach((tracker: LabTracker) => {
      this.team!.teamActiveLabs?.forEach((labId: string) => {
        if (labId === tracker.labStarted?.id) {
          teamLabsInTracker.push(tracker);
        }
      });
    });

    // Find the labs in view that are being tracked by the user
    const foundLabs = this.trackedLabs
      .map((tracker: LabTracker) => {
        return this.labsSubject.value.filter((lab: Lab) => {
          return lab.id === tracker.labStarted?.id;
        });
      })
      .flat();

    // Memoize the result to avoid recalculating multiple times
    this.memoizedTrackedLabs = allTrackedLabsByUser.filter((tracker) =>
      foundLabs.some((lab) => lab.id === tracker.labStarted?.id)
    );
  }

  isInTrackedLabs(labId?: string): boolean {
    //return this.memoizedTrackedLabs.some(tracker => tracker.labStarted?.id === labId);
    return this.team?.teamActiveLabs?.includes(labId!)!;
  }

  getLabStatus(labId?: string): string | undefined {
    const teamLabTrackerId: string | undefined = this.team?.teamActiveLabs?.find(lab => lab === labId);

    const filteredTrackedLabs: LabTracker[] = this.trackedLabs
      .filter(tracker => tracker.labStarted?.id === teamLabTrackerId)
      .filter(tracker => tracker.labStatus !== LabStatus.DELETED);

    return teamLabTrackerId && filteredTrackedLabs.length > 0
      ? filteredTrackedLabs.find(tracker => tracker.labStarted?.id === teamLabTrackerId)?.labStatus
      : LabStatus.NONE;
  }

  /*getLabStatus(labId?: string): string | undefined {
    const teamLabTracked = this.team?.teamActiveLabs?.find(lab => lab === labId);
    return this.trackedLabs.find(tracker => tracker.labStarted?.id === teamLabTracked)?.labStatus;
  }*/

  startLab(labId?: string, user?: User): void {
    if (!labId) return;

    // Add the labId to the loadingLabs set
    this.loadingLabs.add(labId);

    this.labService.startLab(labId, user?.id).subscribe({
      next: (response: ApiResponse<LabTracker | undefined>) => {
        const { data } = response;
        if (!data) return;

        const newTrackedLab = new LabTracker(data);
        const existingLabTracker = this.trackedLabs.find(tracker =>
          tracker.labStarted?.id === newTrackedLab.labStarted?.id &&
          tracker.labOwner?.name === newTrackedLab.labOwner?.name
        );

        if (existingLabTracker) {
          const index = this.trackedLabs.indexOf(existingLabTracker);
          // Handle existing lab tracker
          if (existingLabTracker.labStatus === LabStatus.DELETED) {
            this.trackedLabs.splice(index, 1);
            this.trackedLabs.push(newTrackedLab);

            console.log("Found a lab tracker, but its DELETED", this.trackedLabs, this.team?.teamActiveLabs);
          } else if (existingLabTracker.labStatus === LabStatus.STOPPED) {
            this.trackedLabs[index] = newTrackedLab;

            console.log("Found a lab tracker, but its STOPPED")

          }
        } else {
          // Create a new lab tracker
          this.trackedLabs.push(newTrackedLab);

        }

        this.team?.teamActiveLabs?.push(newTrackedLab.labStarted?.id!);

        // Emit updated subjects
        this.userTeamSubject.next({ ...this.team });
        this.trackedLabsSubject.next({ ...this.trackedLabs });

        console.log("Started", this.trackedLabs);
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
    if (!labId) return;

    this.loadingLabs.add(labId);

    this.labService.stopLab(labId, user?.id).subscribe({
      next: (response: ApiResponse<LabTracker | undefined>) => {
        if (!response.data) return;

        const stoppedLabTracker = new LabTracker(response.data);

        const index = this.trackedLabs.findIndex((tracker) =>
          tracker.id === stoppedLabTracker.id
        );

        this.trackedLabs[index] = stoppedLabTracker;
        //this.memoizedTrackedLabs = this.trackedLabs;

        this.trackedLabsSubject.next(this.trackedLabs);

      },
      error: (err) => {
        console.error(`Failed to stop lab ${labId}:`, err);
        this.loadingLabs.delete(labId!);
      },
      complete: () => {
        this.loadingLabs.delete(labId!);
      },
    });
  }

  deleteLab(labId?: string): void {
    // Find all labs that match the labId and are stopped
    const matchingLabs = this.trackedLabs.filter(tracker =>
      tracker.labStarted?.id === labId && tracker.labStatus == LabStatus.STOPPED
    );

    // Sort the matching labs by updatedAt in descending order to get the latest one
    const latestLabTracker = matchingLabs.sort((a: LabTracker, b: LabTracker) =>
      b.updatedAt!.getTime() - a.updatedAt!.getTime()
    )[0];



    if (!labId) return;

    this.labService.deleteLab(labId, this.loggedInUser?.id, latestLabTracker.id).subscribe({
      next: (response: ApiResponse<LabTracker | undefined>) => {
        if (!response.data) return;

        const deletedLabTracker = new LabTracker(response.data);

        console.log("Deleted lab", deletedLabTracker);

        const index = this.trackedLabs.findIndex((tracker) =>
          tracker.id === deletedLabTracker.id
        );

        //console.log("ahh ha!!", index, this.trackedLabs[index].id, "should be deleted", deletedLab.id);

        this.trackedLabs[index] = deletedLabTracker;

        //console.log("ahh ha!!", this.trackedLabs[index].labStatus, "should be is not like", deletedLab.labStatus);

        this.trackedLabsSubject.next(this.trackedLabs);

        console.log("Deleted... here we are", this.trackedLabs);
      },
      error: (err) => {
        console.error(`Failed to delete lab ${labId}:`, err);
        this.loadingLabs.delete(labId!);
      },
      complete: () => {
        this.loadingLabs.delete(labId!);
      },
    });
  }

  viewLogs(labId?: string): void {

  }

  isLabLoading(labId?: string): boolean {
    return this.loadingLabs.has(labId!);
  }

  // Method to format the date
  formatDate(date: Date): string {
    if (!date) return '';
    const options: Intl.DateTimeFormatOptions = {
      year: 'numeric',
      month: 'short', // 'short' gives you abbreviated month names
      day: '2-digit',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true // Use 12-hour format
    };

    return date.toLocaleString('en-US', options).replace(/,/g, (
      (count = 0) => (match: any) => {
        count++;
        return count === 2 ? ' @' : match; // Replace only the 2nd comma
      })()
    );
  }

  onMouseEnter() {
    this.isHovered = true;
  }

  onMouseLeave() {
    this.isHovered = false;
  }
}

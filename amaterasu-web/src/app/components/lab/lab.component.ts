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

      this.userTeamSubject.next(this.team);

      //this.filterTrackedLabs();
    });
    /*
    combineLatest([
      this.labService.getAllLabs(),
      this.userService.getLoggedInUser(),
      this.labTrackerService.getAllLabTrackers()
    ])
      .pipe(
        finalize(() => this.isLoadingSubject.next(false))
      )
      .subscribe(([labs, user, labsTracked]) => {
        this.labs = labs;
        this.labsSubject.next(this.labs);

        this.loggedInUser = user;
        this.loggedInUserSubject.next(this.loggedInUser);

        this.trackedLabs = labsTracked.map((tracker) => new LabTracker(tracker));
        this.trackedLabsSubject.next(this.trackedLabs);

        if (this.loggedInUser?.team?.id) {
          this.teamService.getTeamById(this.loggedInUser.team.id).subscribe(team => {
            this.team = team;
            this.memoizedTrackedLabs = this.trackedLabs.filter(tracker =>
              this.team?.teamActiveLabs?.includes(tracker.labStarted?.id ?? '')
            );

            this.userTeamSubject.next(this.team);
          });
        }
      });*/
  }

  filterTrackedLabs(): void {
    // Ensure both labs and trackedLabs are populated before filtering
    if (!this.loggedInUser || this.trackedLabsSubject.value.length === 0) {
      return;
    }

    // Filter the trackedLabs by the user's team
    const allTrackedLabsByUser: LabTracker[] = this.trackedLabsSubject.value.filter((tracker: LabTracker) => {
      return tracker.labOwner?.id === this.loggedInUser?.team?.id; //this.team!.teamActiveLabs?.includes(tracker.labStarted?.id!);
    });

    /*
        let allTrackedLabsByUser1: LabTracker[] = this.trackedLabsSubject.value.filter((tracker: LabTracker) => {
          return this.team!.teamActiveLabs?.includes(tracker.labStarted?.id!);
        });

        let allTrackedLabsByUser2: LabTracker[] = [];

        this.trackedLabs.forEach((tracker: LabTracker) => {
          allTrackedLabsByUser2.push(tracker);
          if (this.team!.teamActiveLabs?.includes(tracker.labStarted?.id!)) {
            console.log("confirmed");
          }
        });*/

    console.log(this.team!.teamActiveLabs, this.trackedLabs);

    let teamLabsInTracker: LabTracker[] = [];

    this.trackedLabs.forEach((tracker: LabTracker) => {
      this.team!.teamActiveLabs?.forEach((labId: string) => {
        console.log(`Comparing labId: ${labId} with tracker ID: ${tracker.labStarted?.id}`);
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
    const teamLabTracked = this.team?.teamActiveLabs?.find(lab => lab === labId);
    return this.trackedLabs.find(tracker => tracker.labStarted?.id === teamLabTracked)?.labStatus;
  }

  startLab(labId?: string, user?: User): void {
    if (!labId) return;

    console.log(`Starting lab ${labId} for user ${user?.username} under ${user?.team?.name} team`);

    // Add the labId to the loadingLabs set
    this.loadingLabs.add(labId);

    this.labService.startLab(labId, user?.id).subscribe({
      next: (response: ApiResponse<LabTracker | undefined>) => {
        if (!response.data) return;

        const newTrackedLab: LabTracker = new LabTracker(response.data);

        console.log(response.message);

        const index = this.trackedLabs.findIndex((tracker) =>
          tracker.labStarted?.id === newTrackedLab.labStarted?.id &&
          tracker.labOwner?.name === newTrackedLab.labOwner?.name
        );

        if (index !== -1) {
          console.log("existing lab tracker found", index);
          this.trackedLabs[index] = newTrackedLab;
        } else {
          console.log("index", "new lab tracker created");
          this.trackedLabs.push(newTrackedLab);
          this.team?.teamActiveLabs?.push(newTrackedLab.labStarted?.id!);
        }

        //this.memoizedTrackedLabs = this.trackedLabs;
        this.userTeamSubject.next(this.team);
        this.trackedLabsSubject.next(this.trackedLabs);
      },
      error: (err) => {
        console.error(`Failed to start lab ${labId}:`, err);
        this.loadingLabs.delete(labId);
      },
      complete: () => {
        // Remove the labId from the loadingLabs set
        this.loadingLabs.delete(labId);
      },
    });
  }

  stopLab(labId?: string, user?: User): void {
    if (!labId) return;

    console.log(`Stopping lab ${labId} for user ${user?.username} under ${user?.team?.name} team`);
    console.log("trackedBefore", this.trackedLabs);

    this.loadingLabs.add(labId);

    this.labService.stopLab(labId, user?.id).subscribe({
      next: (response: ApiResponse<LabTracker | undefined>) => {
        if (!response.data) return;

        const stoppedLab = new LabTracker(response.data);

        console.log(`Lab ${response.data.id} stopped successfully`);

        const index = this.trackedLabs.findIndex((tracker) =>
          tracker.labStarted?.id === stoppedLab.labStarted?.id &&
          tracker.labOwner?.name === stoppedLab.labOwner?.name
        );

        this.trackedLabs[index] = stoppedLab;
        //this.memoizedTrackedLabs = this.trackedLabs;

        this.trackedLabsSubject.next(this.trackedLabs);

        console.log("trackedAfter", this.trackedLabs);


        console.log(response);
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

  deleteLab(labId?: string, user?: User): void {

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

import { Component, OnInit } from '@angular/core';
import { LabService } from '../../services/lab/lab.service';
import { Lab } from '../../models/lab.model';
import { UserService } from '../../services/user/user.service';
import { Observable, BehaviorSubject, switchMap, combineLatest, finalize } from 'rxjs';
import { User } from '../../models/user.model';
import { ApiResponse } from '../../models/api-response.model';
import { LabTrackerService } from '../../services/lab-tracker/lab-tracker.service';
import { LabTracker } from '../../models/lab-tracker.model';

@Component({
  selector: 'app-lab',
  templateUrl: './lab.component.html',
  styleUrl: './lab.component.scss'
})
export class LabComponent implements OnInit {
  labs: Lab[] = [];
  loggedInUser: User | undefined;
  trackedLabs: LabTracker[] = [];

  private isLoadingSubject = new BehaviorSubject<boolean>(false);
  isLoading$ = this.isLoadingSubject.asObservable();

  private memoizedTrackedLabs: LabTracker[] | undefined;
  private loadingLabs = new Set<string>();

  // BehaviorSubjects to hold the data
  private labsSubject = new BehaviorSubject<Lab[]>([]);
  private loggedInUserSubject = new BehaviorSubject<User | undefined>(undefined);
  private trackedLabsSubject = new BehaviorSubject<LabTracker[]>([]);

  // Observables for data
  labs$: Observable<Lab[]> = this.labsSubject.asObservable();
  loggedInUserObservable$: Observable<User | undefined> = this.loggedInUserSubject.asObservable();
  trackedLabsObservable$: Observable<LabTracker[]> = this.trackedLabsSubject.asObservable();

  constructor(
    private labService: LabService,
    private userService: UserService,
    private labTrackerService: LabTrackerService
  ) {
    this.trackedLabsSubject.subscribe((updatedTrackedLabs) => {
      if (!this.memoizedTrackedLabs) return;
      // Merge the arrays and remove duplicates based on labStarted.id
      this.memoizedTrackedLabs = [
        ...this.memoizedTrackedLabs!,
        ...updatedTrackedLabs.filter(
          (newLab) =>
            !this.memoizedTrackedLabs!.some(
              (existingLab) => existingLab.labStarted?.id === newLab.labStarted?.id
            )
        ),
      ];
    });
  }

  ngOnInit(): void {
    this.labs$ = this.labService.getAllLabs();
    this.loggedInUserObservable$ = this.userService.getLoggedInUser();

    combineLatest([
      this.labs$,
      this.loggedInUserObservable$
    ]).pipe(
      switchMap(([labs, user]) => {
        this.labs = labs.map(lab => new Lab(lab));
        this.labsSubject.next(this.labs);

        this.loggedInUser = user;
        this.loggedInUserSubject.next(this.loggedInUser);

        console.log("labs", this.labs);
        console.log("loggedInUser", this.loggedInUser);

        // Return the trackedLabs after both labs and loggedInUser are available
        return this.labTrackerService.getAllLabTrackers();
      }),
      finalize(() => {
        this.isLoadingSubject.next(false); // Loading ends
      })
    ).subscribe((labTrackers) => {
      this.trackedLabs = labTrackers.map(labTracker => new LabTracker(labTracker));
      this.trackedLabsSubject.next(this.trackedLabs);

      this.filterTrackedLabs();
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

    // Find the labs in view that are being tracked by the user
    const foundLabs = allTrackedLabsByUser
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
    // Get the filtered tracked labs for the user
    const allTrackedLabsByUser: LabTracker[] | undefined = this.memoizedTrackedLabs;

    if (!allTrackedLabsByUser) {
      return false;
    }

    // Check if any of the tracked labs match the provided labId
    return allTrackedLabsByUser.some((tracker) => tracker.labStarted?.id === labId);
  }

  getLabStatus(labId?: string): string | undefined {
    const trackedLab = this.memoizedTrackedLabs?.find(tracker => tracker.labStarted?.id === labId);
    return trackedLab?.labStatus;
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

        console.log(`Lab ${response.data.id} started successfully`);

        this.trackedLabs.push(newTrackedLab);
        this.trackedLabsSubject.next(this.trackedLabs);

        console.log(response);
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
}

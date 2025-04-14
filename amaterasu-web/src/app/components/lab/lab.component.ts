import { Component, OnInit } from '@angular/core';
import { LabService } from '../../services/lab.service';
import { Lab, LabDTO, LabFormData } from '../../models/lab.model';
import { UserService } from '../../services/user.service';
import {
  Observable,
  BehaviorSubject,
  switchMap,
  combineLatest,
  of,
  distinctUntilChanged,
  filter,
} from 'rxjs';
import { User } from '../../models/user.model';
import { ApiResponse } from '../../models/api-response.model';
import { LabTrackerService } from '../../services/lab-tracker.service';
import { LabTracker } from '../../models/lab-tracker.model';
import { TeamService } from '../../services/team.service';
import { Team } from '../../models/team.model';
import { LabStatus } from '../../enums/lab-status.enum';
import { MatDialog } from '@angular/material/dialog';
import { EditDialogService } from '../../services/edit-dialog.service';
import { LabType } from '../../enums/lab-type.enum';
import { ProxmoxService } from '../../services/proxmox.service';
import { RemoteServer } from '../../models/remote-server.model';
import { RemoteServerService } from '../../services/remote-server.service';
import { FormControl } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { trigger, transition, style, animate } from '@angular/animations';

@Component({
  selector: 'app-lab',
  templateUrl: './lab.component.html',
  styleUrl: './lab.component.scss',
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
  standalone: false,
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
  labTrackersByTeam$: Observable<LabTracker[]> = of([]);
  userTeam$: Observable<Team | undefined> = this.userTeamSubject.asObservable();
  isLoading$: Observable<boolean> = this.isLoadingSubject.asObservable();

  constructor(
    private labService: LabService,
    private userService: UserService,
    private teamService: TeamService,
    private labTrackerService: LabTrackerService,
    private dialog: MatDialog,
    private editDialogService: EditDialogService,
    private proxmoxService: ProxmoxService,
    private remoteServerService: RemoteServerService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.isLoadingSubject.next(true);

    this.labService.fetchLabs();

    combineLatest([this.labService.labs$, this.authService.user$])
      .pipe(
        // Make sure user exists and labs and labsTracked are arrays (empty arrays are valid)
        filter(([labs, user]) => !!user && Array.isArray(labs)),
        // Only react when the team ID changes, not on every emission
        distinctUntilChanged(
          ([_, prevUser], [___, currUser]) =>
            prevUser?.team?.id === currUser?.team?.id
        ),
        switchMap(([labs, user]) => {
          // Store values locally
          this.labs = labs!;
          this.labs$ = this.labService.labs$;
          this.loggedInUser = user;
          this.loggedInUserSubject.next(this.loggedInUser);

          this.team = this.loggedInUser?.team;
          this.userTeamSubject.next(this.loggedInUser?.team);

          const teamId = user?.team?.id;
          if (!teamId) {
            console.error('Logged in user does not have a team.');
            return of(undefined);
          }

          // Only fetch team data if necessary
          return this.labTrackerService.getLabTrackersByTeam(
            this.loggedInUser?.team?.id!
          );
        })
      )
      .subscribe({
        next: (labTrackers: LabTracker[] | undefined) => {
          if (!labTrackers) {
            return;
          }

          // Handle empty labsTracked array as a valid case
          this.trackedLabs = labTrackers.filter(
            (labTracker: LabTracker) =>
              labTracker.labStatus !== LabStatus.DELETED
          );

          this.labTrackerService.setLabTrackersByTeam(this.trackedLabs);

          this.isLoadingSubject.next(false);
        },
        error: (err) => {
          console.error('Error in data loading process:', err);
          this.isLoadingSubject.next(false);
        },
      });
  }

  addLab(): void {
    const labFormData: LabFormData = new LabFormData();
    const vmTemplates = this.proxmoxService.getVMTemplates.bind(
      this.proxmoxService
    );
    const remoteServers$: Observable<RemoteServer[]> =
      this.remoteServerService.getAllServers();

    const labFormDataWithVMs = new LabFormData(
      (k: any, v: any) => {},
      {
        remoteServer: remoteServers$,
        //'vms': vmTemplates$
      },
      {
        vms: vmTemplates,
      }
    );

    this.editDialogService
      .openDialog<Lab>(labFormDataWithVMs, (labDTO: LabDTO) => {
        if (!labDTO) return;
        this.busy = true;

        labDTO = new LabDTO(labDTO);
        labDTO.createdBy = this.loggedInUser?.username;

        this.labService
          .createNewLab(
            labDTO,
            this.remoteServerService.getSelectedRemoteServer().id!
          )
          .subscribe((labResp: ApiResponse<Lab>) => {
            this.busy = false;

            if (!labResp.data) return;
            this.labs.push(new Lab(labResp.data));
          });
      })
      .subscribe((res: any) => {});
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

  onMouseEnter(): void {
    this.isHovered = true;
  }

  onMouseLeave(): void {
    this.isHovered = false;
  }

  clear(): void {
    if (!this.loggedInUser?.team?.id) {
      console.error('Team ID is missing');
      return;
    }

    this.labService.clear(this.loggedInUser.team.id).subscribe({
      next: () => {
        window.location.reload();
      },
      error: (err) => {
        console.error('Failed to clear labs:', err);
      },
    });
  }

  formatLabName(name: string): string {
    return name.toLowerCase().replace(/\s+/g, '-');
  }
}

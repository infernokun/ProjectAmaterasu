import { Component, OnDestroy, OnInit } from '@angular/core';
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
  takeUntil,
  catchError,
  Subject,
  finalize,
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
import { DateUtils } from '../../utils/date-utils';

@Component({
  selector: 'app-lab',
  templateUrl: './lab.component.html',
  styleUrl: './lab.component.scss',
  animations: [],
  standalone: false,
})
export class LabComponent implements OnInit, OnDestroy {
  private loggedInUserSubject: BehaviorSubject<User | undefined> = new BehaviorSubject<User | undefined>(undefined);
  private userTeamSubject: BehaviorSubject<Team | undefined> = new BehaviorSubject<Team | undefined>(undefined);
  private isLoadingSubject: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
  private readonly destroy$ = new Subject<void>();
  private readonly isLoading$ = new BehaviorSubject<boolean>(false);
  

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
    this.initializeData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeData(): void {
    this.isLoading$.next(true);
    this.labService.fetchLabs();

    combineLatest([this.labService.labs$, this.authService.user$])
      .pipe(
        takeUntil(this.destroy$),
        filter(([labs, user]) => !!user && Array.isArray(labs)),
        distinctUntilChanged(([_, prevUser], [__, currUser]) => 
          prevUser?.team?.id === currUser?.team?.id
        ),
        switchMap(([labs, user]) => {
          this.updateLocalState(labs!, user!);
          
          const teamId = user?.team?.id;
          if (!teamId) {
            return of([]);
          }
          
          return this.labTrackerService.getLabTrackersByTeam(teamId)
            .pipe(
              catchError(error => {
                console.error('Failed to load lab trackers:', error);
                return of([]);
              })
            );
        }),
        finalize(() => this.isLoading$.next(false))
      )
      .subscribe({
        next: (labTrackers: LabTracker[]) => {
          this.trackedLabs = labTrackers.filter(
            tracker => tracker.labStatus !== LabStatus.DELETED
          );
          this.labTrackerService.setLabTrackersByTeam(this.trackedLabs);
        },
        error: (err) => {
          console.error('Error in data loading process:', err);
          this.isLoading$.next(false);
        }
      });
  }

  private updateLocalState(labs: Lab[], user: User): void {
    this.labs = labs;
    this.loggedInUser = user;
    this.loggedInUserSubject.next(this.loggedInUser);
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
    return DateUtils.formatDateWithTime(date);
  }

  onMouseEnter(): void {
    this.isHovered = true;
  }

  onMouseLeave(): void {
    this.isHovered = false;
  }

  clear(): void {
    const teamId = this.loggedInUser?.team?.id;
    if (!teamId) {
      console.error('Team ID is missing');
      return;
    }

    this.labService.clear(teamId)
      .pipe(
        takeUntil(this.destroy$),
        catchError(error => {
          console.error('Failed to clear labs:', error);
          return of(null);
        })
      )
      .subscribe({
        next: () => window.location.reload(),
        error: (err) => console.error('Failed to clear labs:', err)
      });
  }

  formatLabName(name: string): string {
    return name.toLowerCase().replace(/\s+/g, '-');
  }
}

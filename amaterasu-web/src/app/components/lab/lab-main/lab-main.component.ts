import { trigger, transition, style, animate } from '@angular/animations';
import { Component, Input, OnInit } from '@angular/core';
import { Lab } from '../../../models/lab.model';
import {
  BehaviorSubject,
  Observable,
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

    this.labDeploymentService.labsLoading$.subscribe((labsLoading: Set<string>) => {
      this.labsLoading = labsLoading;
    });

    this.labDeploymentService.deployLabResponse$.subscribe((response: ApiResponse<LabActionResult>) => {
      this.labsLoading.delete(response.data.labTracker?.labStarted?.id!);

      this.labDeploymentService.updateLabsLoading(this.labsLoading);
    })
  }

  onMouseEnter(): void {
    this.isHovered = true;
  }

  onMouseLeave(): void {
    this.isHovered = false;
  }

  isInTrackedLabs(labId?: string): boolean {
    if (!labId) return false; // Early return if labId is not provided

    const labFound = this.labTrackers.find((labTracker: LabTracker) => labTracker.labStarted?.id == labId);

    // Check if the labId exists in trackedLabs based on matching teamActiveLabs and ensuring the status is not DELETED
    return (labFound != undefined);
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
      this.labService
        .uploadDockerComposeFile(labId, content)
        .subscribe((res: ApiResponse<string>) => {});
    };
    reader.readAsText(file);
  }

  deployLabStart(lab: Lab): void {
    this.labDeploymentService.startLabDeployment(lab);
    this.labsLoading.add(lab.id!);
  }
}

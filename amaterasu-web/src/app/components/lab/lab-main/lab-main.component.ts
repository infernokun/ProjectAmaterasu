import { trigger, transition, style, animate } from '@angular/animations';
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Lab } from '../../../models/lab.model';
import {
  BehaviorSubject,
  Observable,
  Subject,
  takeUntil,
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
import { FADE_ANIMATION } from '../../../utils/animations';

@Component({
  selector: 'app-lab-main',
  standalone: false,
  templateUrl: './lab-main.component.html',
  styleUrl: './lab-main.component.scss',
  animations: [FADE_ANIMATION],
})
export class LabMainComponent implements OnInit, OnDestroy {
  readonly LabType = LabType;
  isHovered = false;
  
  // Observables for async pipe
  labs$: Observable<Lab[] | undefined> | undefined;
  labTrackers$: Observable<LabTracker[] | undefined> | undefined;
  isLoading$: Observable<boolean> | undefined;
  labsLoading$: Observable<Set<string>> | undefined;
  
  // Private properties
  private labTrackers: LabTracker[] = [];
  private destroy$ = new Subject<void>();

  @Input() user: User | undefined;

  constructor(
    private labService: LabService,
    private teamService: TeamService,
    private labTrackerService: LabTrackerService,
    private dialog: MatDialog,
    private remoteServerService: RemoteServerService,
    private authService: AuthService,
    private labDeploymentService: LabDeploymentService
  ) {
    this.labs$ = this.labService.labs$;
    this.labsLoading$ = this.labDeploymentService.labsLoading$;
  }

  ngOnInit(): void {
    this.setupSubscriptions();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private setupSubscriptions(): void {
    // Store lab trackers for isInTrackedLabs method
    this.labTrackerService.labTrackersByTeam$
      .pipe(takeUntil(this.destroy$))
      .subscribe(trackers => {
        this.labTrackers = trackers || [];
      });
  }

  deployLabStart(lab: Lab): void {
    if (!lab.id) return;
    this.labDeploymentService.startLabDeployment(lab);
  }

  dockerComposeUpload(event: Event, labId: string): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    
    const file = input.files[0];
    const reader = new FileReader();

    reader.onload = (e: ProgressEvent<FileReader>) => {
      const content = e.target?.result as string;
      if (!content) return;
      
      this.labService.uploadDockerComposeFile(labId, content)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (res: ApiResponse<string>) => {
            // Handle success
            console.log('Upload successful:', res);
          },
          error: (err) => {
            // Handle error
            console.error('Upload failed:', err);
          }
        });
    };
    
    reader.readAsText(file);
  }

  isInTrackedLabs(labId?: string): boolean {
    if (!labId) return false;
    return this.labTrackers.some(tracker => tracker.labStarted?.id === labId);
  }

  onMouseEnter(): void {
    this.isHovered = true;
  }

  onMouseLeave(): void {
    this.isHovered = false;
  }
}
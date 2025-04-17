import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Lab } from '../../../models/lab.model';
import {
  catchError,
  Observable,
  of,
  Subject,
  takeUntil,
} from 'rxjs';
import { LabType } from '../../../enums/lab-type.enum';
import { User } from '../../../models/user.model';
import { LabTracker } from '../../../models/lab-tracker.model';
import { LabTrackerService } from '../../../services/lab-tracker.service';
import { LabService } from '../../../services/lab.service';
import { AuthService } from '../../../services/auth.service';
import { TeamService } from '../../../services/team.service';
import { MatDialog } from '@angular/material/dialog';
import { RemoteServerService } from '../../../services/remote-server.service';
import { ApiResponse } from '../../../models/api-response.model';
import { LabDeploymentService } from '../../../services/lab-deployment.service';
import { FADE_ANIMATION } from '../../../utils/animations';
import { CommonDialogComponent } from '../../common/dialog/common-dialog/common-dialog.component';
import { EditDialogService } from '../../../services/edit-dialog.service';
import { RemoteServer, RemoteServerSelectData } from '../../../models/remote-server.model';
import { getServerType } from '../../../utils/server-lab-type';

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
    private labDeploymentService: LabDeploymentService,
    private editDialogService: EditDialogService
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

  getSettings(lab: Lab): void {
    if (!lab.id) {
      console.error('Cannot get settings without required IDs');
      return;
    }

    const labId = lab.id;

    const remoteServers$: Observable<RemoteServer[]> =
      this.remoteServerService.getRemoteServerByServerType(
        getServerType(lab.labType!)
      );

    const remoteServerSelectFormData = new RemoteServerSelectData({
      remoteServer: remoteServers$,
    });

    let dialogCancelled = true;

    this.editDialogService
      .openDialog<any>(remoteServerSelectFormData, (response: any) => {
        if (!response?.remoteServer) {
          console.error('No remote server selected');
          return;
        }

        dialogCancelled = false;
        this.labService.getSettings(labId, response.remoteServer)
      .pipe(
        takeUntil(this.destroy$),
        catchError(error => {
          console.error('Failed to get lab settings:', error);
          return of({ code: 404, data: {}, message: 'Failed to fetch settings' });
        })
      )
      .subscribe((res: ApiResponse<any>) => {
        if (!res.data || !res.data.yml) {
          console.error('No YAML data found in response!');
          return;
        }
        
        this.showOutputDialog('Lab Settings', res.data.yml, 'yaml', false);
      });
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (dialogCancelled && lab.id) {
        }
      });
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

  private showOutputDialog(title: string, content: string | object, fileType: string, isReadOnly: boolean = true): void {
    const dialogContent = typeof content === 'object' ? JSON.stringify(content, null, 2) : content;
    
    this.dialog.open(CommonDialogComponent, {
      data: {
        title: title,
        content: dialogContent,
        isCode: true,
        isReadOnly: isReadOnly,
        fileType: fileType,
      },
      width: '75rem',
      height: '50rem',
      disableClose: true,
    });
  }
}
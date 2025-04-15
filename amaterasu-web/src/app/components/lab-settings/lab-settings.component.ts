import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LabService } from '../../services/lab.service';
import { BehaviorSubject, catchError, combineLatest, of, Subject, takeUntil } from 'rxjs';
import { Lab } from '../../models/lab.model';
import { UserService } from '../../services/user.service';
import { Team } from '../../models/team.model';
import { LabTrackerService } from '../../services/lab-tracker.service';
import { LabTracker } from '../../models/lab-tracker.model';
import { ServerType } from '../../enums/server-type.enum';
import { ApiResponse } from '../../models/api-response.model';

import { load } from "js-yaml";

export class ComposeFile {
  services?: Service[]

  constructor(serverResult: any) {
    this.services = serverResult.services;
  }
}

export class Service {
  image?: string;
  volumes?: Volume[];
}

export class Volume {
  mappings?: string[];
}

@Component({
    selector: 'app-lab-settings',
    templateUrl: './lab-settings.component.html',
    styleUrl: './lab-settings.component.scss',
    standalone: false
})
export class LabSettingsComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();

  labId: string = '';
  labName: string = '';
  admin = false;

  ymlData: ComposeFile | undefined;

  labTrackerSubject: BehaviorSubject<LabTracker | undefined> = new BehaviorSubject<LabTracker | undefined>(undefined);
  labTracker: LabTracker | undefined;
  ServerType = ServerType;
  
  constructor(private route: ActivatedRoute, private labService: LabService, private labTrackerService: LabTrackerService, private userService: UserService) { }

  ngOnInit(): void {
    combineLatest([
      this.route.paramMap,
      this.route.queryParamMap
    ]).subscribe(([paramMap, queryParamMap]) => {
      this.labName = paramMap.get('name')!;
      this.labId = queryParamMap.get('id')!;

      this.labTrackerService.getLabTrackerById(this.labId).subscribe((labTracker) => {
        console.log('labTracker', labTracker);
        this.labTracker = labTracker;

        if (labTracker.services?.length! < 1) {
          console.log("Loading additional config loader");
          this.labTrackerService.getSettings(labTracker.id!, labTracker.remoteServer?.id!)
          .pipe(
            takeUntil(this.destroy$),
            catchError(error => {
              console.error('Failed to get lab settings:', error);
              return of({ code: 404, data: {}, message: 'Failed to fetch settings' });
            })
          )
          .subscribe((res: ApiResponse<any>) => {
            if (res && res.data && res.data.yml) {
              this.ymlData = load(res.data.yml) as unknown as ComposeFile;
              
              console.log(this.ymlData);
              console.log(this.ymlData.services![0])
            }
          });
        }
      });
    });

    this.userService.getLoggedInUser().subscribe((user) => {
      if (!user) return;

      console.log('user', user);

      let team: Team = user.team!;
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}

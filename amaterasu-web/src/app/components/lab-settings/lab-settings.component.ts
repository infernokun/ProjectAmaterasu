import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LabService } from '../../services/lab.service';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { Lab } from '../../models/lab.model';
import { UserService } from '../../services/user.service';
import { Team } from '../../models/team.model';
import { LabTrackerService } from '../../services/lab-tracker.service';
import { LabTracker } from '../../models/lab-tracker.model';

@Component({
  selector: 'app-lab-settings',
  templateUrl: './lab-settings.component.html',
  styleUrl: './lab-settings.component.scss'
})
export class LabSettingsComponent implements OnInit {
  labId: string = '';
  labName: string = '';
  admin = false;

  labTrackerSubject: BehaviorSubject<LabTracker | undefined> = new BehaviorSubject<LabTracker | undefined>(undefined);
  labTracker: LabTracker | undefined;
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
        this.labTrackerSubject.next(this.labTracker)
      });
    });

    this.userService.getLoggedInUser().subscribe((user) => {
      if (!user) return;

      console.log('user', user);

      let team: Team = user.team!;
    });
  }
}

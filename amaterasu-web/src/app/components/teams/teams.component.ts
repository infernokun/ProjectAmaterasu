import { Component, OnInit } from '@angular/core';
import { TeamService } from '../../services/team.service';
import { Team, TeamFormData } from '../../models/team.model';
import { LabFormData } from '../../models/lab.model';
import { EditDialogService } from '../../services/edit-dialog.service';
import { AuthService } from '../../services/auth.service';
import { ApiResponse } from '../../models/api-response.model';

@Component({
  selector: 'app-teams',
  templateUrl: './teams.component.html',
  styleUrl: './teams.component.scss'
})
export class TeamsComponent implements OnInit {
  teams: Team[] = [];
  busy = false;

  constructor(private teamService: TeamService,
    private editDialogService: EditDialogService,
    private authService: AuthService) { }

  ngOnInit(): void {
    this.teamService.getAllTeams().subscribe((teams: any) => {
      console.log(teams);
      this.teams = teams;
    });
  }

  addTeam(): void {
    const teamFormData = new TeamFormData();

    this.editDialogService.openDialog<Team>(teamFormData, (team: Team) => {
      if (!team) return;
      this.busy = true;

      team = new Team(team);

      team.createdBy = this.authService.userSubject.value?.username;

      console.log('teamFromData', teamFormData);
      this.teamService.createNewTeam(team).subscribe((teamResp: ApiResponse<Team>) => {
        this.busy = false;

        console.log('teamResp', teamResp);
        if (!teamResp.data) return;
        this.teams.push(new Team(teamResp.data));
      });
    }).subscribe((res: any) => {

    });
  }
}

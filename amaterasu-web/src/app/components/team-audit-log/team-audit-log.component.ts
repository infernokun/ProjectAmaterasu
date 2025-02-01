import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TeamService } from '../../services/team/team.service';
import { Team } from '../../models/team.model';

@Component({
  selector: 'app-team-audit-log',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './team-audit-log.component.html',
  styleUrl: './team-audit-log.component.scss'
})
export class TeamAuditLogComponent {

  teams: Team[] = [];

  constructor(private teamService: TeamService) {
  }

  ngOnInit() {
    this.teamService.getAllTeams().subscribe((teams) => {
      this.teams = teams;
    });
  }
}

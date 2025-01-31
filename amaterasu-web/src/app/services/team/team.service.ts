import { Injectable } from '@angular/core';
import { EnvironmentService } from '../environment/environment.service';
import { BaseService } from '../base/base.service';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { Team } from '../../models/team.model';
import { ApiResponse } from '../../models/api-response.model';

@Injectable({
  providedIn: 'root'
})
export class TeamService extends BaseService {

  constructor(
    protected httpClient: HttpClient,
    private environmentService: EnvironmentService) {
    super(httpClient);
  }

  getAllTeams(): Observable<Team[]> {
    return this.get<ApiResponse<Team[]>>(this.environmentService.settings?.restUrl + '/team')
      .pipe(
        map((response: ApiResponse<Team[]>) => response.data.map((team) => new Team(team)))
      );
  }

  getTeamById(id: string): Observable<Team> {
    return this.get<ApiResponse<Team>>(this.environmentService.settings?.restUrl + '/team/' + id)
      .pipe(
        map((response: ApiResponse<Team>) => new Team(response.data))
      );
  }
}

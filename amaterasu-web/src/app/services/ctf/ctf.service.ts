import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, EMPTY, Observable, switchMap } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { CTFEntity } from '../../models/ctf/ctf-entity.model';
import { FlagAnswer } from '../../models/ctf/flag-answer.model';
import { BaseService } from '../base.service';
import { EnvironmentService } from '../environment.service';
import { CTFEntityAnswerResponse } from '../../models/dto/answered-ctfentity-response.model';
import { CTFEntityHintResponse } from '../../models/dto/ctf-entity-hint-response.model';
import { CTFEntityAnswer } from '../../models/ctf/ctf-entity-answer.model';

@Injectable({
  providedIn: 'root'
})
export class CTFService extends BaseService {
  public loadingSubject = new BehaviorSubject<boolean>(true);
  loading$ = this.loadingSubject.asObservable();

  requestCTFEntityUrl: string = '';
  requestCTFEntityAnswerUrl: string = '';

  constructor(
    protected httpClient: HttpClient,
    private environmentService: EnvironmentService
  ) {
    super(httpClient);
    this.requestCTFEntityUrl = this.environmentService.settings?.restUrl + '/ctf-entity';
    this.requestCTFEntityAnswerUrl = this.environmentService.settings?.restUrl + '/answer';
  }

  getAllChallenges(): Observable<ApiResponse<CTFEntity[]>> {
    return this.get<ApiResponse<CTFEntity[]>>(this.requestCTFEntityUrl);
  }

  getChallengesByRoom(roomId: string): Observable<ApiResponse<CTFEntity[]>> {
    return this.get<ApiResponse<CTFEntity[]>>(`${this.requestCTFEntityUrl}/by?room=${roomId}`);
  }

  answerChallenge(flag: FlagAnswer): Observable<ApiResponse<CTFEntityAnswerResponse>> {
    return this.post<ApiResponse<CTFEntityAnswerResponse>>(`${this.requestCTFEntityAnswerUrl}`, flag);
  }

  answerChallengeCheck(ctfEntity: CTFEntity, roomId: string): Observable<ApiResponse<CTFEntityAnswer>> {
    return this.get<ApiResponse<CTFEntityAnswer>>(`${this.requestCTFEntityAnswerUrl}/check?roomId=${roomId}&ctfEntityId=${ctfEntity.id}`);
  }

  useHint(hintId: string, roomId: string, userId: string, ctfEntityId: string): Observable<ApiResponse<CTFEntityHintResponse>> {
    return this.post<ApiResponse<CTFEntityHintResponse>>(`${this.requestCTFEntityUrl}/use-hint?hintId=${hintId}&roomId=${roomId}&userId=${userId}&ctfEntityId=${ctfEntityId}`, {});
  }
}

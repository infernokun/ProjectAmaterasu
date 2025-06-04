import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, EMPTY, Observable, switchMap } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { CTFEntity } from '../../models/ctf/ctf-entity.model';
import { FlagAnswer } from '../../models/ctf/flag-answer.model';
import { BaseService } from '../base.service';
import { EnvironmentService } from '../environment.service';
import { AnsweredCTFEntityResponse } from '../../models/dto/answered-ctfentity-response.model';

@Injectable({
  providedIn: 'root'
})
export class CTFService extends BaseService {
  public loadingSubject = new BehaviorSubject<boolean>(true);
  loading$ = this.loadingSubject.asObservable();

  constructor(
    protected httpClient: HttpClient,
    private environmentService: EnvironmentService
  ) {
    super(httpClient);
  }

  getAllChallenges(): Observable<ApiResponse<CTFEntity[]>> {
    return this.get<ApiResponse<CTFEntity[]>>(this.environmentService.settings?.restUrl + '/ctf-entity');
  }

  getChallengesByRoom(roomId: string): Observable<ApiResponse<CTFEntity[]>> {
    return this.get<ApiResponse<CTFEntity[]>>(this.environmentService.settings?.restUrl + '/ctf-entity/by?room=' + roomId);
  }

  answerChallenge(flag: FlagAnswer): Observable<ApiResponse<AnsweredCTFEntityResponse>> {
    console.log(flag)
    return this.post<ApiResponse<AnsweredCTFEntityResponse>>(this.environmentService.settings?.restUrl + '/answer', flag);
  }

  answerChallengeCheck(ctfEntity: CTFEntity, roomId: string): Observable<ApiResponse<any>> {
    return this.get<ApiResponse<any>>(this.environmentService.settings?.restUrl + `/answer/check?roomId=${roomId}&ctfEntityId=${ctfEntity.id}`);
  }

  useHint(hintId: String, roomId: string, userId: string, ctfEntityId: string): Observable<ApiResponse<any>> {
    return this.get<ApiResponse<any>>(this.environmentService.settings?.restUrl + `/answer/check?hintId=${hintId}&roomId=${roomId}&userId=${userId}&ctfEntityId=${ctfEntityId}`);
  }
}

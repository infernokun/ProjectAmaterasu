import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { Lab } from '../models/lab.model';
import { ApiResponse } from '../models/api-response.model';
import { LabActionResult } from '../models/lab-action-result.model';

@Injectable({
  providedIn: 'root'
})
export class LabDeploymentService {
  private deployLabSubject = new Subject<Lab>();
  deployLab$ = this.deployLabSubject.asObservable();

  private deployLabResponseSubject = new Subject<ApiResponse<LabActionResult>>();
  deployLabResponse$ = this.deployLabResponseSubject.asObservable();

  private labsLoadingSubject = new Subject<Set<string>>();
  labsLoading$ = this.labsLoadingSubject.asObservable();


  startLabDeployment(lab: Lab) {
    this.deployLabSubject.next(lab);
  }

  startLabDeploymentFinish(response: ApiResponse<LabActionResult>) {
    this.deployLabResponseSubject.next(response);
  }

  updateLabsLoading(labsLoading: Set<string>) {
    this.labsLoadingSubject.next(labsLoading);
  }
  
}

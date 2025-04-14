import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { Lab } from '../models/lab.model';

@Injectable({
  providedIn: 'root'
})
export class LabDeploymentService {
  private deployLabSubject = new Subject<Lab>();
  deployLab$ = this.deployLabSubject.asObservable();

  startLabDeployment(lab: Lab) {
    this.deployLabSubject.next(lab);
  }
}

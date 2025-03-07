import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { BehaviorSubject, map, Observable } from "rxjs";
import { ApiResponse } from "../models/api-response.model";
import { ApplicationInfo } from "../models/application-info.model";
import { EnvironmentService, EnvironmentSettings } from "./environment.service";
import { BaseService } from "./base.service";

@Injectable({
  providedIn: 'root'
})
export class AppInitService extends BaseService {
  initialized$: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);
  applicationInfo$: BehaviorSubject<ApplicationInfo> = new BehaviorSubject<ApplicationInfo>(new ApplicationInfo({}));

  constructor(protected httpClient: HttpClient, private environmentService: EnvironmentService) {
    super(httpClient);
    //this.load();
  }

  isInitialized(): Observable<boolean> {
    return this.initialized$.asObservable();
  }

  getApplicationInfo(): Observable<ApplicationInfo> {
    return this.applicationInfo$.asObservable();
  }

  initializationCheck(): Observable<ApplicationInfo> {
    return this.get<ApiResponse<ApplicationInfo>>(this.environmentService.settings?.restUrl + '/application-info').pipe(
      map((response: ApiResponse<ApplicationInfo>) => new ApplicationInfo(response.data))
    );
  }

  load(environmentService: EnvironmentService): void {
    this.get<ApiResponse<ApplicationInfo>>(environmentService.settings!.restUrl + '/application-info').subscribe(
      (response: ApiResponse<ApplicationInfo>) => {
        console.log('Received Application Info: ', response);
        if (response && response.data) {
          this.applicationInfo$.next(new ApplicationInfo(response.data));
          this.initialized$.next(true);
        } else {
          console.warn('No application info found');
          this.initialized$.next(false);
        }
      },
      (err: any) => {
        console.error('Error reading configuration file: ', err.message);
        this.initialized$.next(false);
      }
    );
  }

  createApplicationInfo(applicationInfo: ApplicationInfo): Observable<ApiResponse<ApplicationInfo>> {
    return this.post<ApiResponse<ApplicationInfo>>(this.environmentService.settings?.restUrl + '/application-info', applicationInfo);
  }
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { AuditEvent, Page } from '../../models/audit-event.model';
import { EnvironmentService } from '../../services/environment.service';

export interface AuditQuery {
  targetType?: string;
  targetId?: string;
  teamId?: string;
  /** One action or several, comma separated. */
  action?: string;
  /** ISO timestamps bounding the events. */
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/** The audit log. Admins get every event; everyone else gets their own team's. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);

  list(params: AuditQuery = {}): Observable<Page<AuditEvent>> {
    let query = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        query = query.set(key, String(value));
      }
    }
    return this.http
      .get<ApiResponse<Page<AuditEvent>>>(`${this.env.settings?.restUrl}/audit`, { params: query })
      .pipe(map(response => response.data));
  }
}

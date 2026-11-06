import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { Page } from '../../models/audit-event.model';
import { AppNotification } from '../../models/notification.model';
import { EnvironmentService } from '../../services/environment.service';

/** The signed-in user's own notifications. */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);

  private url(path = ''): string {
    return `${this.env.settings?.restUrl}/notifications${path}`;
  }

  list(page = 0, size = 20): Observable<Page<AppNotification>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<Page<AppNotification>>>(this.url(), { params }).pipe(map(response => response.data));
  }

  unreadCount(): Observable<number> {
    return this.http.get<ApiResponse<{ count: number }>>(this.url('/unread-count')).pipe(map(response => response.data.count));
  }

  markRead(id: string): Observable<AppNotification> {
    return this.http.post<ApiResponse<AppNotification>>(this.url(`/${id}/read`), {}).pipe(map(response => response.data));
  }

  markAllRead(): Observable<number> {
    return this.http.post<ApiResponse<{ count: number }>>(this.url('/read-all'), {}).pipe(map(response => response.data.count));
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../../../models/api-response.model';
import { AuthService } from '../../../services/auth.service';
import { EnvironmentService } from '../../../services/environment.service';

/** A one-time console for one VM, from POST /api/lab-tracker/{id}/vms/{vmid}/console. */
export interface ConsoleSession {
  sessionId: string;
  /** The relay's websocket path, under /socket-handler/. */
  path: string;
  /** The VNC password for this connection only. */
  password: string;
  vmid: number;
  vmName: string;
}

@Injectable({ providedIn: 'root' })
export class ConsoleService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(EnvironmentService);
  private readonly auth = inject(AuthService);

  open(trackerId: string, vmid: number | string): Observable<ConsoleSession> {
    return this.http
      .post<ApiResponse<ConsoleSession>>(`${this.env.settings?.restUrl}/lab-tracker/${trackerId}/vms/${vmid}/console`, {})
      .pipe(map(response => response.data));
  }

  /** Opens the relay socket for a console session. */
  connect(path: string): WebSocket {
    return new WebSocket(this.socketUrl(path), ['binary']);
  }

  /** The relay URL with the access token, which the handshake needs like the live update socket. */
  socketUrl(path: string): string {
    const configured = this.env.settings?.websocketUrl;
    const base = configured || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`;
    const token = this.auth.getAccessToken() ?? '';
    return `${base}${path}?access_token=${encodeURIComponent(token)}`;
  }
}

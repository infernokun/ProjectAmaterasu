import { Injectable, OnDestroy } from '@angular/core';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { EnvironmentService } from './environment.service';
import { AuthService } from './auth.service';
import { distinctUntilChanged, map, Observable, Subject, Subscription } from 'rxjs';

const RECONNECT_DELAY_MS = 5000;

@Injectable({
  providedIn: 'root',
})
export class WebsocketService implements OnDestroy {
  private clientFacingSubject$: Subject<any> = new Subject<any>();
  private socket$?: WebSocketSubject<any>;
  private socketSubscription?: Subscription;
  private userSubscription: Subscription;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private signedIn = false;

  constructor(
    private environmentService: EnvironmentService,
    private authService: AuthService
  ) {
    // The handshake needs an access token, so connect only while signed in
    this.userSubscription = this.authService.user$.pipe(
      map(user => user?.id),
      distinctUntilChanged()
    ).subscribe(userId => {
      this.signedIn = !!userId;
      this.disconnect();
      if (this.signedIn) {
        this.connect();
      }
    });
  }

  ngOnDestroy(): void {
    this.userSubscription.unsubscribe();
    this.disconnect();
  }

  /** Socket messages other than heartbeats. */
  public getMessage(): Observable<any> {
    return this.clientFacingSubject$.asObservable();
  }

  public getSubject(): Subject<any> {
    return this.clientFacingSubject$;
  }

  public disconnect(): void {
    clearTimeout(this.reconnectTimer);
    this.socketSubscription?.unsubscribe();
    this.socket$?.complete();
    this.socket$ = undefined;
  }

  private connect(): void {
    const token = this.authService.getAccessToken();
    if (!token) {
      return;
    }

    this.socket$ = webSocket({
      url: `${this.baseUrl()}/socket-handler/update?access_token=${encodeURIComponent(token)}`,
      closeObserver: { next: () => this.scheduleReconnect() }
    });
    this.socketSubscription = this.socket$.subscribe({
      next: (message) => {
        if (this.environmentService.settings?.production === false) {
          console.log('Got socket message', message);
        }
        if (!(message && message.type === 'heartbeat')) {
          this.clientFacingSubject$.next(message);
        }
      },
      error: () => this.scheduleReconnect()
    });
  }

  // Each attempt reads the current access token, since the old one may have expired
  private scheduleReconnect(): void {
    clearTimeout(this.reconnectTimer);
    if (this.signedIn) {
      this.reconnectTimer = setTimeout(() => {
        this.disconnect();
        this.connect();
      }, RECONNECT_DELAY_MS);
    }
  }

  // Same host as the page unless the environment names one; nginx proxies /socket-handler/ to the API
  private baseUrl(): string {
    const configured = this.environmentService.settings?.websocketUrl;
    if (configured) {
      return configured;
    }
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}`;
  }
}

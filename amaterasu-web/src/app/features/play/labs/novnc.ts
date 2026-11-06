import { InjectionToken } from '@angular/core';

/** The parts of noVNC's RFB client the console uses. */
export interface RfbClient extends EventTarget {
  scaleViewport: boolean;
  resizeSession: boolean;
  focusOnClick: boolean;
  background: string;
  disconnect(): void;
  sendCtrlAltDel(): void;
  focus(options?: FocusOptions): void;
}

export interface RfbOptions {
  credentials?: { password?: string };
  shared?: boolean;
  wsProtocols?: string[];
}

export type RfbConstructor = new (target: HTMLElement, channel: WebSocket | string, options?: RfbOptions) => RfbClient;

// noVNC is an ES module with top level await, served as is from /assets/novnc (see angular.json),
// so it loads only when a console opens and stays outside the bundle
const NOVNC_MODULE = 'assets/novnc/core/rfb.js';

export const RFB_LOADER = new InjectionToken<() => Promise<RfbConstructor>>('RFB_LOADER', {
  providedIn: 'root',
  factory: () => async () => {
    const url = new URL(NOVNC_MODULE, document.baseURI).href;
    const module = await import(/* @vite-ignore */ url);
    return module.default as RfbConstructor;
  },
});

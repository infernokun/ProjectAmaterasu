import { Observable, debounceTime, filter, map } from 'rxjs';

/** A room's points or attempts changed (sent by the API over the socket). */
export interface RoomActivityMessage {
  type: 'roomActivity';
  roomId: string;
}

export function isRoomActivity(message: unknown): message is RoomActivityMessage {
  return !!message && typeof message === 'object' && (message as { type?: unknown }).type === 'roomActivity';
}

/** Activity in the rooms `watching` accepts, at most once per burst. */
export function roomActivity(messages: Observable<unknown>, watching: (roomId: string) => boolean, wait = 800): Observable<string> {
  return messages.pipe(filter(isRoomActivity), map(message => message.roomId), filter(watching), debounceTime(wait));
}

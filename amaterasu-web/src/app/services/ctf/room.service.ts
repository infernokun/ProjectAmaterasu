import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { BaseService } from '../base.service';
import { Room } from '../../models/ctf/room.model';
import { EnvironmentService } from '../environment.service';
import { JoinRoomResponse } from '../../models/dto/join-room-response.model';
import { RoomUserResponse } from '../../models/ctf/room-user-response.model';


@Injectable({
  providedIn: 'root'
})
export class RoomService extends BaseService {
  private roomsSubject: BehaviorSubject<Room[] | undefined> = new BehaviorSubject<Room[] | undefined>(undefined);
  rooms$: Observable<Room[] | undefined> = this.roomsSubject.asObservable();

  private currentRoomUserSubject: BehaviorSubject<JoinRoomResponse | undefined> = new BehaviorSubject<JoinRoomResponse | undefined>(undefined);
  currentRoomUser$: Observable<JoinRoomResponse | undefined> = this.currentRoomUserSubject.asObservable();

  private currentRoomSubject: BehaviorSubject<Room | undefined> = new BehaviorSubject<Room | undefined>(undefined);
  currentRoom$: Observable<Room | undefined> = this.currentRoomSubject.asObservable();

  reqUrl: string = '';

  constructor(
    private httpClient: HttpClient,
    private environmentService: EnvironmentService) {
    super(httpClient);

    this.reqUrl = this.environmentService.settings?.restUrl + '/room';
  }

  getAllRooms(): Observable<ApiResponse<Room[]>> {
    return this.get<ApiResponse<Room[]>>(`${this.reqUrl}`);
  }

  /** Rooms the caller can run: every room for admins, the ones they facilitate otherwise. */
  getFacilitatedRooms(): Observable<ApiResponse<Room[]>> {
    return this.get<ApiResponse<Room[]>>(`${this.reqUrl}/facilitated`);
  }

  getRoomById(roomId: string): Observable<ApiResponse<Room>> {
    return this.get<ApiResponse<Room>>(`${this.reqUrl}/by?id=${roomId}`);
  }

  createRoom(room: Room): Observable<ApiResponse<Room>> {
    return this.post<ApiResponse<Room>>(`${this.reqUrl}`, room);
  }

  /** The caller's progress in every lab they may see, from the Labs list's cards. */
  getProgress(): Observable<ApiResponse<{ id: string; points: number; solved: number }[]>> {
    return this.get<ApiResponse<{ id: string; points: number; solved: number }[]>>(`${this.reqUrl}/cards`);
  }

  addNewRoom(room: Room): void {
    const currentRooms = this.roomsSubject.value || [];
    this.roomsSubject.next([...currentRooms, room]);
  }

  addRooms(rooms: Room[]): void {
    this.roomsSubject.next(rooms || []);
  }

  setCurrentRoomUser(roomUser: JoinRoomResponse): void {
    this.currentRoomUserSubject.next(roomUser);
  }

  peekCurrentRoomUser(): JoinRoomResponse | undefined {
    return this.currentRoomUserSubject.value ?? undefined;
  }

  getCurrentRoomUser(): JoinRoomResponse {
    if (!this.currentRoomUserSubject.value) {
      throw new Error('Current room user is not set');
    }

    return this.currentRoomUserSubject.value;
  }

  getCurrentRoom(): Room | undefined {
    return this.currentRoomSubject.value;
  }

  setCurrentRoom(room: Room): void {
    this.currentRoomSubject.next(room);
  }

  getRoomUsersForScoreboard(roomId: string): Observable<ApiResponse<RoomUserResponse[]>> {
    return this.get<ApiResponse<RoomUserResponse[]>>(`${this.reqUrl}/scoreboard/${roomId}/users`);
  }

  getRoomUserForScoreboard(roomId: string): Observable<ApiResponse<RoomUserResponse>>  {
    return this.get<ApiResponse<RoomUserResponse>>(`${this.reqUrl}/scoreboard/${roomId}`);
  }
}

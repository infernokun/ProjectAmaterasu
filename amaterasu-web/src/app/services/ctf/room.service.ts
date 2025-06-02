import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { BaseService } from '../base.service';
import { Room } from '../../models/ctf/room.model';
import { EnvironmentService } from '../environment.service';
import { RoomUserStatus } from '../../enums/room-user-status.enum';

interface JoinRoomResponse {
  roomId: string;
  userId: string;
  roomUserStatus: RoomUserStatus;
}

@Injectable({
  providedIn: 'root'
})
export class RoomService extends BaseService {
  private roomsSubject = new BehaviorSubject<Room[] | undefined>(undefined);
  rooms$: Observable<Room[] | undefined> = this.roomsSubject.asObservable();

  private roomJoinableSubject = new BehaviorSubject<Map<string, RoomUserStatus> | undefined>(undefined);
  roomJoinable$: Observable<Map<string, RoomUserStatus> | undefined> = this.roomJoinableSubject.asObservable();

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

  getRoomById(roomId: string): Observable<ApiResponse<Room>> {
    return this.get<ApiResponse<Room>>(`${this.reqUrl}/by?id=${roomId}`);
  }

  createRoom(room: Room): Observable<ApiResponse<Room>> {
    return this.post<ApiResponse<Room>>(`${this.reqUrl}`, room);
  }

  joinRoom(roomId: string, userId: string): Observable<ApiResponse<JoinRoomResponse>> {
    return this.post<ApiResponse<JoinRoomResponse>>(`${this.reqUrl}/join/${roomId}/${userId}`, { });
  }

  checkJoinable(userId: string, roomIds: string[]): Observable<ApiResponse<Map<string, RoomUserStatus>>> {
    return this.post<ApiResponse<Map<string, RoomUserStatus>>>(`${this.reqUrl}/check-joinable`, { userId: userId, roomIds: roomIds });
  }

  addNewRoom(room: Room): void {
    const currentRooms = this.roomsSubject.value || [];
    this.roomsSubject.next([...currentRooms, room]);
  }

  addRooms(rooms: Room[]): void {
    this.roomsSubject.next(rooms || []);
  }

  addRoomJoinable(roomId: string, roomUserStatus: RoomUserStatus): void {
    const currentJoinable = this.roomJoinableSubject.value || new Map<string, RoomUserStatus>();
    currentJoinable.set(roomId, roomUserStatus);
    this.roomJoinableSubject.next(currentJoinable);
  }

  addRoomJoinables(roomJoinables: Map<string, RoomUserStatus>): void {
    this.roomJoinableSubject.next(roomJoinables || new Map<string, RoomUserStatus>());
  }
}

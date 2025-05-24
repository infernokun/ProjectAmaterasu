import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { ApiResponse } from '../../models/api-response.model';
import { BaseService } from '../base.service';
import { Room } from '../../models/ctf/room.model';
import { EnvironmentService } from '../environment.service';

@Injectable({
  providedIn: 'root'
})
export class RoomService extends BaseService {

  private roomsSubject = new BehaviorSubject<Room[] | undefined>(undefined);
  rooms$: Observable<Room[] | undefined> = this.roomsSubject.asObservable();

  constructor(
    private httpClient: HttpClient,
    private environmentService: EnvironmentService) {
    super(httpClient);
  }

  getAllRooms(): Observable<ApiResponse<Room[]>> {
    return this.get<ApiResponse<Room[]>>(this.environmentService.settings?.restUrl + '/room');
  }

  createRoom(room: Room): Observable<ApiResponse<Room>> {
    return this.post<ApiResponse<Room>>(this.environmentService.settings?.restUrl + '/room', room);
  }

  addNewRoom(room: Room): void {
    const currentRooms = this.roomsSubject.value || [];
    this.roomsSubject.next([...currentRooms, room]);
  }

  addRooms(rooms: Room[]): void {
    this.roomsSubject.next(rooms || []);
  }
}

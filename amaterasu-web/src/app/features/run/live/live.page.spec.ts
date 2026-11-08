import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { RoomsStore } from '../../../core/stores/rooms.store';
import { AuthService } from '../../../services/auth.service';
import { RoomService } from '../../../services/ctf/room.service';
import { WebsocketService } from '../../../services/websocket.service';
import { FakeAuthService } from '../../../testing/fake-auth';
import { PlayApiService } from '../../play/data/play-api.service';
import { RoomLive } from '../../play/data/play.models';
import { LivePage } from './live.page';

const live = (roomId: string, roomName: string): RoomLive => ({
  roomId, roomName, playerCount: 0, teamCount: 0, solvesRecent: 0, solvesTotal: 0, wrongRecent: 0, topWrong: null,
  hintsUnlocked: 0, hintPoints: 0, feed: [], challenges: [], players: [],
});

describe('LivePage room picker', () => {
  let api: jasmine.SpyObj<PlayApiService>;
  let rooms: jasmine.SpyObj<RoomService>;
  const auth = new FakeAuthService();

  function create(facilitated: { id: string; name: string }[], currentRoom?: { id: string }) {
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['live', 'allTrackers', 'teams']);
    api.live.and.callFake((id: string) => of(live(id, facilitated.find(room => room.id === id)?.name ?? id)));
    api.allTrackers.and.returnValue(of([]));
    api.teams.and.returnValue(of([]));
    rooms = jasmine.createSpyObj<RoomService>('RoomService', ['getFacilitatedRooms']);
    rooms.getFacilitatedRooms.and.returnValue(of({ code: 200, message: '', data: facilitated } as any));
    TestBed.configureTestingModule({
      imports: [LivePage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: PlayApiService, useValue: api },
        { provide: RoomService, useValue: rooms },
        { provide: RoomsStore, useValue: { load: () => undefined, currentRoom: () => currentRoom, rooms: () => [] } },
        { provide: WebsocketService, useValue: { getMessage: () => new Subject().asObservable() } },
      ],
    });
    const fixture = TestBed.createComponent(LivePage);
    return fixture;
  }

  async function settle(fixture: ReturnType<typeof create>) {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('only lists the rooms the facilitator runs', async () => {
    auth.signIn({ id: 'f1', username: 'dkim' }, 'FACILITATOR');
    const fixture = create([{ id: 'r1', name: 'Winter Range' }, { id: 'r2', name: 'Spring Cup' }]);
    await settle(fixture);
    const options = [...(fixture.nativeElement as HTMLElement).querySelectorAll('#room-pick option')].map(o => o.textContent!.trim());
    expect(options).toEqual(['Winter Range', 'Spring Cup']);
    expect(api.live).toHaveBeenCalledWith('r1');
  });

  it('skips a current room the facilitator does not run', async () => {
    auth.signIn({ id: 'f1', username: 'dkim' }, 'FACILITATOR');
    const fixture = create([{ id: 'r2', name: 'Spring Cup' }], { id: 'r1' });
    await settle(fixture);
    expect(api.live).toHaveBeenCalledWith('r2');
    expect(api.live).not.toHaveBeenCalledWith('r1');
  });

  it('tells a facilitator with no rooms how to get one', async () => {
    auth.signIn({ id: 'f1', username: 'dkim' }, 'FACILITATOR');
    const fixture = create([]);
    await settle(fixture);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ask a lab\'s author to add you as a facilitator');
    expect(api.live).not.toHaveBeenCalled();
  });
});

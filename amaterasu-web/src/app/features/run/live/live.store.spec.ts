import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { Team } from '../../../models/team.model';
import { WebsocketService } from '../../../services/websocket.service';
import { PlayApiService } from '../../play/data/play-api.service';
import { RoomLive } from '../../play/data/play.models';
import { LiveStore } from './live.store';

describe('LiveStore', () => {
  let socket: Subject<unknown>;
  let api: jasmine.SpyObj<PlayApiService>;
  const live: RoomLive = {
    roomId: 'r1', roomName: 'Winter Range', playerCount: 3, teamCount: 2, solvesRecent: 1, solvesTotal: 4, wrongRecent: 2,
    topWrong: null, hintsUnlocked: 0, hintPoints: 0, feed: [
      { kind: 'SOLVE', at: '2026-11-04T10:00:00Z', who: 'priya.n', team: 'Red Fox', text: 'solved X', detail: null, points: 200 },
      { kind: 'AWARD', at: '2026-11-04T09:00:00Z', who: 'dkim', team: 'Red Fox', text: 'awarded priya.n', detail: 'Writeup', points: 50 },
    ], challenges: [], players: [],
  };
  const tracker = (id: string, team: string, lab: string, status: string, server = { id: 's1', name: 'dock-01', remoteServerStats: { status: 'ACTIVE' } }) =>
    new LabTracker({ id, labStatus: status, labOwner: { id: team, name: team.toUpperCase() }, labStarted: { id: lab, name: lab }, remoteServer: server });

  beforeEach(() => {
    socket = new Subject();
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['live', 'allTrackers', 'teams']);
    api.live.and.returnValue(of(live));
    api.allTrackers.and.returnValue(of([
      tracker('1', 'red', 'web', 'ACTIVE'),
      tracker('2', 'blue', 'web', 'FAILED'),
      tracker('3', 'blue', 'ad', 'ACTIVE', { id: 's2', name: 'pve-a', remoteServerStats: { status: 'OFFLINE' } }),
    ]));
    api.teams.and.returnValue(of([new Team({ id: 'red', name: 'RED' }), new Team({ id: 'blue', name: 'BLUE' }), new Team({ id: 'idle', name: 'IDLE' })]));
    TestBed.configureTestingModule({
      providers: [LiveStore, { provide: PlayApiService, useValue: api }, { provide: WebsocketService, useValue: { getMessage: () => socket.asObservable() } }],
    });
  });

  async function loaded() {
    const store = TestBed.inject(LiveStore);
    await store.load('r1');
    return store;
  }

  it('raises failed labs and offline servers, and lets them be dismissed', async () => {
    const store = await loaded();
    expect(store.alerts().map(alert => alert.title)).toEqual(['web is failed for BLUE', 'pve-a is offline']);
    store.dismiss(store.alerts()[1].id);
    expect(store.alerts().length).toBe(1);
  });

  it('lays out a team by lab matrix for teams with labs', async () => {
    const store = await loaded();
    const matrix = store.matrix();
    expect(matrix.columns.map(column => column.name)).toEqual(['ad', 'web']);
    expect(matrix.rows.map(row => [row.team, ...row.cells.map(cell => cell.label)])).toEqual([['BLUE', 'Active', 'Failed'], ['RED', 'None', 'Active']]);
    expect(store.labsRunning()).toBe(2);
  });

  it('shows a lab\'s own teams, which are not site teams, in the matrix', async () => {
    api.allTrackers.and.returnValue(of([tracker('1', 'red', 'web', 'ACTIVE'), tracker('4', 'owls', 'web', 'PAUSED')]));
    const store = await loaded();
    expect(store.matrix().rows.map(row => row.team)).toEqual(['OWLS', 'RED']);
  });

  it('applies tracker changes for every team from the socket', async () => {
    const store = await loaded();
    socket.next({ type: 'labTracker', action: 'updated', labTracker: { id: '2', labStatus: 'ACTIVE', labOwner: { id: 'blue' }, labStarted: { id: 'web', name: 'web' } } });
    socket.next({ type: 'labTracker', action: 'removed', labTracker: { id: '3' } });
    expect(store.failed()).toBe(0);
    expect(store.trackers().length).toBe(2);
  });

  it('filters the feed', async () => {
    const store = await loaded();
    store.setFeedFilter('AWARD');
    expect(store.feed().map(item => item.who)).toEqual(['dkim']);
  });
});

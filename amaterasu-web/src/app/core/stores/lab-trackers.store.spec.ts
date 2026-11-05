import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { LabStatus } from '../../enums/lab-status.enum';
import { LabTracker } from '../../models/lab/lab-tracker.model';
import { AuthService } from '../../services/auth.service';
import { LabTrackerService } from '../../services/lab/lab-tracker.service';
import { WebsocketService } from '../../services/websocket.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { LabTrackersStore } from './lab-trackers.store';

describe('LabTrackersStore', () => {
  let auth: FakeAuthService;
  let socket: Subject<unknown>;
  let trackerService: jasmine.SpyObj<LabTrackerService>;

  const tracker = (id: string, status: LabStatus, teamId = 't1') =>
    new LabTracker({ id, labStatus: status, labOwner: { id: teamId }, labStarted: { name: id } });

  beforeEach(() => {
    auth = new FakeAuthService();
    socket = new Subject();
    trackerService = jasmine.createSpyObj<LabTrackerService>('LabTrackerService', ['getLabTrackersByTeam']);
    trackerService.getLabTrackersByTeam.and.returnValue(of([tracker('a', LabStatus.ACTIVE), tracker('b', LabStatus.PAUSED)]));
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: LabTrackerService, useValue: trackerService },
        { provide: WebsocketService, useValue: { getMessage: () => socket.asObservable() } },
      ],
    });
  });

  function signedInStore() {
    auth.signIn({ id: 'u1', team: { id: 't1', name: 'Red Fox' } }, 'MEMBER');
    const store = TestBed.inject(LabTrackersStore);
    TestBed.tick();
    return store;
  }

  it("loads the team's labs once signed in", () => {
    const store = signedInStore();
    expect(trackerService.getLabTrackersByTeam).toHaveBeenCalledWith('t1');
    expect(store.teamLabs().map(t => t.id)).toEqual(['a', 'b']);
    expect(store.runningCount()).toBe(1);
  });

  it('applies socket updates for its team and ignores other teams', () => {
    const store = signedInStore();
    socket.next({ type: 'labTracker', action: 'updated', labTracker: { id: 'b', labStatus: 'ACTIVE', labOwner: { id: 't1' } } });
    socket.next({ type: 'labTracker', action: 'updated', labTracker: { id: 'x', labStatus: 'ACTIVE', labOwner: { id: 't2' } } });
    socket.next({ type: 'heartbeat' });

    expect(store.byId().get('b')?.labStatus).toBe(LabStatus.ACTIVE);
    expect(store.byId().has('x')).toBeFalse();
    expect(store.runningCount()).toBe(2);
  });

  it('drops removed and deleted trackers', () => {
    const store = signedInStore();
    socket.next({ type: 'labTracker', action: 'removed', labTracker: { id: 'a', labOwner: { id: 't1' } } });
    socket.next({ type: 'labTracker', action: 'updated', labTracker: { id: 'b', labStatus: 'DELETED', labOwner: { id: 't1' } } });
    expect(store.teamLabs()).toEqual([]);
  });

  it('shows a request in flight on that lab only', () => {
    const store = signedInStore();
    const [a, b] = store.teamLabs();
    store.setPending('a', 'PAUSING');
    expect(store.displayState()(a)).toBe('PAUSING');
    expect(store.displayState()(b)).toBe(LabStatus.PAUSED);
    store.clearPending('a');
    expect(store.displayState()(a)).toBe(LabStatus.ACTIVE);
  });

  it('empties when the user signs out', () => {
    const store = signedInStore();
    auth.signOutNow();
    TestBed.tick();
    expect(store.trackers()).toEqual([]);
  });
});

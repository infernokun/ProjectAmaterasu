import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { LabStatus } from '../../enums/lab-status.enum';
import { LabTracker } from '../../models/lab/lab-tracker.model';
import { AuthService } from '../../services/auth.service';
import { LabTrackerService } from '../../services/lab/lab-tracker.service';
import { LabService } from '../../services/lab/lab.service';
import { WebsocketService } from '../../services/websocket.service';
import { FakeAuthService } from '../../testing/fake-auth';
import { LabTrackersStore } from './lab-trackers.store';

describe('LabTrackersStore actions', () => {
  let auth: FakeAuthService;
  let labService: jasmine.SpyObj<LabService>;
  const tracker = (status: LabStatus) =>
    new LabTracker({ id: 'a', labStatus: status, labOwner: { id: 't1' }, labStarted: { id: 'lab1', name: 'Web Exploitation 101' }, remoteServer: { id: 's1', name: 'dock-01' } });

  beforeEach(() => {
    auth = new FakeAuthService();
    labService = jasmine.createSpyObj<LabService>('LabService', ['startLab', 'pauseLab', 'stopLab', 'deleteLab']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: LabService, useValue: labService },
        { provide: LabTrackerService, useValue: { getLabTrackersByTeam: () => of([tracker(LabStatus.ACTIVE)]) } },
        { provide: WebsocketService, useValue: { getMessage: () => new Subject().asObservable() } },
      ],
    });
    auth.signIn({ id: 'u1', team: { id: 't1', name: 'Red Fox' } }, 'MEMBER');
  });

  function store() {
    const instance = TestBed.inject(LabTrackersStore);
    TestBed.tick();
    return instance;
  }

  it('shows Pausing on that lab only while the request runs, then the new state', async () => {
    const response = new Subject<any>();
    labService.pauseLab.and.returnValue(response);
    const trackers = store();
    const pending = trackers.pause(trackers.teamLabs()[0]);
    expect(trackers.displayState()(trackers.teamLabs()[0])).toBe('PAUSING');

    response.next({ data: { isSuccessful: true, labTracker: { ...tracker(LabStatus.PAUSED) } } });
    response.complete();
    const outcome = await pending;
    expect(outcome.ok).toBeTrue();
    expect(outcome.message).toContain('Its progress is kept');
    expect(trackers.displayState()(trackers.teamLabs()[0])).toBe(LabStatus.PAUSED);
    expect(labService.pauseLab).toHaveBeenCalledWith(jasmine.objectContaining({ labTrackerId: 'a', remoteServerId: 's1', teamId: 't1' }));
  });

  it('reports the server output when an action fails', async () => {
    labService.stopLab.and.returnValue(of({ data: { isSuccessful: false, output: 'ssh: connect timed out', labTracker: tracker(LabStatus.FAILED) } } as any));
    const trackers = store();
    const outcome = await trackers.stop(trackers.teamLabs()[0]);
    expect(outcome.ok).toBeFalse();
    expect(outcome.message).toBe('ssh: connect timed out');
    expect(trackers.teamLabs()[0].labStatus).toBe(LabStatus.FAILED);
  });

  it('reads the reason from an error response', async () => {
    labService.startLab.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { message: 'Lab did not start successfully.', data: { isSuccessful: false, output: 'dock-01 is not ACTIVE!' } },
    })));
    const trackers = store();
    const outcome = await trackers.start('lab2', 'Log Hunting', 's1');
    expect(outcome.ok).toBeFalse();
    expect(outcome.message).toBe('dock-01 is not ACTIVE!');
    expect(trackers.starting()).toEqual({});
  });

  it('drops a lab removed from the team', async () => {
    labService.deleteLab.and.returnValue(of({ data: { isSuccessful: true, labTracker: { ...tracker(LabStatus.DELETED) } } } as any));
    const trackers = store();
    const outcome = await trackers.removeFromTeam(trackers.teamLabs()[0]);
    expect(outcome.message).toBe('Web Exploitation 101 removed from Red Fox');
    expect(trackers.teamLabs()).toEqual([]);
  });
});

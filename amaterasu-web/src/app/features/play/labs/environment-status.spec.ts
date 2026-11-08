import { LabStatus } from '../../../enums/lab-status.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { EnvironmentView } from '../data/play.models';
import { environmentStatus, withTrackerChange } from './environment-status';

describe('environmentStatus', () => {
  const tracker = (labStatus: LabStatus, extra: object = {}) => new LabTracker({ id: 't1', labStatus, ...extra });

  it('maps every stored status to one of five, like the API', () => {
    expect(environmentStatus(null)).toBe('NOT_RUNNING');
    expect(environmentStatus(tracker(LabStatus.STOPPED))).toBe('NOT_RUNNING');
    expect(environmentStatus(tracker(LabStatus.ACTIVE))).toBe('RUNNING');
    expect(environmentStatus(tracker(LabStatus.DEGRADED))).toBe('RUNNING');
    expect(environmentStatus(tracker(LabStatus.PAUSED))).toBe('PAUSED');
    expect(environmentStatus(tracker(LabStatus.FAILED))).toBe('FAILED');
  });

  it('shows Starting for a start in progress anywhere, or a VM still booting', () => {
    expect(environmentStatus(tracker(LabStatus.STOPPED, { pendingAction: 'STARTING' }))).toBe('STARTING');
    expect(environmentStatus(tracker(LabStatus.PAUSED), 'RESUMING')).toBe('STARTING');
    expect(environmentStatus(tracker(LabStatus.ACTIVE, { pendingAction: 'STOPPING' }))).toBe('RUNNING');
    expect(environmentStatus(tracker(LabStatus.ACTIVE, { vms: [{ vmid: 1, status: 'running', guestAgent: true, ready: false }] })))
      .toBe('STARTING');
  });
});

describe('withTrackerChange', () => {
  const shared: EnvironmentView = {
    labId: 'def1', labName: 'Hunt', labType: 'VIRTUAL_MACHINE', mode: 'SHARED', status: 'NOT_RUNNING', trackerId: null,
    tracker: null, canControl: false, note: null, copiesRunning: 0,
  };

  it('picks up a new shared copy for this lab only', () => {
    const copy = new LabTracker({ id: 't9', labStatus: LabStatus.ACTIVE, labStarted: { id: 'def1' }, roomId: 'lab1' });
    expect(withTrackerChange(shared, 'other', copy, false, null)).toBe(shared);
    const changed = withTrackerChange(shared, 'lab1', copy, false, null);
    expect(changed.trackerId).toBe('t9');
    expect(changed.status).toBe('RUNNING');
    expect(withTrackerChange(changed, 'lab1', copy, true, null).trackerId).toBeNull();
  });

  it("follows the viewer's team copy, not another team's", () => {
    const perTeam: EnvironmentView = { ...shared, mode: 'PER_TEAM' };
    const theirs = new LabTracker({ id: 't2', labStatus: LabStatus.ACTIVE, labStarted: { id: 'def1' }, labOwner: { id: 'blue' } });
    expect(withTrackerChange(perTeam, 'lab1', theirs, false, 'red')).toBe(perTeam);
    expect(withTrackerChange(perTeam, 'lab1', theirs, false, 'blue').trackerId).toBe('t2');
  });
});

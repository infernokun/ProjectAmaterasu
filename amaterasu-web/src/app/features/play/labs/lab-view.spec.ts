import { LabStatus } from '../../../enums/lab-status.enum';
import { LabType } from '../../../enums/lab-type.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { RemoteServer } from '../../../models/lab/remote-server.model';
import { bestServer, labActions, labDetail, lifeStep, serviceName } from './lab-view';

describe('lab view rules', () => {
  const compose = (status: LabStatus, services: object[] = []) => new LabTracker({
    id: 'lt1', labStatus: status, services, labStarted: { labType: LabType.DOCKER_COMPOSE, name: 'Log Hunting' },
  });

  it('offers Pause and Stop while running, Resume when paused, a fresh copy when stopped', () => {
    expect(labActions(compose(LabStatus.ACTIVE), LabStatus.ACTIVE)).toEqual(jasmine.objectContaining({ canPause: true, canStop: true, canResume: false, canRemove: false }));
    expect(labActions(compose(LabStatus.PAUSED), LabStatus.PAUSED)).toEqual(jasmine.objectContaining({ canResume: true, canStop: true, canRemove: true, canPause: false }));
    expect(labActions(compose(LabStatus.STOPPED), LabStatus.STOPPED)).toEqual(jasmine.objectContaining({ canStartFresh: true, canRemove: true, canStop: false }));
  });

  it('offers nothing but a working note while a request runs', () => {
    const can = labActions(compose(LabStatus.ACTIVE), 'PAUSING');
    expect(can.working).toBeTrue();
    expect(can.canPause || can.canStop || can.canResume || can.canOpen).toBeFalse();
  });

  it('offers a restart for the service that went down', () => {
    const tracker = compose(LabStatus.DEGRADED, [
      { name: 'lt1-web-1', service: 'web', state: 'running' },
      { name: 'lt1-kibana-1', state: 'exited' },
    ]);
    expect(labActions(tracker, LabStatus.DEGRADED).canRestart).toBeTrue();
    expect(serviceName(tracker, tracker.services![1])).toBe('kibana');
    expect(labDetail(tracker, LabStatus.DEGRADED)).toBe('1 of 2 services running. kibana is not running.');
  });

  it('places the lifecycle step', () => {
    expect(lifeStep('RESUMING')).toBe('run');
    expect(lifeStep(LabStatus.PAUSED)).toBe('pause');
    expect(lifeStep('STOPPING')).toBe('stop');
    expect(lifeStep(LabStatus.FAILED)).toBeNull();
  });

  it('starts on the online server with the most free memory', () => {
    const server = (name: string, status: LabStatus, availableRam: number) =>
      new RemoteServer({ id: name, name, remoteServerStats: { status, availableRam } });
    expect(bestServer([server('a', LabStatus.ACTIVE, 2), server('b', LabStatus.OFFLINE, 64), server('c', LabStatus.ACTIVE, 8)])?.name).toBe('c');
    expect(bestServer([server('b', LabStatus.OFFLINE, 64)])).toBeUndefined();
  });
});

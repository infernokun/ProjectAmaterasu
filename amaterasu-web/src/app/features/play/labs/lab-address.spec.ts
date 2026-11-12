import { LabType } from '../../../enums/lab-type.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { LabCard } from '../data/play.models';
import { consoleVm, labConsoleLink, labDetailsLink, labForCopy } from './lab-address';

const card = (id: string, labId: string, trackerId: string | null = null) => ({
  id, name: id, environment: { labId, trackerId },
}) as unknown as LabCard;

describe('lab addresses', () => {
  it('puts details and consoles under the lab, with the copy only when one is named', () => {
    expect(labDetailsLink('r1')).toEqual({ commands: ['/labs', 'r1', 'details'], queryParams: {} });
    expect(labDetailsLink('r1', 't1')).toEqual({ commands: ['/labs', 'r1', 'details'], queryParams: { copy: 't1' } });
    expect(labConsoleLink('r1', 101, 't1')).toEqual({ commands: ['/labs', 'r1', 'console', 101], queryParams: { copy: 't1' } });
  });

  it('opens the console on the running bastion first, else the first running VM', () => {
    const vmLab = (vms: object[]) => new LabTracker({ id: 't1', labStarted: { labType: LabType.VIRTUAL_MACHINE }, vms });
    expect(consoleVm(vmLab([{ vmid: 1, status: 'running' }, { vmid: 2, status: 'running', role: 'bastion' }]))?.vmid).toBe(2);
    expect(consoleVm(vmLab([{ vmid: 1, status: 'stopped', role: 'bastion' }, { vmid: 3, status: 'running' }]))?.vmid).toBe(3);
    expect(consoleVm(vmLab([{ vmid: 1, status: 'stopped' }]))).toBeNull();
    expect(consoleVm(new LabTracker({ id: 't1', labStarted: { labType: LabType.DOCKER_COMPOSE }, vms: [] }))).toBeNull();
  });

  describe('the lab a copy belongs to', () => {
    const teamCopy = new LabTracker({ id: 't2', labStarted: { id: 'env1' } });

    it('is named on a shared copy', () => {
      expect(labForCopy(new LabTracker({ id: 't1', roomId: 'r9', labStarted: { id: 'env1' } }), [])).toBe('r9');
    });

    it('is the lab whose own copy it is, then the current lab, then any lab running that environment', () => {
      const cards = [card('a', 'env1'), card('b', 'env1', 't2'), card('c', 'env2')];
      expect(labForCopy(teamCopy, cards)).toBe('b');
      expect(labForCopy(teamCopy, [card('a', 'env1'), card('c', 'env1')], 'c')).toBe('c');
      expect(labForCopy(teamCopy, [card('a', 'env1'), card('c', 'env1')])).toBe('a');
    });

    it('is none when no lab runs that environment', () => {
      expect(labForCopy(teamCopy, [card('c', 'env2')])).toBeNull();
    });
  });
});

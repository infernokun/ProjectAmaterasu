import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuditService } from '../../../core/services/audit.service';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabType } from '../../../enums/lab-type.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { PlayApiService } from '../data/play-api.service';
import { RoomPlayStore } from '../rooms/room-play.store';
import { LabConsolePage } from './lab-console.page';

describe('LabConsolePage VMs', () => {
  let fixture: ComponentFixture<LabConsolePage>;
  let state: string;

  const bastion = (address: string | null, ready: boolean) => ({
    vmid: 105, name: 'red-fox-pivot-tpl-bastion', status: 'running', role: 'bastion', ciuser: 'player', guestAgent: true,
    ready, accessAddress: address, uplinkBridge: 'vmbr0', labSubnet: '172.16.0.0/24', cpus: 1, maxmem: 536870912,
  });
  const tracker = (vms: object[]) => new LabTracker({
    id: 'lt1', labStatus: LabStatus.ACTIVE, labStarted: { id: 'lab1', name: 'Pivot', labType: LabType.VIRTUAL_MACHINE },
    remoteServer: { name: 'pve-a' }, bridges: ['abr0'], vms,
  });

  /** Inside a lab when `own` is given: the viewer's own copy there. */
  function render(current: LabTracker, own?: string | null) {
    TestBed.configureTestingModule({
      imports: [LabConsolePage],
      providers: [
        provideRouter([]),
        { provide: LabTrackersStore, useValue: { byId: () => new Map([['lt1', current]]), displayState: () => () => state } },
        { provide: PlayApiService, useValue: { tracker: () => of(current), challengesForLab: () => of([]), access: () => of({}) } },
        { provide: AuditService, useValue: { list: () => of({ content: [] }) } },
        ...(own !== undefined ? [{ provide: RoomPlayStore, useValue: { roomId: () => 'r1', overview: () => ({ environment: { trackerId: own } }) } }] : []),
      ],
    });
    fixture = TestBed.createComponent(LabConsolePage);
    if (own === undefined) fixture.componentRef.setInput('trackerId', 'lt1');
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const badges = () => Array.from(el().querySelectorAll('app-status-badge')).map(badge => badge.textContent?.trim());

  beforeEach(() => (state = LabStatus.ACTIVE));

  it('shows SSH access through the bastion and the uplink in the bridge note', () => {
    render(tracker([bastion('10.0.0.42', true), { vmid: 106, name: 'red-fox-pivot-tpl-target', status: 'running', guestAgent: true, ready: true }]));
    expect(el().textContent).toContain('SSH access');
    expect(el().textContent).toContain('ssh player@10.0.0.42');
    expect(el().textContent).toContain('Bastion');
    expect(el().textContent).toContain('The bastion red-fox-pivot-tpl-bastion is the one way in');
    expect(el().textContent).toContain('vmbr0');
  });

  it('says Starting up until the guest agent answers, then Ready', () => {
    render(tracker([bastion(null, false), { vmid: 106, name: 'red-fox-pivot-tpl-target', status: 'running', guestAgent: true, ready: true },
      { vmid: 107, name: 'cirros', status: 'running' }]));
    expect(badges()).toContain('Starting up');
    expect(badges()).toContain('Ready');
    expect(badges()).toContain('running');
    expect(el().textContent).toContain("Waiting for the bastion's address");
  });

  it('hides access while the lab is paused', () => {
    state = LabStatus.PAUSED;
    render(tracker([{ ...bastion('10.0.0.42', false), status: 'stopped' }]));
    expect(el().textContent).not.toContain('SSH access');
    expect(badges()).toContain('stopped');
  });

  it('keeps the plain bridge note without a bastion', () => {
    render(tracker([{ vmid: 106, name: 'target', status: 'running' }]));
    expect(el().textContent).toContain('cannot reach other teams or the Proxmox node');
    expect(el().textContent).not.toContain('one way in');
    expect(el().textContent).not.toContain('SSH access');
  });

  describe('inside a lab', () => {
    it('shows the viewer\'s own copy with consoles under the lab and no All labs link', () => {
      render(tracker([bastion('10.0.0.42', true)]), 'lt1');
      expect(el().textContent).toContain('Pivot');
      const open = Array.from(el().querySelectorAll<HTMLAnchorElement>('a.btn')).find(a => a.textContent?.includes('Open console'))!;
      expect(open.getAttribute('href')).toBe('/labs/r1/console/105?copy=lt1');
      expect(el().textContent).not.toContain('All labs');
    });

    it('shows the copy in ?copy= over the viewer\'s own', () => {
      render(tracker([bastion('10.0.0.42', true)]), 'mine');
      fixture.componentRef.setInput('copy', 'lt1');
      fixture.detectChanges();
      expect(el().textContent).toContain('Pivot');
    });

    it('says there is no copy yet when the viewer has none', () => {
      render(tracker([]), null);
      expect(el().textContent).toContain('No copy to show yet');
    });
  });
});

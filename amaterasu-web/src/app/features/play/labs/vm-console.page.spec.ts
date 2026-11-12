import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LabTrackersStore } from '../../../core/stores/lab-trackers.store';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabType } from '../../../enums/lab-type.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { PlayApiService } from '../data/play-api.service';
import { RoomPlayStore } from '../rooms/room-play.store';
import { ConsoleService } from './console.service';
import { RFB_LOADER, RfbOptions } from './novnc';
import { VmConsolePage } from './vm-console.page';

class FakeRfb extends EventTarget {
  static last: FakeRfb | null = null;
  scaleViewport = false;
  resizeSession = true;
  focusOnClick = false;
  background = '';
  readonly disconnect = jasmine.createSpy('disconnect');
  readonly sendCtrlAltDel = jasmine.createSpy('sendCtrlAltDel');
  readonly focus = jasmine.createSpy('focus');

  constructor(readonly target: HTMLElement, readonly channel: WebSocket, readonly options: RfbOptions) {
    super();
    FakeRfb.last = this;
  }
}

class FakeSocket extends EventTarget {
  close() {
    this.dispatchEvent(Object.assign(new Event('close'), { code: 4502, reason: 'pve-a did not accept the console connection.' }));
  }
}

describe('VmConsolePage', () => {
  let fixture: ComponentFixture<VmConsolePage>;
  let consoles: jasmine.SpyObj<ConsoleService>;
  let socket: FakeSocket;
  const tracker = (labType = LabType.VIRTUAL_MACHINE, status = 'running') => new LabTracker({
    id: 'lt1', labStatus: LabStatus.ACTIVE, labStarted: { id: 'lab1', name: 'AD Forest Breach', labType },
    remoteServer: { name: 'pve-a' }, vms: [{ vmid: 101, name: 'kali', status }, { vmid: 102, name: 'dc01', status: 'running' }],
  });

  /** Inside a lab when `lab` is set: its id and the viewer's own copy. */
  function render(current: LabTracker, vmid = '101', lab?: { own: string | null; copy?: string }) {
    socket = new FakeSocket();
    FakeRfb.last = null;
    consoles = jasmine.createSpyObj<ConsoleService>('ConsoleService', ['open', 'connect']);
    consoles.open.and.returnValue(of({ sessionId: 's1', path: '/socket-handler/console/s1', password: 'PVEVNC:t', vmid: 101, vmName: 'kali' }));
    consoles.connect.and.returnValue(socket as unknown as WebSocket);
    TestBed.configureTestingModule({
      imports: [VmConsolePage],
      providers: [
        provideRouter([]),
        { provide: LabTrackersStore, useValue: { byId: () => new Map([['lt1', current]]) } },
        { provide: PlayApiService, useValue: { tracker: () => of(current) } },
        { provide: ConsoleService, useValue: consoles },
        { provide: RFB_LOADER, useValue: () => Promise.resolve(FakeRfb) },
        ...(lab ? [{ provide: RoomPlayStore, useValue: { roomId: () => 'r1', overview: () => ({ environment: { trackerId: lab.own } }) } }] : []),
      ],
    });
    fixture = TestBed.createComponent(VmConsolePage);
    if (lab) {
      if (lab.copy) fixture.componentRef.setInput('copy', lab.copy);
    } else {
      fixture.componentRef.setInput('trackerId', 'lt1');
    }
    fixture.componentRef.setInput('vmid', vmid);
    fixture.detectChanges();
  }

  async function settle() {
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('connects through the relay with the one-time password and shows when it is connected', async () => {
    render(tracker());
    expect(text()).toContain('Connecting to kali');
    await settle();
    expect(consoles.open).toHaveBeenCalledWith('lt1', '101');
    expect(consoles.connect).toHaveBeenCalledWith('/socket-handler/console/s1');
    expect(FakeRfb.last!.options.credentials).toEqual({ password: 'PVEVNC:t' });
    expect(FakeRfb.last!.scaleViewport).toBeTrue();

    FakeRfb.last!.dispatchEvent(new Event('connect'));
    fixture.detectChanges();
    expect(text()).toContain('Connected');
    expect(text()).toContain('Send Ctrl+Alt+Del');
    expect(text()).not.toContain('Connecting to kali');
  });

  it('shows why the console dropped and offers to reconnect', async () => {
    render(tracker());
    await settle();
    FakeRfb.last!.dispatchEvent(new Event('connect'));
    socket.close();
    FakeRfb.last!.dispatchEvent(new CustomEvent('disconnect', { detail: { clean: false } }));
    await settle();
    expect(text()).toContain('Disconnected');
    expect(text()).toContain('pve-a did not accept the console connection.');

    const first = FakeRfb.last;
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find(button => button.textContent?.includes('Reconnect'))!.click();
    await settle();
    expect(consoles.open).toHaveBeenCalledTimes(2);
    expect(FakeRfb.last).not.toBe(first);
  });

  it('says the console is not available when the API refuses', async () => {
    render(tracker());
    consoles.open.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403, error: { message: 'Access denied' } })));
    (fixture.componentInstance as unknown as { reconnect(): void }).reconnect();
    await settle();
    expect(text()).toContain('Console not available');
    expect(text()).toContain('Only the team that owns this lab can open its consoles.');
  });

  it('does not ask for a console on a stopped VM or a container lab', async () => {
    render(tracker(LabType.VIRTUAL_MACHINE, 'stopped'));
    await settle();
    expect(text()).toContain('kali is stopped. Start or resume the lab first.');
    expect(consoles.open).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    render(tracker(LabType.DOCKER_COMPOSE));
    await settle();
    expect(text()).toContain('Consoles are only for Proxmox VM labs.');
  });

  it('lets you switch to the other VMs in the lab', async () => {
    render(tracker());
    await settle();
    const picks = Array.from(fixture.nativeElement.querySelectorAll('.vm-picks a')) as HTMLAnchorElement[];
    expect(picks.map(a => a.textContent?.trim())).toEqual(['kali', 'dc01']);
    expect(picks[0].getAttribute('aria-current')).toBe('page');
    expect(picks[1].getAttribute('href')).toBe('/environments/lt1/console/102');
  });

  describe('inside a lab', () => {
    it('opens the viewer\'s own copy and keeps its links under the lab', async () => {
      render(tracker(), '101', { own: 'lt1' });
      await settle();
      expect(consoles.open).toHaveBeenCalledWith('lt1', '101');
      const picks = Array.from(fixture.nativeElement.querySelectorAll('.vm-picks a')) as HTMLAnchorElement[];
      expect(picks[1].getAttribute('href')).toBe('/labs/r1/console/102');
      const details = (Array.from(fixture.nativeElement.querySelectorAll('.page-head a.btn')) as HTMLAnchorElement[])
        .find(a => a.textContent?.includes('Details'))!;
      expect(details.getAttribute('href')).toBe('/labs/r1/details');
    });

    it('opens the copy in ?copy= and keeps it in the links', async () => {
      render(tracker(), '101', { own: 'other', copy: 'lt1' });
      await settle();
      expect(consoles.open).toHaveBeenCalledWith('lt1', '101');
      const picks = Array.from(fixture.nativeElement.querySelectorAll('.vm-picks a')) as HTMLAnchorElement[];
      expect(picks[1].getAttribute('href')).toBe('/labs/r1/console/102?copy=lt1');
    });

    it('says so when the viewer has no copy yet', async () => {
      render(tracker(), '101', { own: null });
      await settle();
      expect(consoles.open).not.toHaveBeenCalled();
      expect(text()).toContain('Your copy of this environment is not running.');
    });
  });
});

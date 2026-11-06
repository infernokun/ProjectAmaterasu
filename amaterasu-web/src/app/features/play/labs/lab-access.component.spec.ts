import { Clipboard } from '@angular/cdk/clipboard';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabType } from '../../../enums/lab-type.enum';
import { LabTracker } from '../../../models/lab/lab-tracker.model';
import { PlayApiService } from '../data/play-api.service';
import { LabAccessComponent } from './lab-access.component';

describe('LabAccessComponent', () => {
  let fixture: ComponentFixture<LabAccessComponent>;
  let api: jasmine.SpyObj<PlayApiService>;
  let clipboard: jasmine.SpyObj<Clipboard>;

  const tracker = (address: string | null, vms?: object[]) => new LabTracker({
    id: 'lt1', labStatus: LabStatus.ACTIVE, labStarted: { name: 'Pivot', labType: LabType.VIRTUAL_MACHINE },
    vms: vms ?? [
      { vmid: 105, name: 'red-fox-pivot-tpl-bastion', status: 'running', role: 'bastion', ciuser: 'player', guestAgent: true,
        ready: !!address, accessAddress: address, uplinkBridge: 'vmbr0', labSubnet: '172.16.0.0/24' },
      { vmid: 106, name: 'red-fox-pivot-tpl-target', status: 'running', guestAgent: true, ready: true },
    ],
  });

  function render(current: LabTracker, compact = false) {
    api = jasmine.createSpyObj<PlayApiService>('PlayApiService', ['access']);
    api.access.and.returnValue(of({ username: 'player', password: 'k7mq-x3vt-9hwp-r2nd', address: '10.0.0.42',
      labSubnet: '172.16.0.0/24', bastion: 'red-fox-pivot-tpl-bastion' }));
    clipboard = jasmine.createSpyObj<Clipboard>('Clipboard', ['copy']);
    clipboard.copy.and.returnValue(true);
    TestBed.configureTestingModule({
      imports: [LabAccessComponent],
      providers: [{ provide: PlayApiService, useValue: api }, { provide: Clipboard, useValue: clipboard }],
    });
    fixture = TestBed.createComponent(LabAccessComponent);
    fixture.componentRef.setInput('tracker', current);
    fixture.componentRef.setInput('compact', compact);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) => Array.from(el().querySelectorAll('button')).find(b => b.textContent?.trim() === label);
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };

  it('shows the SSH command for the bastion with a copy button and tips', () => {
    render(tracker('10.0.0.42'));
    expect(el().textContent).toContain('SSH access');
    expect(el().textContent).toContain('ssh player@10.0.0.42');
    expect(el().querySelector('[aria-label="Copy SSH command"]')).not.toBeNull();
    expect(el().textContent).toContain('172.16.0.0/24');
    expect(el().textContent).toContain('ssh -J player@10.0.0.42 user@<lab-host>');
    expect(el().textContent).toContain('ssh -L 8080:<lab-host>:80 player@10.0.0.42');
  });

  it('waits for the bastion address', () => {
    render(tracker(null));
    expect(el().textContent).toContain("Waiting for the bastion's address");
    expect(el().textContent).not.toContain('ssh player@');
  });

  it('fetches the password only when asked, then shows and hides it', async () => {
    render(tracker('10.0.0.42'), true);
    expect(api.access).not.toHaveBeenCalled();
    expect(el().textContent).not.toContain('k7mq-x3vt-9hwp-r2nd');
    expect(el().textContent).not.toContain('ssh -J');

    button('Show')!.click();
    await settle();
    expect(api.access).toHaveBeenCalledWith('lt1');
    expect(el().querySelector('[data-testid="password"]')!.textContent).toContain('k7mq-x3vt-9hwp-r2nd');

    button('Hide')!.click();
    fixture.detectChanges();
    expect(el().textContent).not.toContain('k7mq-x3vt-9hwp-r2nd');
  });

  it('copies the password without showing it', async () => {
    render(tracker('10.0.0.42'));
    button('Copy')!.click();
    // Not whenStable: it would also wait out the 1.5 s "Copied" timer
    await new Promise(resolve => setTimeout(resolve));
    fixture.detectChanges();
    expect(clipboard.copy).toHaveBeenCalledWith('k7mq-x3vt-9hwp-r2nd');
    expect(el().textContent).not.toContain('k7mq-x3vt-9hwp-r2nd');
    expect(button('Copied')).toBeDefined();
  });

  it('offers the VM login when a lab has a cloud-init user but no bastion', () => {
    render(tracker(null, [{ vmid: 107, name: 'player-box', status: 'running', ciuser: 'player' }]));
    expect(el().textContent).toContain('VM login');
    expect(el().textContent).not.toContain('SSH access');
  });

  it('shows nothing for a lab without a login', () => {
    render(tracker(null, [{ vmid: 107, name: 'cirros', status: 'running' }]));
    expect(el().textContent?.trim()).toBe('');
  });
});

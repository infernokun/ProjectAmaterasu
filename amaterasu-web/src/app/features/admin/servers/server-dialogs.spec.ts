import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { LabStatus } from '../../../enums/lab-status.enum';
import { ServerType } from '../../../enums/server-type.enum';
import { EditServerData, EditServerDialogComponent, editProblems } from './edit-server-dialog';
import { MoveLabData, MoveLabDialogComponent } from './move-lab-dialog';
import { Deployment, Server, ServerSettings } from './servers.service';

const settings: ServerSettings = {
  id: 's1', name: 'dock-01', serverType: ServerType.DOCKER_HOST, ipAddress: '10.20.0.11', port: 22,
  username: 'amaterasu', nodeName: null, hasPassword: true, hasApiToken: false,
};

const server = (id: string, online = true, type = ServerType.DOCKER_HOST): Server => ({
  id, name: id, ipAddress: '10.20.0.1', port: 22, serverType: type,
  stats: {
    status: online ? LabStatus.ACTIVE : LabStatus.OFFLINE, totalRam: 32, usedRam: 8, availableRam: 24, cpu: 8, cpuUsagePercent: 10,
    totalDiskSpace: 500, usedDiskSpace: 100, availableDiskSpace: 400, uptime: 100, updatedAt: null,
  },
});

const deployment = (status: LabStatus): Deployment => ({
  id: 't1', labName: 'Log Hunting', labType: 'DOCKER_COMPOSE', teamId: 'red', teamName: 'Red Fox', status, serverId: 'dock-02', units: '', updatedAt: null,
});

const buttons = (el: HTMLElement) => [...el.querySelectorAll<HTMLButtonElement>('button')];
const button = (el: HTMLElement, text: string) => buttons(el).find(b => b.textContent!.trim().startsWith(text))!;

function type(el: HTMLElement, label: string, value: string): void {
  const field = [...el.querySelectorAll('label.field')].find(f => f.querySelector('.label')!.textContent!.trim() === label)!;
  const input = field.querySelector('input')!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

describe('editProblems', () => {
  it('checks each field of a server edit', () => {
    const ok = { name: 'dock-03', ipAddress: '10.20.0.13', port: 22, username: 'amaterasu' };
    expect(editProblems(ok, ServerType.DOCKER_HOST, ['dock-01'])).toEqual({});
    expect(editProblems({ ...ok, name: 'DOCK-01' }, ServerType.DOCKER_HOST, ['dock-01']).name).toContain('Another server');
    expect(editProblems({ ...ok, ipAddress: 'https://10.0.0.1/' }, ServerType.DOCKER_HOST, []).ipAddress).toBeDefined();
    expect(editProblems({ ...ok, ipAddress: '[fd00::1]' }, ServerType.DOCKER_HOST, []).ipAddress).toBeUndefined();
    expect(editProblems({ ...ok, port: 0 }, ServerType.DOCKER_HOST, []).port).toBeDefined();
    expect(editProblems({ ...ok, username: ' ' }, ServerType.DOCKER_HOST, []).username).toBeDefined();
    const proxmox = { name: 'pve-a', ipAddress: 'pve-a.lab', port: 8006, nodeName: '' , apiToken: 'no-equals' };
    const problems = editProblems(proxmox, ServerType.PROXMOX, []);
    expect(problems.nodeName).toBeDefined();
    expect(problems.apiToken).toBe('Format TOKEN_ID=SECRET');
  });
});

describe('EditServerDialogComponent', () => {
  let fixture: ComponentFixture<EditServerDialogComponent>;
  let data: EditServerData;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    data = {
      settings, otherNames: ['dock-02'],
      validate: jasmine.createSpy('validate').and.resolveTo(true),
      save: jasmine.createSpy('save').and.resolveTo(undefined),
    };
    TestBed.configureTestingModule({
      imports: [EditServerDialogComponent],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: data }, { provide: MatDialogRef, useValue: jasmine.createSpyObj('ref', ['close']) }],
    });
    fixture = TestBed.createComponent(EditServerDialogComponent);
    fixture.detectChanges();
  });

  it('starts from the saved settings and keeps the stored password unless one is typed', async () => {
    expect(el().querySelector<HTMLInputElement>('input.mono')!.value).toBe('dock-01');
    expect(el().querySelector<HTMLInputElement>('input[type=password]')!.placeholder).toContain('keep the stored password');
    expect(button(el(), 'Save changes').disabled).toBeTrue();

    type(el(), 'Name', 'dock-01b');
    fixture.detectChanges();
    expect(button(el(), 'Save changes').disabled).toBeFalse();
    button(el(), 'Save changes').click();
    await fixture.whenStable();
    expect(data.save).toHaveBeenCalledWith({ name: 'dock-01b', ipAddress: '10.20.0.11', port: 22, username: 'amaterasu', password: undefined });
  });

  it('needs a passing connection test before saving new connection settings', async () => {
    type(el(), 'Address', '10.20.0.99');
    fixture.detectChanges();
    expect(button(el(), 'Save changes').disabled).toBeTrue();
    expect(el().textContent).toContain('Test them before saving');

    (data.validate as jasmine.Spy).and.resolveTo(false);
    button(el(), 'Test connection').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el().querySelector('[role=alert]')!.textContent).toContain('No answer from 10.20.0.99:22');
    expect(button(el(), 'Save changes').disabled).toBeTrue();

    (data.validate as jasmine.Spy).and.resolveTo(true);
    button(el(), 'Test connection').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(button(el(), 'Save changes').disabled).toBeFalse();
  });

  it('marks invalid fields once they are left', () => {
    type(el(), 'Name', 'dock-02');
    type(el(), 'SSH port', '70000');
    fixture.detectChanges();
    expect(el().textContent).toContain('Another server is called dock-02');
    expect(el().textContent).toContain('A port from 1 to 65535');
    expect(button(el(), 'Test connection').disabled).toBeTrue();
  });
});

describe('MoveLabDialogComponent', () => {
  function create(status: LabStatus, targets: Server[]) {
    const data: MoveLabData = {
      deployment: deployment(status), from: server('dock-02'), targets,
      move: jasmine.createSpy('move').and.resolveTo(undefined),
    };
    TestBed.configureTestingModule({
      imports: [MoveLabDialogComponent],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: data }, { provide: MatDialogRef, useValue: jasmine.createSpyObj('ref', ['close']) }],
    });
    const fixture = TestBed.createComponent(MoveLabDialogComponent);
    fixture.detectChanges();
    return { fixture, data, el: fixture.nativeElement as HTMLElement };
  }

  it('moves a lab that is not deployed without destroying anything', async () => {
    const { fixture, data, el } = create(LabStatus.STOPPED, [server('dock-01'), server('dock-03', false)]);
    expect(el.textContent).toContain('nothing is destroyed');
    button(el, 'Move lab').click();
    await fixture.whenStable();
    expect(data.move).toHaveBeenCalledWith('dock-01', false);
  });

  it('spells out that a deployed copy is destroyed and only moves to an online server', async () => {
    const { fixture, data, el } = create(LabStatus.ACTIVE, [server('dock-03', false), server('dock-01')]);
    expect(el.textContent).toContain('Its current copy is destroyed.');
    expect(el.textContent).toContain('A fresh copy is then started on dock-01');

    const offline = el.querySelectorAll<HTMLInputElement>('input[type=radio]')[0];
    offline.checked = true;
    offline.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(el.textContent).toContain('dock-03 is not online');
    expect(button(el, 'Destroy and start fresh').disabled).toBeTrue();

    const online = el.querySelectorAll<HTMLInputElement>('input[type=radio]')[1];
    online.checked = true;
    online.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    button(el, 'Destroy and start fresh').click();
    await fixture.whenStable();
    expect(data.move).toHaveBeenCalledWith('dock-01', true);
  });

  it('shows why a move failed and stays open', async () => {
    const { fixture, data, el } = create(LabStatus.ACTIVE, [server('dock-01')]);
    (data.move as jasmine.Spy).and.rejectWith(new Error('x'));
    button(el, 'Destroy and start fresh').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')!.textContent).toContain('The lab could not be moved.');
  });

  it('says when there is nowhere to move it', () => {
    const { el } = create(LabStatus.STOPPED, []);
    expect(el.textContent).toContain('There is no other Docker host');
    expect(button(el, 'Move lab').disabled).toBeTrue();
  });
});

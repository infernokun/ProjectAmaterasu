import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LabStatus } from '../../../enums/lab-status.enum';
import { ServerType } from '../../../enums/server-type.enum';
import { serverHealth, uptime, usage } from './server-health';
import { Server, ServerStats, ServersService } from './servers.service';
import { ServersStore } from './servers.store';

const stats = (patch: Partial<ServerStats> = {}): ServerStats => ({
  status: LabStatus.ACTIVE, totalRam: 64, usedRam: 38, availableRam: 26, cpu: 16, cpuUsagePercent: 42,
  totalDiskSpace: 1000, usedDiskSpace: 410, availableDiskSpace: 590, uptime: 12 * 86400, updatedAt: '2026-11-04 12:00:00', ...patch,
});

const server = (id: string, patch: Partial<Server> = {}): Server => ({
  id, name: id, ipAddress: '10.20.0.11', port: 22, serverType: ServerType.DOCKER_HOST, stats: stats(), ...patch,
});

describe('serverHealth', () => {
  it('reads online, busy, offline and unchecked servers', () => {
    const online = serverHealth(server('dock-01'));
    expect(online.label).toBe('Online');
    expect(online.cpu.text).toBe('42% of 16');
    expect(online.memory).toEqual({ label: 'Memory', pct: 59, level: 'ok', text: '38 of 64 GB' });
    expect(online.disk.text).toBe('0.4 of 1 TB');

    const busy = serverHealth(server('dock-02', { stats: stats({ cpuUsagePercent: 88 }) }));
    expect(busy.label).toBe('Busy');
    expect(busy.cpu.level).toBe('bad');

    expect(serverHealth(server('pve-b', { stats: stats({ status: LabStatus.OFFLINE }) })).label).toBe('Offline');
    expect(serverHealth(server('new', { stats: null })).label).toBe('Not checked');

    const proxmox = serverHealth(server('pve-a', { serverType: ServerType.PROXMOX, stats: stats({ totalRam: 0, cpu: 0, cpuUsagePercent: 0 }) }));
    expect(proxmox.label).toBe('Online');
    expect(proxmox.memory.text).toBe('no data');
  });

  it('formats sizes and uptime', () => {
    expect(usage(1100, 4000)).toBe('1.1 of 4 TB');
    expect(usage(29.4, 32)).toBe('29 of 32 GB');
    expect(uptime(41 * 86400)).toBe('41 days');
    expect(uptime(7200)).toBe('2 h');
    expect(uptime(0)).toBe('unknown');
  });
});

describe('ServersStore', () => {
  let api: jasmine.SpyObj<ServersService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<ServersService>('ServersService', ['servers', 'deployments', 'validate', 'add', 'check', 'delete',
      'settings', 'validateEdit', 'update', 'moveLab']);
    api.servers.and.returnValue(of([server('dock-02'), server('dock-01')]));
    api.deployments.and.returnValue(of([
      { id: 'a', labName: 'Web', labType: null, teamId: 't1', teamName: 'Red Fox', status: LabStatus.ACTIVE, serverId: 'dock-01', units: '4 containers', updatedAt: null },
      { id: 'b', labName: 'Web', labType: null, teamId: 't2', teamName: 'Night Owls', status: LabStatus.STOPPED, serverId: 'dock-01', units: '', updatedAt: null },
      { id: 'c', labName: 'Logs', labType: null, teamId: 't2', teamName: 'Night Owls', status: LabStatus.PAUSED, serverId: 'dock-02', units: '', updatedAt: null },
    ]));
    TestBed.configureTestingModule({ providers: [ServersStore, { provide: ServersService, useValue: api }] });
  });

  it('lists servers by name with the labs still deployed on each', () => {
    const store = TestBed.inject(ServersStore);
    store.load();
    expect(store.cards().map(c => c.server.name)).toEqual(['dock-01', 'dock-02']);
    expect(store.cards()[0].deployments.map(d => d.id)).toEqual(['a']);
    expect(store.selected()?.server.id).toBe('dock-01');
    store.select('dock-02');
    expect(store.selected()?.deployments.map(d => d.id)).toEqual(['c']);
  });

  it('checks and removes servers', async () => {
    const store = TestBed.inject(ServersStore);
    store.load();
    api.check.and.returnValue(of(server('dock-01', { stats: stats({ status: LabStatus.OFFLINE }) })));
    await store.check('dock-01');
    expect(store.cards()[0].health.label).toBe('Offline');
    expect(store.checking()).toBeNull();

    api.delete.and.returnValue(of({}));
    store.select('dock-02');
    await store.remove('dock-02');
    expect(store.servers().map(s => s.id)).toEqual(['dock-01']);
    expect(store.selected()?.server.id).toBe('dock-01');
  });

  it('lists every team lab that starts on a server, deployed ones first', () => {
    const store = TestBed.inject(ServersStore);
    store.load();
    expect(store.cards()[0].labs.map(d => d.id)).toEqual(['a', 'b']);
    expect(store.cards()[0].deployments.map(d => d.id)).toEqual(['a']);
  });

  it('saves server edits into the list', async () => {
    const store = TestBed.inject(ServersStore);
    store.load();
    api.update.and.returnValue(of(server('dock-01', { name: 'dock-01b', port: 2222 })));
    await store.update('dock-01', { name: 'dock-01b', ipAddress: '10.20.0.11', port: 2222, username: 'labs' });
    expect(api.update).toHaveBeenCalledWith('dock-01', jasmine.objectContaining({ port: 2222 }));
    expect(store.servers().find(s => s.id === 'dock-01')?.name).toBe('dock-01b');
  });

  it('moves a lab and reloads the deployments when the move fails', async () => {
    const store = TestBed.inject(ServersStore);
    store.load();
    api.moveLab.and.returnValue(of({ id: 'b', labName: 'Web', labType: null, teamId: 't2', teamName: 'Night Owls', status: LabStatus.STOPPED, serverId: 'dock-02', units: '', updatedAt: null }));
    await store.moveLab('b', 'dock-02', false);
    expect(api.moveLab).toHaveBeenCalledWith('b', 'dock-02', false);
    expect(store.cards()[1].labs.map(d => d.id)).toContain('b');

    api.moveLab.and.returnValue(throwError(() => new Error('the old copy could not be removed')));
    api.deployments.calls.reset();
    await expectAsync(store.moveLab('a', 'dock-02', true)).toBeRejected();
    expect(api.deployments).toHaveBeenCalledTimes(1);
  });
});

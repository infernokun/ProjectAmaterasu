import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { LabStatus } from '../../../enums/lab-status.enum';
import { LabType } from '../../../enums/lab-type.enum';
import { ServerType } from '../../../enums/server-type.enum';
import { LabsStore } from '../../../core/stores/labs.store';
import { Server } from '../../admin/servers/servers.service';
import { CatalogLab, LabBuilderService } from './lab-builder.service';
import { LabBuilderStore } from './lab-builder.store';

const server = (id: string, type: ServerType, status: LabStatus | null = LabStatus.ACTIVE): Server => ({
  id, name: id, ipAddress: '10.0.0.1', port: 22, serverType: type, noNewLabs: false,
  stats: status ? {
    status, totalRam: 32, usedRam: 8, availableRam: 24, cpu: 8, cpuUsagePercent: 10,
    totalDiskSpace: 500, usedDiskSpace: 100, availableDiskSpace: 400, uptime: 100,
  } : null,
});

const lab = (patch: Partial<CatalogLab> = {}): CatalogLab => ({
  id: 'lab1', name: 'API Abuse Basics', description: 'A JSON API', version: '1.0.0', capacity: 10,
  labType: LabType.DOCKER_COMPOSE, ready: false, published: false, vmIds: [], createdBy: null, updatedAt: null, ...patch,
});

const flush = () => new Promise(resolve => setTimeout(resolve));

describe('LabBuilderStore', () => {
  let api: jasmine.SpyObj<LabBuilderService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<LabBuilderService>('LabBuilderService', [
      'servers', 'lab', 'templates', 'networks', 'networkCards', 'validateYaml', 'checkOnServer', 'composeFile', 'uploadCompose', 'create', 'update',
      'publish', 'unpublish',
    ]);
    api.servers.and.returnValue(of([
      server('dock-01', ServerType.DOCKER_HOST, LabStatus.OFFLINE),
      server('dock-02', ServerType.DOCKER_HOST),
      server('pve-a', ServerType.PROXMOX),
    ]));
    api.templates.and.returnValue(of([
      { vmid: 9002, name: 'win-dc-2022', cpus: 2, maxmem: 4 * 1024 ** 3 },
      { vmid: 9001, name: 'kali-2026.3', cpus: 2, maxmem: 4 * 1024 ** 3 },
    ]));
    api.networks.and.returnValue(of([{ iface: 'vmbr1', address: null, cidr: '10.10.0.1/24' }]));
    api.validateYaml.and.returnValue(of(true));
    TestBed.configureTestingModule({
      providers: [LabBuilderStore, { provide: LabBuilderService, useValue: api }, { provide: LabsStore, useValue: { load: jasmine.createSpy('load') } }],
    });
  });

  it('starts a compose lab on an online Docker host', async () => {
    const store = TestBed.inject(LabBuilderStore);
    await store.init(null);
    expect(store.serverId()).toBe('dock-02');
    expect(store.isCompose()).toBeTrue();
    expect(store.canSave()).toBeFalse();
    store.update({ name: 'API Abuse Basics' });
    expect(store.canSave()).toBeTrue();
    store.setCompose('services: [oops');
    expect(store.canSave()).toBeFalse();
  });

  it('validates compose files and saves new ones with their file', async () => {
    const store = TestBed.inject(LabBuilderStore);
    await store.init(null);
    store.update({ name: 'API Abuse Basics', capacity: 4 });
    store.setCompose('services:\n  api:\n    image: api:1\n    mem_limit: 2g\n');

    await store.validate();
    expect(store.validated()).toBeTrue();
    expect(store.results().map(r => r.title)).toContain('Save to check it on dock-02');
    expect(store.capacityCheck().memory.text).toBe('8 of 24 GB');

    api.create.and.returnValue(of(lab({ capacity: 4 })));
    await store.save();
    const [request, serverId] = api.create.calls.mostRecent().args;
    expect(serverId).toBe('dock-02');
    expect(request).toEqual(jasmine.objectContaining({ labType: LabType.DOCKER_COMPOSE, capacity: 4, dockerFile: store.compose() }));
    expect(store.labId()).toBe('lab1');
    expect(store.dirty()).toBeFalse();
  });

  it('edits a compose lab and uploads the file only when it changed', async () => {
    api.lab.and.returnValue(of(lab()));
    api.composeFile.and.returnValue(of('services:\n  web:\n    image: nginx\n'));
    api.update.and.returnValue(of(lab({ description: 'Changed' })));
    api.uploadCompose.and.returnValue(of({}));
    api.checkOnServer.and.returnValue(of(true));
    const store = TestBed.inject(LabBuilderStore);
    await store.init('lab1');
    await flush();

    expect(api.composeFile).toHaveBeenCalledWith('lab1', 'dock-02');
    expect(store.composeChanged()).toBeFalse();
    await store.validate();
    expect(store.results().map(r => r.title)).toContain('docker compose config passed on dock-02');

    store.update({ description: 'Changed' });
    await store.save();
    expect(api.uploadCompose).not.toHaveBeenCalled();

    store.setCompose('services:\n  web:\n    image: nginx:1.27\n');
    await store.save();
    expect(api.uploadCompose).toHaveBeenCalledWith('lab1', 'dock-02', 'services:\n  web:\n    image: nginx:1.27\n');
  });

  it('builds VM labs from Proxmox templates and checks their bridges', async () => {
    api.networkCards.and.callFake((_: string, vmid: number) =>
      of(vmid === 9001 ? { net0: 'virtio=AA,bridge=vmbr1' } : { net0: 'virtio=BB,bridge=vmbr9' }));
    const store = TestBed.inject(LabBuilderStore);
    await store.init(null);
    store.setType(LabType.VIRTUAL_MACHINE);
    await flush();

    expect(store.serverId()).toBe('pve-a');
    expect(store.templates().map(t => t.vmid)).toEqual([9001, 9002]);
    store.toggleTemplate(9001);
    store.toggleTemplate(9002);
    expect(store.perCopy()).toEqual({ cpus: 4, memoryGb: 8 });

    await store.validate();
    const titles = store.results().map(r => r.title);
    expect(titles).toContain('2 templates found on pve-a');
    expect(titles).toContain('Each team copy gets a private copy of vmbr1');
    expect(titles).toContain('vmbr9 does not exist on pve-a');
    expect(store.validated()).toBeFalse();
  });

  it('keeps a compose draft from publishing until a Docker host accepted the saved file', async () => {
    api.lab.and.returnValue(of(lab()));
    api.composeFile.and.returnValue(of('services:\n  web:\n    image: nginx\n'));
    const store = TestBed.inject(LabBuilderStore);
    await store.init('lab1');
    await flush();
    expect(store.published()).toBeFalse();
    expect(store.publishBlocker()).toContain('Validate first');

    store.update({ description: 'Changed' });
    expect(store.publishBlocker()).toBe('Save the draft, then validate it on a Docker host');
  });

  it('publishes a ready lab, saving first, and unpublishes it', async () => {
    api.lab.and.returnValue(of(lab({ labType: LabType.VIRTUAL_MACHINE, ready: true, vmIds: [9001] })));
    api.update.and.returnValue(of(lab({ labType: LabType.VIRTUAL_MACHINE, ready: true, vmIds: [9001], description: 'Changed' })));
    api.publish.and.returnValue(of(lab({ labType: LabType.VIRTUAL_MACHINE, ready: true, vmIds: [9001], published: true })));
    api.unpublish.and.returnValue(of(lab({ labType: LabType.VIRTUAL_MACHINE, ready: true, vmIds: [9001], published: false })));
    const store = TestBed.inject(LabBuilderStore);
    await store.init('lab1');
    await flush();
    store.update({ description: 'Changed' });
    expect(store.publishBlocker()).toBeNull();

    await store.publish();
    expect(api.update).toHaveBeenCalled();
    expect(api.publish).toHaveBeenCalledWith('lab1');
    expect(store.published()).toBeTrue();
    expect(store.dirty()).toBeFalse();

    await store.unpublish();
    expect(api.unpublish).toHaveBeenCalledWith('lab1');
    expect(store.published()).toBeFalse();
  });
});

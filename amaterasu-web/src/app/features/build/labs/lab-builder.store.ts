import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';
import { LabType } from '../../../enums/lab-type.enum';
import { ServerType } from '../../../enums/server-type.enum';
import { errorMessage } from '../../../core/http-error';
import { RequestState, initialRequestState } from '../../../core/stores/request-status';
import { LabsStore } from '../../../core/stores/labs.store';
import { Server } from '../../admin/servers/servers.service';
import { serverHealth } from '../../admin/servers/server-health';
import { CheckResult, analyzeCompose } from './compose-analysis';
import { CatalogLab, LabBuilderService, LabRequest, NodeNetwork, VmTemplate } from './lab-builder.service';

export type BuilderType = LabType.DOCKER_COMPOSE | LabType.VIRTUAL_MACHINE;
type Load = 'idle' | 'loading' | 'loaded' | 'error';

export const STARTER_COMPOSE = `services:
  web:
    image: nginx:1.27-alpine
    mem_limit: 256m
`;

interface BuilderState extends RequestState {
  labId: string | null;
  original: CatalogLab | null;
  type: BuilderType;
  name: string;
  version: string;
  capacity: number;
  description: string;
  servers: Server[];
  serverId: string | null;
  compose: string;
  /** The file as last loaded or saved, to tell whether it changed. */
  savedCompose: string | null;
  composeLoad: Load;
  composeError: string;
  templates: VmTemplate[];
  templatesLoad: Load;
  templatesError: string;
  networks: NodeNetwork[];
  picked: number[];
  validation: 'idle' | 'running' | 'done';
  results: CheckResult[];
  saving: boolean;
  dirty: boolean;
  savedAt: number | null;
}

const initial: BuilderState = {
  labId: null, original: null, type: LabType.DOCKER_COMPOSE, name: '', version: '1.0.0', capacity: 10, description: '',
  servers: [], serverId: null, compose: STARTER_COMPOSE, savedCompose: null, composeLoad: 'idle', composeError: '',
  templates: [], templatesLoad: 'idle', templatesError: '', networks: [], picked: [], validation: 'idle', results: [],
  saving: false, dirty: false, savedAt: null, ...initialRequestState,
};

const GB = 1024 ** 3;

/** One builder for Docker Compose and Proxmox VM labs: details, source, validation and capacity. */
export const LabBuilderStore = signalStore(
  withState<BuilderState>(initial),
  withComputed(store => {
    const compose = computed(() => store.type() === LabType.DOCKER_COMPOSE);
    const wantedServerType = computed(() => (compose() ? ServerType.DOCKER_HOST : ServerType.PROXMOX));
    const serversForType = computed(() => store.servers().filter(server => server.serverType === wantedServerType()));
    const server = computed(() => serversForType().find(s => s.id === store.serverId()) ?? null);
    const analysis = computed(() => analyzeCompose(store.compose()));
    const selectedTemplates = computed(() => store.templates().filter(t => store.picked().includes(t.vmid)));
    const perCopy = computed(() => compose()
      ? { cpus: analysis().perCopy.cpus, memoryGb: analysis().perCopy.memoryGb }
      : {
        cpus: selectedTemplates().reduce((sum, t) => sum + t.cpus, 0),
        memoryGb: Math.round(selectedTemplates().reduce((sum, t) => sum + t.maxmem, 0) / GB * 10) / 10,
      });
    const problems = computed(() => store.results().filter(r => r.tone === 'bad').length);
    const warnings = computed(() => store.results().filter(r => r.tone === 'warn').length);
    const sourceReady = computed(() => (compose() ? analysis().ok : store.picked().length > 0));
    return {
      isCompose: compose,
      serversForType,
      server,
      analysis,
      selectedTemplates,
      perCopy,
      problems,
      warnings,
      sourceReady,
      composeChanged: computed(() => store.savedCompose() === null || store.savedCompose() !== store.compose()),
      validated: computed(() => store.validation() === 'done' && problems() === 0),
      canSave: computed(() => !!store.name().trim() && store.capacity() >= 1 && !!server() && sourceReady() && !store.saving()),
      capacityCheck: computed(() => {
        const stats = server()?.stats;
        const copies = Math.max(1, store.capacity());
        const needCpu = Math.round(perCopy().cpus * copies * 10) / 10;
        const needMem = Math.round(perCopy().memoryGb * copies * 10) / 10;
        const health = server() ? serverHealth(server()!) : null;
        const measured = !!stats && health?.online && stats.totalRam > 0;
        const meter = (need: number, have: number) => {
          const pct = have > 0 ? Math.min(100, Math.round((need / have) * 100)) : 0;
          return { pct, level: pct >= 100 ? 'bad' : pct >= 75 ? 'warn' : 'ok' };
        };
        return {
          measured: !!measured,
          cpu: { ...meter(needCpu, stats?.cpu ?? 0), text: `${needCpu} of ${stats?.cpu ?? 0} vCPU` },
          memory: { ...meter(needMem, stats?.availableRam ?? 0), text: `${needMem} of ${Math.round(stats?.availableRam ?? 0)} GB` },
          overMemory: !!measured && needMem > (stats?.availableRam ?? 0),
        };
      }),
    };
  }),
  withMethods((store, api = inject(LabBuilderService), labs = inject(LabsStore)) => {
    const resetValidation = () => patchState(store, { validation: 'idle', results: [] });

    const loadTemplates = async (serverId: string) => {
      patchState(store, { templatesLoad: 'loading', templatesError: '', templates: [], networks: [] });
      try {
        const [templates, networks] = await Promise.all([
          firstValueFrom(api.templates(serverId)),
          firstValueFrom(api.networks(serverId)).catch(() => [] as NodeNetwork[]),
        ]);
        patchState(store, { templates: [...templates].sort((a, b) => a.vmid - b.vmid), networks, templatesLoad: 'loaded' });
      } catch (error) {
        patchState(store, { templatesLoad: 'error', templatesError: errorMessage(error) ?? 'The node did not answer.' });
      }
    };

    const loadCompose = async (labId: string, serverId: string) => {
      patchState(store, { composeLoad: 'loading', composeError: '' });
      try {
        const yml = await firstValueFrom(api.composeFile(labId, serverId));
        patchState(store, { compose: yml, savedCompose: yml, composeLoad: 'loaded' });
      } catch (error) {
        patchState(store, {
          compose: '', savedCompose: null, composeLoad: 'error',
          composeError: errorMessage(error) ?? 'The file could not be read from the server.',
        });
      }
    };

    const loadSource = (serverId: string | null) => {
      if (!serverId) {
        return;
      }
      if (store.type() === LabType.VIRTUAL_MACHINE) {
        loadTemplates(serverId);
      } else if (store.labId()) {
        loadCompose(store.labId()!, serverId);
      }
    };

    /** A server for the lab's type: online ones first. */
    const pickServer = (): string | null => {
      const candidates = store.serversForType();
      const online = candidates.find(server => serverHealth(server).online);
      return (online ?? candidates[0])?.id ?? null;
    };

    const validateCompose = async (): Promise<CheckResult[]> => {
      const results = [...store.analysis().results];
      if (!store.analysis().ok) {
        return results;
      }
      const server = store.server();
      try {
        const parsed = await firstValueFrom(api.validateYaml(store.compose()));
        results.push(parsed
          ? { tone: 'active', title: 'The API accepts the file', body: 'It parses on the server as well.' }
          : { tone: 'bad', title: 'The API could not parse the file', body: 'Check indentation and quoting.' });
      } catch (error) {
        results.push({ tone: 'bad', title: 'The file could not be checked', body: errorMessage(error) ?? 'The API did not answer.' });
      }
      if (!server) {
        return results;
      }
      if (!store.labId() || store.composeChanged()) {
        results.push({
          tone: 'work', title: `Save to check it on ${server.name}`,
          body: 'docker compose config runs against the saved file on the host.',
        });
        return results;
      }
      try {
        const passed = await firstValueFrom(api.checkOnServer(store.labId()!, server.id));
        results.push(passed
          ? { tone: 'active', title: `docker compose config passed on ${server.name}`, body: 'The host accepts every service and option in the saved file.' }
          : { tone: 'bad', title: `docker compose config failed on ${server.name}`, body: 'Run it on the host to see the error, then fix the file.' });
      } catch (error) {
        results.push({ tone: 'bad', title: `Could not reach ${server.name}`, body: errorMessage(error) ?? 'The host did not answer.' });
      }
      return results;
    };

    const validateVms = async (): Promise<CheckResult[]> => {
      const server = store.server();
      if (!server) {
        return [{ tone: 'bad', title: 'Choose a Proxmox node', body: 'Templates are cloned from the node you pick.' }];
      }
      const selected = store.selectedTemplates();
      const missing = store.picked().filter(vmid => !store.templates().some(t => t.vmid === vmid));
      const results: CheckResult[] = [];
      if (selected.length) {
        results.push({
          tone: 'active', title: `${selected.length} ${selected.length === 1 ? 'template' : 'templates'} found on ${server.name}`,
          body: selected.map(t => t.name).join(', '),
        });
      }
      for (const vmid of missing) {
        results.push({ tone: 'bad', title: `VMID ${vmid} is no longer a template on ${server.name}`, body: 'Pick it again or remove it from the lab.' });
      }
      const bridges = new Set<string>();
      const noCard: string[] = [];
      for (const template of selected) {
        try {
          const cards = await firstValueFrom(api.networkCards(server.id, template.vmid));
          const values = Object.values(cards);
          if (!values.length) {
            noCard.push(template.name);
          }
          values.forEach(card => {
            const bridge = /bridge=([^,]+)/.exec(card)?.[1];
            if (bridge) {
              bridges.add(bridge);
            }
          });
        } catch (error) {
          results.push({ tone: 'bad', title: `Could not read ${template.name}`, body: errorMessage(error) ?? 'The node did not answer.' });
        }
      }
      if (noCard.length) {
        results.push({ tone: 'warn', title: `${noCard.join(', ')} ${noCard.length === 1 ? 'has' : 'have'} no network card`, body: 'Teams can only reach it through the Proxmox console.' });
      } else if (selected.length) {
        results.push({ tone: 'active', title: 'Every template has a network card', body: "Each card moves to the team copy's private bridge at start." });
      }
      const known = new Set(store.networks().map(n => n.iface));
      for (const bridge of bridges) {
        results.push(known.size && !known.has(bridge)
          ? { tone: 'bad', title: `${bridge} does not exist on ${server.name}`, body: 'The templates point at a bridge the node does not have.' }
          : { tone: 'active', title: `Each team copy gets a private copy of ${bridge}`, body: 'Created at start as abrN, with no host address.' });
      }
      const totalMem = store.perCopy().memoryGb * Math.max(1, store.capacity());
      if (selected.length && totalMem > 0) {
        results.push({ tone: 'work', title: `${store.capacity()} copies use ${Math.round(totalMem)} GB of memory`, body: 'Check that the node has room before an event.' });
      }
      return results;
    };

    return {
      async init(labId: string | null): Promise<void> {
        patchState(store, { ...initial, labId, status: 'loading' });
        try {
          const [servers, lab] = await Promise.all([
            firstValueFrom(api.servers()),
            labId ? firstValueFrom(api.lab(labId)) : Promise.resolve(null),
          ]);
          patchState(store, { servers });
          if (lab) {
            patchState(store, {
              original: lab,
              type: lab.labType === LabType.VIRTUAL_MACHINE ? LabType.VIRTUAL_MACHINE : LabType.DOCKER_COMPOSE,
              name: lab.name, version: lab.version, capacity: lab.capacity, description: lab.description,
              picked: [...lab.vmIds], compose: '',
            });
          }
          const serverId = pickServer();
          patchState(store, { serverId, status: 'loaded' });
          loadSource(serverId);
        } catch (error) {
          patchState(store, { status: 'error', error: errorMessage(error) ?? 'The lab could not be loaded.' });
        }
      },
      setType(type: BuilderType): void {
        if (store.labId() || store.type() === type) {
          return;
        }
        patchState(store, { type, picked: [], templates: [], templatesLoad: 'idle', dirty: true });
        resetValidation();
        const serverId = pickServer();
        patchState(store, { serverId });
        loadSource(serverId);
      },
      update(patch: Partial<Pick<BuilderState, 'name' | 'version' | 'capacity' | 'description'>>): void {
        patchState(store, { ...patch, dirty: true });
      },
      setServer(serverId: string): void {
        patchState(store, { serverId });
        resetValidation();
        loadSource(serverId);
      },
      reloadTemplates(): void {
        if (store.serverId()) {
          loadTemplates(store.serverId()!);
        }
      },
      setCompose(compose: string): void {
        if (compose !== store.compose()) {
          patchState(store, { compose, dirty: true });
          resetValidation();
        }
      },
      toggleTemplate(vmid: number): void {
        const picked = store.picked();
        patchState(store, { picked: picked.includes(vmid) ? picked.filter(id => id !== vmid) : [...picked, vmid], dirty: true });
        resetValidation();
      },
      async validate(): Promise<void> {
        patchState(store, { validation: 'running', results: [] });
        const results = store.isCompose() ? await validateCompose() : await validateVms();
        patchState(store, { validation: 'done', results });
      },
      /** Creates or updates the lab, uploading the compose file when it changed. */
      async save(): Promise<CatalogLab> {
        const server = store.server();
        if (!server) {
          throw new Error('Choose a server first');
        }
        patchState(store, { saving: true });
        try {
          const request: LabRequest = {
            name: store.name().trim(),
            description: store.description().trim(),
            version: store.version().trim(),
            labType: store.type(),
            capacity: store.capacity(),
            ...(store.isCompose() ? { dockerFile: store.compose() } : { vms: store.picked() }),
          };
          let lab: CatalogLab;
          if (store.labId()) {
            lab = await firstValueFrom(api.update(store.labId()!, request));
            if (store.isCompose() && store.composeChanged()) {
              await firstValueFrom(api.uploadCompose(lab.id, server.id, store.compose()));
            }
          } else {
            lab = await firstValueFrom(api.create(request, server.id));
          }
          patchState(store, {
            labId: lab.id, original: lab, dirty: false, savedAt: Date.now(),
            savedCompose: store.isCompose() ? store.compose() : store.savedCompose(),
          });
          labs.load(true);
          return lab;
        } finally {
          patchState(store, { saving: false });
        }
      },
    };
  }),
);

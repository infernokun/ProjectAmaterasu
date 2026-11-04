import { load } from 'js-yaml';

/** Matches the chip and dot colors: s-active, s-work, s-warn, s-bad. */
export type ResultTone = 'active' | 'work' | 'warn' | 'bad';

export interface CheckResult {
  tone: ResultTone;
  title: string;
  body: string;
}

export interface ComposeAnalysis {
  /** False when the file does not parse or has no services. */
  ok: boolean;
  services: string[];
  results: CheckResult[];
  /** Summed limits of the services that declare them. */
  perCopy: { cpus: number; memoryGb: number };
  /** Services without a memory limit, which the estimate leaves out. */
  unlimited: string[];
}

type Service = Record<string, unknown>;

/**
 * What the API will do with a compose file for each team copy (it drops published ports and
 * fixed names and moves bind mounts into the team's folder), plus problems worth fixing first.
 */
export function analyzeCompose(source: string): ComposeAnalysis {
  const empty: ComposeAnalysis = { ok: false, services: [], results: [], perCopy: { cpus: 0, memoryGb: 0 }, unlimited: [] };
  if (!source.trim()) {
    return { ...empty, results: [{ tone: 'bad', title: 'The file is empty', body: 'Paste or upload a docker-compose.yml.' }] };
  }
  let doc: unknown;
  try {
    doc = load(source);
  } catch (error) {
    const reason = error instanceof Error ? error.message.split('\n')[0] : 'It does not parse.';
    return { ...empty, results: [{ tone: 'bad', title: 'The file is not valid YAML', body: reason }] };
  }
  const services = isRecord(doc) && isRecord(doc['services']) ? doc['services'] as Record<string, unknown> : null;
  if (!services || !Object.keys(services).length) {
    return { ...empty, results: [{ tone: 'bad', title: 'No services found', body: 'Add a services section with at least one service.' }] };
  }

  const names = Object.keys(services);
  const results: CheckResult[] = [{ tone: 'active', title: `Parsed ${names.length} ${names.length === 1 ? 'service' : 'services'}`, body: names.join(', ') }];
  let cpus = 0;
  let memoryGb = 0;
  const unlimited: string[] = [];

  for (const name of names) {
    const service = (isRecord(services[name]) ? services[name] : {}) as Service;
    const ports = service['ports'];
    if (Array.isArray(ports) && ports.length) {
      results.push({
        tone: 'work',
        title: `Published ${ports.length === 1 ? 'port' : 'ports'} ${ports.map(String).join(', ')} on ${name} ${ports.length === 1 ? 'is' : 'are'} dropped`,
        body: 'Teams share a host, so services stay on the lab network only.',
      });
    }
    if (service['container_name']) {
      results.push({ tone: 'work', title: `container_name on ${name} is dropped`, body: 'Fixed names would clash between team copies.' });
    }
    const binds = bindMounts(service['volumes']);
    if (binds.length) {
      results.push({
        tone: 'work',
        title: `${binds.join(', ')} on ${name} ${binds.length === 1 ? 'moves' : 'move'} into each team's folder`,
        body: 'Each team copy gets its own copy of bind mounted paths.',
      });
    }
    if (!service['image'] && service['build']) {
      results.push({ tone: 'warn', title: `${name} is built on the host`, body: 'Every team copy builds it at start, which is slow. Push an image instead.' });
    } else if (!service['image']) {
      results.push({ tone: 'bad', title: `${name} has no image`, body: 'Set image so the host knows what to run.' });
    }
    const limits = serviceLimits(service);
    cpus += limits.cpus;
    if (limits.memoryGb > 0) {
      memoryGb += limits.memoryGb;
    } else {
      unlimited.push(name);
    }
  }

  for (const name of unlimited) {
    results.push({ tone: 'warn', title: `${name} has no memory limit`, body: 'Set one so a single team cannot starve the host.' });
  }

  return {
    ok: !results.some(result => result.tone === 'bad'),
    services: names,
    results,
    perCopy: { cpus: round(cpus), memoryGb: round(memoryGb) },
    unlimited,
  };
}

/** "512m", "2g", "1.5GB" or bytes, in GB. */
export function parseMemory(value: unknown): number {
  if (typeof value === 'number') {
    return value / 1024 ** 3;
  }
  if (typeof value !== 'string') {
    return 0;
  }
  const match = /^\s*([\d.]+)\s*([kmgt]?)i?b?\s*$/i.exec(value);
  if (!match) {
    return 0;
  }
  const amount = parseFloat(match[1]);
  const unit = match[2].toLowerCase();
  const factor = unit === 't' ? 1024 : unit === 'g' ? 1 : unit === 'm' ? 1 / 1024 : unit === 'k' ? 1 / 1024 ** 2 : 1 / 1024 ** 3;
  return amount * factor;
}

function serviceLimits(service: Service): { cpus: number; memoryGb: number } {
  const deploy = isRecord(service['deploy']) ? service['deploy'] : {};
  const resources = isRecord(deploy['resources']) ? deploy['resources'] : {};
  const limits = isRecord(resources['limits']) ? resources['limits'] : {};
  const cpus = parseFloat(String(limits['cpus'] ?? service['cpus'] ?? 0)) || 0;
  const memoryGb = parseMemory(limits['memory'] ?? service['mem_limit']);
  return { cpus, memoryGb };
}

function bindMounts(volumes: unknown): string[] {
  if (!Array.isArray(volumes)) {
    return [];
  }
  return volumes
    .map(volume => (typeof volume === 'string' ? volume.split(':')[0] : isRecord(volume) && volume['type'] === 'bind' ? String(volume['source'] ?? '') : ''))
    .filter(source => /^(\.{1,2}\/|\/|~|\.$)/.test(source));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}

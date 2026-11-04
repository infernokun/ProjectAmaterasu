import { analyzeCompose, parseMemory } from './compose-analysis';

const COMPOSE = `
services:
  api:
    image: apiabuse/api:1.0
    deploy:
      resources:
        limits: { cpus: "1.5", memory: 1g }
  gateway:
    image: nginx:1.27-alpine
    ports: ["8080:80"]
    mem_limit: 512m
    volumes: [./gateway.conf:/etc/nginx/conf.d/default.conf]
  db:
    image: postgres:16
    container_name: api-db
    mem_limit: 1g
    volumes: [dbdata:/var/lib/postgresql/data]
  cache:
    image: redis:7
volumes:
  dbdata: {}
`;

describe('analyzeCompose', () => {
  it('lists what changes per team copy and what to fix', () => {
    const result = analyzeCompose(COMPOSE);
    expect(result.ok).toBeTrue();
    expect(result.services).toEqual(['api', 'gateway', 'db', 'cache']);
    const titles = result.results.map(r => r.title);
    expect(titles).toContain('Parsed 4 services');
    expect(titles).toContain('Published port 8080:80 on gateway is dropped');
    expect(titles).toContain('container_name on db is dropped');
    expect(titles).toContain("./gateway.conf on gateway moves into each team's folder");
    expect(titles).toContain('cache has no memory limit');
    expect(titles.some(t => t.includes('dbdata'))).toBeFalse();
  });

  it('adds up declared limits for the capacity check', () => {
    const result = analyzeCompose(COMPOSE);
    expect(result.perCopy.cpus).toBe(1.5);
    expect(result.perCopy.memoryGb).toBe(2.5);
    expect(result.unlimited).toEqual(['cache']);
  });

  it('reports files that cannot run', () => {
    expect(analyzeCompose('').ok).toBeFalse();
    expect(analyzeCompose('services: [oops').results[0].title).toBe('The file is not valid YAML');
    expect(analyzeCompose('version: "3"').results[0].title).toBe('No services found');
    const noImage = analyzeCompose('services:\n  web:\n    environment: [A=1]\n');
    expect(noImage.ok).toBeFalse();
    expect(noImage.results.map(r => r.title)).toContain('web has no image');
  });

  it('reads memory sizes', () => {
    expect(parseMemory('512m')).toBe(0.5);
    expect(parseMemory('2g')).toBe(2);
    expect(parseMemory('1.5GB')).toBe(1.5);
    expect(parseMemory(undefined)).toBe(0);
  });
});

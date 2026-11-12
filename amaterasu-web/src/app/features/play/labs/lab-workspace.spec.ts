import { EnvironmentView } from '../data/play.models';
import { labCrumbs, labViews } from './lab-workspace';

const env = (vms: object[] = []): EnvironmentView => ({
  labId: 'env1', labName: 'Shop', labType: 'VIRTUAL_MACHINE', mode: 'PER_TEAM', status: 'RUNNING', trackerId: 't1',
  tracker: { id: 't1', labStatus: 'ACTIVE', labStarted: { labType: 'VIRTUAL_MACHINE' }, vms }, canControl: false, note: null, copiesRunning: 1,
});

describe('lab views', () => {
  const keys = (views: { key: string }[]) => views.map(view => view.key);

  it('gives players the questions and, with an environment, its details and console', () => {
    expect(keys(labViews('r1', null, { build: false, run: false }))).toEqual(['']);
    expect(keys(labViews('r1', env(), { build: false, run: false }))).toEqual(['', 'details']);
    expect(keys(labViews('r1', env([{ vmid: 101, status: 'running' }]), { build: false, run: false }))).toEqual(['', 'details', 'console']);
  });

  it('adds Edit for authors, and Teams and Manage for those who run the lab', () => {
    expect(keys(labViews('r1', null, { build: true, run: false }))).toEqual(['', 'edit']);
    expect(keys(labViews('r1', null, { build: false, run: true }))).toEqual(['', 'teams', 'manage']);
    expect(keys(labViews('r1', null, { build: true, run: true }))).toEqual(['', 'edit', 'teams', 'manage']);
  });

  it('links Questions back to the open question and Console to the first running VM', () => {
    const [questions, , console] = labViews('r1', env([{ vmid: 101, status: 'running' }]), { build: false, run: false }, 'q2');
    expect(questions).toEqual(jasmine.objectContaining({ commands: ['/labs', 'r1'], queryParams: { challenge: 'q2' } }));
    expect(console).toEqual(jasmine.objectContaining({ commands: ['/labs', 'r1', 'console', 101], queryParams: {} }));
  });
});

describe('lab breadcrumbs', () => {
  it('reads Labs › lab on the lab, each part but the last a link', () => {
    expect(labCrumbs('r1', 'On Call', null)).toEqual([{ label: 'Labs', link: '/labs' }, { label: 'On Call' }]);
  });

  it('adds the sub-page under a link back to the lab', () => {
    expect(labCrumbs('r1', 'On Call', { key: 'details', title: 'Details' })).toEqual([
      { label: 'Labs', link: '/labs' }, { label: 'On Call', link: ['/labs', 'r1'] }, { label: 'Details' },
    ]);
  });

  it('puts a console under the details of the same copy', () => {
    expect(labCrumbs('r1', 'On Call', { key: 'console', title: 'Console' }, 't7')).toEqual([
      { label: 'Labs', link: '/labs' }, { label: 'On Call', link: ['/labs', 'r1'] },
      { label: 'Details', link: ['/labs', 'r1', 'details'], queryParams: { copy: 't7' } }, { label: 'Console' },
    ]);
  });
});

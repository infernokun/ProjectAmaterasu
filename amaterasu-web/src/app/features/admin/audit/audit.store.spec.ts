import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuditQuery, AuditService } from '../../../core/services/audit.service';
import { AuditEvent, Page } from '../../../models/audit-event.model';
import { AdminUsersService } from '../users/admin-users.service';
import { actionMeta, targetTypeLabel } from './audit-actions';
import { AuditStore, PAGE_SIZE } from './audit.store';

const event = (id: string): AuditEvent => ({ id, at: '2026-11-04T12:00:00Z', actorUsername: 'jmorales', action: 'LAB_STARTED', teamId: 't1' });

const page = (content: AuditEvent[], totalElements: number, number = 0): Page<AuditEvent> => ({
  content, totalElements, totalPages: Math.ceil(totalElements / PAGE_SIZE), number, size: PAGE_SIZE, first: number === 0, last: false,
});

describe('AuditStore', () => {
  let audit: jasmine.SpyObj<AuditService>;

  beforeEach(() => {
    audit = jasmine.createSpyObj<AuditService>('AuditService', ['list']);
    audit.list.and.returnValue(of(page([event('a'), event('b')], 120)));
    TestBed.configureTestingModule({
      providers: [
        AuditStore,
        { provide: AuditService, useValue: audit },
        { provide: AdminUsersService, useValue: { teams: () => of([{ id: 't1', name: 'Red Fox' }]) } },
      ],
    });
  });

  const lastQuery = (): AuditQuery => audit.list.calls.mostRecent().args[0]!;

  it('loads the last week, newest first, with team names', () => {
    const store = TestBed.inject(AuditStore);
    expect(audit.list).toHaveBeenCalledTimes(1);
    const from = Date.parse(lastQuery().from!);
    expect(Date.now() - from).toBeCloseTo(7 * 86_400_000, -4);
    expect(lastQuery().page).toBe(0);
    expect(store.events().length).toBe(2);
    expect(store.pageCount()).toBe(3);
    expect(store.rangeLabel()).toBe('Showing 1 to 2 of 120');
    expect(store.teamNames().get('t1')).toBe('Red Fox');
  });

  it('sends the action group, target and team, and starts again at page one', () => {
    const store = TestBed.inject(AuditStore);
    store.goTo(2);
    expect(lastQuery().page).toBe(2);

    store.setGroup('teams');
    expect(lastQuery().action).toBe('TEAM_CREATED,TEAM_UPDATED,TEAM_DELETED');
    expect(lastQuery().page).toBe(0);

    store.setTargetType('LAB_TRACKER');
    store.setTeam('t1');
    store.setRange('all');
    expect(lastQuery()).toEqual(jasmine.objectContaining({ targetType: 'LAB_TRACKER', teamId: 't1', from: undefined }));
  });

  it('keeps paging inside the results', () => {
    const store = TestBed.inject(AuditStore);
    store.goTo(10);
    expect(store.page()).toBe(2);
    store.goTo(-1);
    expect(store.page()).toBe(0);
  });
});

describe('audit labels', () => {
  it('reads known and unknown actions', () => {
    expect(actionMeta('USER_DISABLED')).toEqual({ label: 'Disabled user', tone: 'bad' });
    expect(actionMeta('SOMETHING_NEW')).toEqual({ label: 'Something new', tone: 'off' });
    expect(targetTypeLabel('LAB_TRACKER')).toBe('Environment');
    expect(targetTypeLabel('USER')).toBe('User');
  });
});

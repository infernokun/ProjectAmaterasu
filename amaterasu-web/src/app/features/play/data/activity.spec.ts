import { activityLine, PLAY_ACTIONS } from './activity';
import { AuditEvent } from '../../../models/audit-event.model';

const event = (over: Partial<AuditEvent>): AuditEvent =>
  ({ id: '1', at: '2026-11-05T10:00:00Z', actorUsername: 'priya.n', action: 'LAB_PAUSED', targetLabel: 'Log Hunting', ...over });

describe('activityLine', () => {
  it('reads as actor, verb and target', () => {
    expect(activityLine(event({})).text).toBe('paused Log Hunting');
  });

  it('does not repeat the actor as the target', () => {
    expect(activityLine(event({ action: 'SOLVE', targetLabel: 'priya.n' })).text).toBe('solve');
  });

  it('lists only play actions for the team feeds', () => {
    expect(PLAY_ACTIONS.split(',')).toContain('FLAG_SOLVED');
    expect(PLAY_ACTIONS).not.toContain('SIGN_IN');
  });
});

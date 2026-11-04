import { AuditEvent } from '../../../models/audit-event.model';
import { StatusTone } from '../../../shared/lab-status';
import { IconName } from '../../../shared/ui/icons';

export interface ActivityLine {
  id: string;
  who: string;
  text: string;
  detail: string;
  at: string;
  tone: StatusTone | null;
  icon: IconName;
}

const VERBS: Record<string, { text: string; tone: StatusTone | null; icon: IconName }> = {
  LAB_STARTED: { text: 'started', tone: 'active', icon: 'play' },
  LAB_PAUSED: { text: 'paused', tone: 'paused', icon: 'pause' },
  LAB_STOPPED: { text: 'stopped', tone: 'off', icon: 'stop' },
  LAB_DELETED: { text: 'removed', tone: 'off', icon: 'trash' },
  FLAG_SOLVED: { text: 'solved', tone: 'active', icon: 'check' },
  POINTS_AWARDED: { text: 'awarded points to', tone: 'accent', icon: 'award' },
};

/** Audit events as feed lines ("priya.n paused AD Forest Breach"). */
export function activityLine(event: AuditEvent): ActivityLine {
  const verb = VERBS[event.action] ?? { text: event.action.toLowerCase().replace(/_/g, ' '), tone: null, icon: 'activity' as IconName };
  return {
    id: event.id,
    who: event.actorUsername ?? 'system',
    text: `${verb.text} ${event.targetLabel ?? ''}`.trim(),
    detail: event.detail ?? '',
    at: event.at,
    tone: verb.tone,
    icon: verb.icon,
  };
}

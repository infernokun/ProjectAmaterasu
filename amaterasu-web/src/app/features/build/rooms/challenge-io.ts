import { DifficultyLevel } from '../../../enums/difficulty-level.enum';
import { toDate } from '../../../core/time';
import { AuthorChallenge } from './authoring.service';

/** The portable form of a lab's questions: what Export writes and Import reads. */
export interface PortableChallenge {
  question: string;
  category: string;
  difficultyLevel: string;
  points: number;
  /** Null for unlimited attempts. */
  maxAttempts: number | null;
  description: string;
  visible: boolean;
  releaseDate: string | null;
  expirationDate: string | null;
  solutionExplanation: string;
  flags: { flag: string; caseSensitive: boolean; surroundWithTag: boolean; weight: number }[];
  hints: { hint: string; cost: number; unlockAfterAttempts: number; orderIndex: number }[];
  /** Names of lab files players download for it. */
  attachments: string[];
}

export function exportChallenges(challenges: AuthorChallenge[]): PortableChallenge[] {
  return challenges.map(c => ({
    question: c.question,
    category: c.category,
    difficultyLevel: c.difficultyLevel,
    points: c.points,
    maxAttempts: c.maxAttempts,
    description: c.description,
    visible: c.visible,
    releaseDate: c.releaseDate,
    expirationDate: c.expirationDate,
    solutionExplanation: c.solutionExplanation,
    flags: c.flags.map(f => ({ ...f })),
    hints: c.hints.map((h, orderIndex) => ({ hint: h.hint, cost: h.cost, unlockAfterAttempts: h.unlockAfterAttempts, orderIndex })),
    attachments: [...(c.attachments ?? [])],
  }));
}

export interface ImportResult {
  challenges: (PortableChallenge & { room: { id: string } })[];
  error: string | null;
}

const LEVELS = new Set<string>(Object.values(DifficultyLevel));

/** Reads an exported file (an array, or an object with a challenges array) into batch import bodies. */
export function parseImport(text: string, roomId: string): ImportResult {
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    return { challenges: [], error: 'The file is not valid JSON.' };
  }
  const list = Array.isArray(data) ? data : isRecord(data) && Array.isArray(data['challenges']) ? data['challenges'] : null;
  if (!list) {
    return { challenges: [], error: 'Expected a list of questions, like the file Export writes.' };
  }
  if (!list.length) {
    return { challenges: [], error: 'The file has no questions in it.' };
  }
  const challenges: ImportResult['challenges'] = [];
  for (let i = 0; i < list.length; i++) {
    const raw = list[i];
    if (!isRecord(raw) || typeof raw['question'] !== 'string' || !raw['question'].trim()) {
      return { challenges: [], error: `Question ${i + 1} has no title.` };
    }
    const level = String(raw['difficultyLevel'] ?? raw['difficulty'] ?? 'EASY').toUpperCase();
    const flags = Array.isArray(raw['flags']) ? raw['flags'].filter(isRecord) : typeof raw['flag'] === 'string' ? [{ flag: raw['flag'] }] : [];
    const hints = Array.isArray(raw['hints']) ? raw['hints'] : [];
    challenges.push({
      question: raw['question'].trim(),
      category: String(raw['category'] ?? 'General'),
      difficultyLevel: LEVELS.has(level) ? level : 'EASY',
      points: Math.max(0, Number(raw['points']) || 0),
      maxAttempts: raw['maxAttempts'] === null || raw['maxAttempts'] === undefined || raw['maxAttempts'] === ''
        ? null : Math.max(1, Number(raw['maxAttempts']) || 1),
      description: String(raw['description'] ?? ''),
      visible: raw['visible'] !== false,
      releaseDate: apiDate(raw['releaseDate']),
      expirationDate: apiDate(raw['expirationDate']),
      solutionExplanation: String(raw['solutionExplanation'] ?? raw['solution'] ?? ''),
      flags: flags.map(f => ({
        flag: String(f['flag'] ?? ''),
        caseSensitive: f['caseSensitive'] !== false,
        surroundWithTag: !!f['surroundWithTag'],
        weight: Number(f['weight']) || 1,
      })).filter(f => f.flag),
      hints: hints.map((h, orderIndex) => (typeof h === 'string'
        ? { hint: h, cost: 0, unlockAfterAttempts: 0, orderIndex }
        : isRecord(h) ? { hint: String(h['hint'] ?? ''), cost: Number(h['cost']) || 0, unlockAfterAttempts: Number(h['unlockAfterAttempts']) || 0, orderIndex } : null))
        .filter((h): h is PortableChallenge['hints'][number] => !!h && !!h.hint),
      attachments: Array.isArray(raw['attachments']) ? raw['attachments'].filter(a => typeof a === 'string' && a.trim()).map(a => a.trim()) : [],
      room: { id: roomId },
    });
  }
  return { challenges, error: null };
}

/** Any date the file has, as the API's "yyyy-MM-dd HH:mm:ss" in UTC. */
function apiDate(value: unknown): string | null {
  const date = toDate(value as string | number | null | undefined);
  return date ? date.toISOString().slice(0, 19).replace('T', ' ') : null;
}

function isRecord(value: unknown): value is Record<string, any> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

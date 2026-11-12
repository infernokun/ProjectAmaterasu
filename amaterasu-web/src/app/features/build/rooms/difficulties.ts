import { DifficultyLevel } from '../../../enums/difficulty-level.enum';

export const DIFFICULTIES: { value: DifficultyLevel; label: string }[] = [
  { value: DifficultyLevel.BEGINNER, label: 'Beginner' },
  { value: DifficultyLevel.EASY, label: 'Easy' },
  { value: DifficultyLevel.MEDIUM, label: 'Medium' },
  { value: DifficultyLevel.HARD, label: 'Hard' },
  { value: DifficultyLevel.EXPERT, label: 'Expert' },
  { value: DifficultyLevel.IMPOSSIBLE, label: 'Impossible' },
];

/** "CTF{x}" to its prefix and suffix around the placeholder. */
export function wrapperParts(tag: string): { prefix: string; suffix: string; name: string } {
  const match = /^(.*?)(?:x|\?)(\W*)$/.exec(tag);
  const prefix = match ? match[1] : `${tag}{`;
  const suffix = match ? match[2] : '}';
  return { prefix, suffix, name: prefix.replace(/\W+$/, '') || tag };
}

import { DifficultyLevel } from '../../../enums/difficulty-level.enum';
import { AuthorChallenge } from './authoring.service';
import { exportChallenges, parseImport } from './challenge-io';

const challenge: AuthorChallenge = {
  id: 'c1', roomId: 'r1', labId: null, question: 'Union Based Dump', category: 'Web', difficultyLevel: DifficultyLevel.MEDIUM,
  points: 300, maxAttempts: 5, description: 'Pull a hidden table.', visible: true,
  releaseDate: '2026-11-04 09:00:00', expirationDate: null, solutionExplanation: 'UNION SELECT', author: 'aokafor',
  flags: [{ flag: 'un10n_s3lect_all', caseSensitive: true, surroundWithTag: true, weight: 1 }],
  hints: [{ id: 'h1', hint: 'Count the columns', cost: 25, unlockAfterAttempts: 0 }],
  solveCount: 4, attemptCount: 19, attachments: [],
};

describe('challenge import and export', () => {
  it('exports without ids or stats and imports into another room', () => {
    const exported = exportChallenges([challenge]);
    expect(JSON.stringify(exported)).not.toContain('c1');
    expect(JSON.stringify(exported)).not.toContain('h1');
    expect(JSON.stringify(exported)).not.toContain('solveCount');

    const result = parseImport(JSON.stringify(exported), 'r2');
    expect(result.error).toBeNull();
    expect(result.challenges[0].room).toEqual({ id: 'r2' });
    expect(result.challenges[0].flags[0].flag).toBe('un10n_s3lect_all');
    expect(result.challenges[0].hints[0]).toEqual({ hint: 'Count the columns', cost: 25, unlockAfterAttempts: 0, orderIndex: 0 });
    expect(result.challenges[0].releaseDate).toBe('2026-11-04 09:00:00');
  });

  it('accepts the populate script shape and ISO dates', () => {
    const result = parseImport(JSON.stringify({
      challenges: [{ question: 'Run it', flag: 'docker run -it', difficulty: 'beginner', hints: ['Think TTY'], releaseDate: '2026-11-04T09:00:00Z' }],
    }), 'r1');
    expect(result.error).toBeNull();
    const [imported] = result.challenges;
    expect(imported.difficultyLevel).toBe('BEGINNER');
    expect(imported.flags).toEqual([{ flag: 'docker run -it', caseSensitive: true, surroundWithTag: false, weight: 1 }]);
    expect(imported.hints[0].hint).toBe('Think TTY');
    expect(imported.releaseDate).toBe('2026-11-04 09:00:00');
  });

  it('explains files it cannot use', () => {
    expect(parseImport('{nope', 'r1').error).toBe('The file is not valid JSON.');
    expect(parseImport('{"a":1}', 'r1').error).toContain('Expected a list');
    expect(parseImport('[]', 'r1').error).toContain('no questions');
    expect(parseImport('[{"points":1}]', 'r1').error).toBe('Question 1 has no title.');
  });

  it('keeps unlimited attempts and the files a question names', () => {
    const result = parseImport(JSON.stringify([
      { question: 'Size of the evidence', flags: [{ flag: '20464', caseSensitive: false }], maxAttempts: null, attachments: ['incident/access.log', ' '] },
      { question: 'No limit given', flags: [{ flag: 'x' }] },
      { question: 'Limited', flags: [{ flag: 'y' }], maxAttempts: 3 },
    ]), 'r1');
    expect(result.challenges.map(c => c.maxAttempts)).toEqual([null, null, 3]);
    expect(result.challenges[0].attachments).toEqual(['incident/access.log']);
    expect(result.challenges[0].flags[0].caseSensitive).toBeFalse();
  });
});

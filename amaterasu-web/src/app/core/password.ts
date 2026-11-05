// The API accepts 8 to 72 bytes; the app asks for more so new passwords are worth having.
export const MIN_PASSWORD = 12;
export const MAX_PASSWORD = 72;

export interface PasswordCheck {
  long: boolean;
  mixed: boolean;
  /** Different from the current password; true when there is none to compare. */
  different: boolean;
  /** 0 to 4. */
  score: number;
  label: 'Not set' | 'Too short' | 'Weak' | 'Fair' | 'Good' | 'Strong';
  ok: boolean;
}

const LABELS = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'] as const;

export function checkPassword(password: string, current?: string): PasswordCheck {
  const bytes = new TextEncoder().encode(password).length;
  const long = password.length >= MIN_PASSWORD && bytes <= MAX_PASSWORD;
  const mixed = /[a-zA-Z]/.test(password) && /[^a-zA-Z]/.test(password);
  const different = password.length > 0 && (current === undefined || password !== current);
  let score = 0;
  if (password.length >= 8) score++;
  if (long) score++;
  if (mixed) score++;
  if (password.length >= 16 || (/[A-Z]/.test(password) && /[0-9]/.test(password) && /[^a-zA-Z0-9]/.test(password))) score++;
  return {
    long,
    mixed,
    different,
    score,
    label: password.length ? LABELS[score] : 'Not set',
    ok: long && mixed && different,
  };
}

const WORDS = [
  'amber', 'basalt', 'cedar', 'delta', 'ember', 'falcon', 'garnet', 'harbor', 'indigo', 'juniper', 'kestrel',
  'lumen', 'meadow', 'nickel', 'onyx', 'pepper', 'quartz', 'raven', 'summit', 'tidal', 'umber', 'violet',
  'willow', 'zephyr', 'cobalt', 'granite', 'lantern', 'maple', 'orbit', 'saffron',
];

/** A temporary password that is easy to read out, such as tidal-Ember-4821-quartz. */
export function generatePassword(random: (max: number) => number = secureRandom): string {
  const word = () => WORDS[random(WORDS.length)];
  const second = word();
  const digits = String(1000 + random(9000));
  return `${word()}-${second[0].toUpperCase()}${second.slice(1)}-${digits}-${word()}`;
}

function secureRandom(max: number): number {
  const value = new Uint32Array(1);
  crypto.getRandomValues(value);
  return value[0] % max;
}

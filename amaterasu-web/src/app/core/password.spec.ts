import { checkPassword, generatePassword } from './password';

describe('password rules', () => {
  it('needs 12 characters mixing letters with numbers or symbols', () => {
    expect(checkPassword('short1').ok).toBeFalse();
    expect(checkPassword('onlylettershere').ok).toBeFalse();
    expect(checkPassword('letters-and-42').ok).toBeTrue();
  });

  it('must differ from the current password', () => {
    expect(checkPassword('letters-and-42', 'letters-and-42').different).toBeFalse();
    expect(checkPassword('letters-and-42', 'old-password-1').ok).toBeTrue();
  });

  it('rates strength', () => {
    expect(checkPassword('').label).toBe('Not set');
    expect(checkPassword('abc').label).toBe('Too short');
    expect(checkPassword('abcdefgh').label).toBe('Weak');
    expect(checkPassword('Tidal-Ember-4821-quartz').label).toBe('Strong');
  });

  it('generates readable passwords that pass the rules', () => {
    let n = 0;
    const password = generatePassword(max => (n++ * 7) % max);
    expect(password).toMatch(/^[a-z]+-[A-Z][a-z]+-\d{4}-[a-z]+$/);
    expect(checkPassword(password).ok).toBeTrue();
    expect(checkPassword(generatePassword()).ok).toBeTrue();
  });
});

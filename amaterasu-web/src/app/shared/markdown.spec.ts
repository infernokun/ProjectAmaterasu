import { parseInline, parseMarkdown, placeholdersIn, splitPlaceholders } from './markdown';

describe('parseMarkdown', () => {
  it('reads headings, paragraphs, lists, code and rules', () => {
    const blocks = parseMarkdown([
      '## Situation', 'The box went **quiet**.', 'Second line.', '', '- one', '- two', '', '1. first', '2. second', '',
      '```', 'ls -la {{address}}', '```', '---', '> careful',
    ].join('\n'));
    expect(blocks.map(block => block.kind)).toEqual(['heading', 'paragraph', 'list', 'list', 'code', 'rule', 'quote']);
    expect(blocks[0]).toEqual({ kind: 'heading', level: 2, inlines: [{ kind: 'text', text: 'Situation' }] });
    expect(blocks[1]).toEqual({ kind: 'paragraph', inlines: [
      { kind: 'text', text: 'The box went ' }, { kind: 'strong', children: [{ kind: 'text', text: 'quiet' }] },
      { kind: 'text', text: '. Second line.' }] });
    expect(blocks[2]).toEqual(jasmine.objectContaining({ ordered: false, items: [[{ kind: 'text', text: 'one' }], [{ kind: 'text', text: 'two' }]] }));
    expect(blocks[3]).toEqual(jasmine.objectContaining({ ordered: true, start: 1 }));
    expect(blocks[4]).toEqual({ kind: 'code', parts: ['ls -la ', { placeholder: 'address' }] });
  });

  it('keeps raw HTML as text', () => {
    const blocks = parseMarkdown('<script>alert(1)</script> <img src=x onerror=alert(1)>');
    expect(blocks).toEqual([{ kind: 'paragraph', inlines: [{ kind: 'text', text: '<script>alert(1)</script> <img src=x onerror=alert(1)>' }] }]);
  });

  it('drops unsafe link targets but keeps the text', () => {
    expect(parseInline('[click](javascript:alert(1))')).toEqual([{ kind: 'text', text: 'click' }]);
    expect(parseInline('[docs](https://example.com)')).toEqual([
      { kind: 'link', href: 'https://example.com', children: [{ kind: 'text', text: 'docs' }] }]);
  });

  it('fills placeholders in text and code, and leaves unknown ones', () => {
    expect(parseInline('ssh `{{username}}@{{address}}` pw {{password}} {{other}}')).toEqual([
      { kind: 'text', text: 'ssh ' },
      { kind: 'code', parts: [{ placeholder: 'username' }, '@', { placeholder: 'address' }] },
      { kind: 'text', text: ' pw ' },
      { kind: 'placeholder', name: 'password' },
      { kind: 'text', text: ' {{other}}' },
    ]);
    expect(splitPlaceholders('{{ subnet }}')).toEqual([{ placeholder: 'subnet' }]);
    expect(placeholdersIn('{{password}} and {{address}} and {{password}}')).toEqual(['password', 'address']);
  });

  it('leaves snake_case alone and reads emphasis', () => {
    expect(parseInline('a snake_case_name')).toEqual([{ kind: 'text', text: 'a snake_case_name' }]);
    expect(parseInline('an _em_ and *em*')).toEqual([
      { kind: 'text', text: 'an ' }, { kind: 'em', children: [{ kind: 'text', text: 'em' }] },
      { kind: 'text', text: ' and ' }, { kind: 'em', children: [{ kind: 'text', text: 'em' }] }]);
  });
});

/**
 * A small Markdown reader for lab scenarios. It builds a tree that templates render as elements,
 * so raw HTML in the source is shown as text and never becomes markup.
 */

/** Placeholders a scenario can use; they are filled from the lab's running environment. */
export const PLACEHOLDERS = ['address', 'username', 'subnet', 'password'] as const;
export type PlaceholderName = (typeof PLACEHOLDERS)[number];

export type CodePart = string | { placeholder: PlaceholderName };

export type Inline =
  | { kind: 'text'; text: string }
  | { kind: 'code'; parts: CodePart[] }
  | { kind: 'strong'; children: Inline[] }
  | { kind: 'em'; children: Inline[] }
  | { kind: 'link'; href: string; children: Inline[] }
  | { kind: 'placeholder'; name: PlaceholderName }
  | { kind: 'br' };

export type Block =
  | { kind: 'heading'; level: number; inlines: Inline[] }
  | { kind: 'paragraph'; inlines: Inline[] }
  | { kind: 'list'; ordered: boolean; start: number; items: Inline[][] }
  | { kind: 'code'; parts: CodePart[] }
  | { kind: 'quote'; blocks: Block[] }
  | { kind: 'table'; align: ('left' | 'center' | 'right' | null)[]; header: Inline[][]; rows: Inline[][][] }
  | { kind: 'rule' };

const PLACEHOLDER = /^\{\{\s*(address|username|subnet|password)\s*\}\}/;
const FENCE = /^\s{0,3}(```|~~~)/;
const HEADING = /^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$/;
const RULE = /^\s{0,3}([-*_])(\s*\1){2,}\s*$/;
const QUOTE = /^\s{0,3}>\s?/;
const ITEM = /^\s*([-*+]|\d{1,9}[.)])\s+(.*)$/;
const SAFE_HREF = /^(https?:\/\/|mailto:|\/|#)/i;
const TABLE_RULE = /^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$/;

export function parseMarkdown(source: string | null | undefined): Block[] {
  const lines = (source ?? '').replace(/\r\n?/g, '\n').split('\n');
  return parseBlocks(lines);
}

function parseBlocks(lines: string[]): Block[] {
  const blocks: Block[] = [];
  let i = 0;
  const startsBlock = (line: string) => FENCE.test(line) || HEADING.test(line) || RULE.test(line) || QUOTE.test(line)
    || ITEM.test(line);
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) {
      i++;
      continue;
    }
    const fence = FENCE.exec(line);
    if (fence) {
      const body: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trimStart().startsWith(fence[1])) {
        body.push(lines[i]);
        i++;
      }
      i++;
      blocks.push({ kind: 'code', parts: splitPlaceholders(body.join('\n')) });
      continue;
    }
    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({ kind: 'heading', level: heading[1].length, inlines: parseInline(heading[2]) });
      i++;
      continue;
    }
    if (RULE.test(line)) {
      blocks.push({ kind: 'rule' });
      i++;
      continue;
    }
    if (QUOTE.test(line)) {
      const body: string[] = [];
      while (i < lines.length && lines[i].trim() && QUOTE.test(lines[i])) {
        body.push(lines[i].replace(QUOTE, ''));
        i++;
      }
      blocks.push({ kind: 'quote', blocks: parseBlocks(body) });
      continue;
    }
    if (line.includes('|') && i + 1 < lines.length && TABLE_RULE.test(lines[i + 1]) && lines[i + 1].includes('-')) {
      const header = splitRow(line);
      const align = splitRow(lines[i + 1]).map(cell => {
        const left = cell.startsWith(':');
        const right = cell.endsWith(':');
        return left && right ? 'center' as const : right ? 'right' as const : left ? 'left' as const : null;
      });
      i += 2;
      const rows: Inline[][][] = [];
      while (i < lines.length && lines[i].trim() && lines[i].includes('|')) {
        const cells = splitRow(lines[i]);
        rows.push(header.map((_, c) => parseInline(cells[c] ?? '')));
        i++;
      }
      blocks.push({ kind: 'table', align: header.map((_, c) => align[c] ?? null), header: header.map(cell => parseInline(cell)), rows });
      continue;
    }
    const item = ITEM.exec(line);
    if (item) {
      const ordered = /\d/.test(item[1]);
      const items: string[][] = [];
      while (i < lines.length) {
        const current = ITEM.exec(lines[i]);
        if (current && /\d/.test(current[1]) === ordered) {
          items.push([current[2]]);
          i++;
        } else if (lines[i].trim() && !startsBlock(lines[i]) && items.length) {
          items[items.length - 1].push(lines[i].trim());
          i++;
        } else if (!lines[i].trim() && i + 1 < lines.length && ITEM.test(lines[i + 1])
          && /\d/.test(ITEM.exec(lines[i + 1])![1]) === ordered) {
          i++;
        } else {
          break;
        }
      }
      blocks.push({
        kind: 'list', ordered, start: ordered ? parseInt(item[1], 10) : 1,
        items: items.map(parts => parseInline(parts.join('\n'))),
      });
      continue;
    }
    const body: string[] = [];
    while (i < lines.length && lines[i].trim() && !(body.length && startsBlock(lines[i]))) {
      body.push(lines[i]);
      i++;
    }
    blocks.push({ kind: 'paragraph', inlines: parseInline(body.join('\n')) });
  }
  return blocks;
}

/** A table row's cells; a pipe inside backticks or after a backslash stays in its cell. */
function splitRow(line: string): string[] {
  const cells: string[] = [];
  let cell = '';
  let code = false;
  const text = line.trim().replace(/^\|/, '');
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '\\' && text[i + 1] === '|') {
      cell += '|';
      i++;
    } else if (ch === '`') {
      code = !code;
      cell += ch;
    } else if (ch === '|' && !code) {
      cells.push(cell.trim());
      cell = '';
    } else {
      cell += ch;
    }
  }
  if (cell.trim() || !text.endsWith('|')) cells.push(cell.trim());
  return cells;
}

export function parseInline(source: string): Inline[] {
  const out: Inline[] = [];
  let text = '';
  const flush = () => {
    if (text) {
      out.push({ kind: 'text', text });
      text = '';
    }
  };
  let i = 0;
  while (i < source.length) {
    const rest = source.slice(i);
    const ch = rest[0];
    let match: RegExpExecArray | null;
    if (ch === '\\' && rest.length > 1) {
      if (rest[1] === '\n') {
        flush();
        out.push({ kind: 'br' });
      } else {
        text += /[\\`*_{}[\]()#+\-.!>~|]/.test(rest[1]) ? rest[1] : rest.slice(0, 2);
      }
      i += 2;
      continue;
    }
    if (ch === '\n') {
      if (/ {2,}$/.test(text)) {
        text = text.replace(/ +$/, '');
        flush();
        out.push({ kind: 'br' });
      } else {
        text = text.replace(/ +$/, '') + ' ';
      }
      i++;
      continue;
    }
    if (ch === '`' && (match = /^(`+)([\s\S]*?[^`])\1(?!`)/.exec(rest))) {
      flush();
      out.push({ kind: 'code', parts: splitPlaceholders(match[2].replace(/\n/g, ' ').replace(/^ (.+) $/, '$1')) });
      i += match[0].length;
      continue;
    }
    if (ch === '{' && (match = PLACEHOLDER.exec(rest))) {
      flush();
      out.push({ kind: 'placeholder', name: match[1] as PlaceholderName });
      i += match[0].length;
      continue;
    }
    if (ch === '[' && (match = /^\[([^\]]+)\]\(\s*((?:[^()\s]|\([^()\s]*\))+)(?:\s+"[^"]*")?\s*\)/.exec(rest))) {
      flush();
      if (SAFE_HREF.test(match[2])) {
        out.push({ kind: 'link', href: match[2], children: parseInline(match[1]) });
      } else {
        out.push(...parseInline(match[1]));
      }
      i += match[0].length;
      continue;
    }
    if ((ch === '*' || ch === '_') && (match = /^(\*\*|__)(?=\S)([\s\S]*?\S)\1/.exec(rest)) && delimited(source, i, match)) {
      flush();
      out.push({ kind: 'strong', children: parseInline(match[2]) });
      i += match[0].length;
      continue;
    }
    if ((ch === '*' || ch === '_') && (match = /^([*_])(?=[^\s*_])([\s\S]*?[^\s\\])\1(?![*_])/.exec(rest)) && delimited(source, i, match)) {
      flush();
      out.push({ kind: 'em', children: parseInline(match[2]) });
      i += match[0].length;
      continue;
    }
    text += ch;
    i++;
  }
  flush();
  return out;
}

/** Underscores only emphasise between word boundaries, so snake_case stays as written. */
function delimited(source: string, at: number, match: RegExpExecArray): boolean {
  if (match[1][0] !== '_') {
    return true;
  }
  const before = source[at - 1] ?? '';
  const after = source[at + match[0].length] ?? '';
  return !/\w/.test(before) && !/\w/.test(after);
}

export function splitPlaceholders(text: string): CodePart[] {
  const parts: CodePart[] = [];
  const pattern = /\{\{\s*(address|username|subnet|password)\s*\}\}/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text))) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    parts.push({ placeholder: match[1] as PlaceholderName });
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

/** The placeholders a scenario uses, in order of first use. */
export function placeholdersIn(source: string | null | undefined): PlaceholderName[] {
  const found = new Set<PlaceholderName>();
  for (const match of (source ?? '').matchAll(/\{\{\s*(address|username|subnet|password)\s*\}\}/g)) {
    found.add(match[1] as PlaceholderName);
  }
  return [...found];
}

/** The template a new lab's scenario starts from. */
export const SCENARIO_TEMPLATE = `## Situation
What is going on, and who the players are in this story.

## Objectives
- What players need to find or do

## Getting in
Connect with \`ssh {{username}}@{{address}}\`. The password is {{password}}.

## Rules
- Stay inside the lab network.
`;

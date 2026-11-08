import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, TemplateRef, computed, input } from '@angular/core';
import { parseMarkdown } from '../markdown';

/**
 * Renders Markdown as elements, never as HTML, so a scenario cannot inject markup or scripts.
 * `placeholder` renders {{address}} and the like; without it they show as written.
 */
@Component({
  selector: 'app-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet],
  template: `
    @for (block of blocks(); track $index) {
      <ng-container *ngTemplateOutlet="blockTpl; context: { $implicit: block }" />
    }

    <ng-template #blockTpl let-block>
      @switch (block.kind) {
        @case ('heading') {
          @switch (block.level) {
            @case (1) { <h2 class="md-h md-h1"><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: block.inlines }" /></h2> }
            @case (2) { <h3 class="md-h md-h2"><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: block.inlines }" /></h3> }
            @default { <h4 class="md-h md-h3"><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: block.inlines }" /></h4> }
          }
        }
        @case ('paragraph') { <p><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: block.inlines }" /></p> }
        @case ('list') {
          @if (block.ordered) {
            <ol [attr.start]="block.start === 1 ? null : block.start">
              @for (item of block.items; track $index) { <li><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: item }" /></li> }
            </ol>
          } @else {
            <ul>
              @for (item of block.items; track $index) { <li><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: item }" /></li> }
            </ul>
          }
        }
        @case ('code') { <pre class="code"><ng-container *ngTemplateOutlet="partsTpl; context: { $implicit: block.parts }" /></pre> }
        @case ('quote') {
          <blockquote>
            @for (inner of block.blocks; track $index) { <ng-container *ngTemplateOutlet="blockTpl; context: { $implicit: inner }" /> }
          </blockquote>
        }
        @case ('table') {
          <div class="md-table">
            <table class="t">
              <thead><tr>
                @for (cell of block.header; track $index; let c = $index) {
                  <th [style.text-align]="block.align[c]"><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: cell }" /></th>
                }
              </tr></thead>
              <tbody>
                @for (row of block.rows; track $index) {
                  <tr>
                    @for (cell of row; track $index; let c = $index) {
                      <td [style.text-align]="block.align[c]"><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: cell }" /></td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
        @case ('rule') { <hr> }
      }
    </ng-template>

    <ng-template #inlineTpl let-nodes>
      @for (node of nodes; track $index) {
        @switch (node.kind) {
          @case ('text') {<ng-container>{{ node.text }}</ng-container>}
          @case ('code') { <code><ng-container *ngTemplateOutlet="partsTpl; context: { $implicit: node.parts }" /></code> }
          @case ('strong') { <strong><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: node.children }" /></strong> }
          @case ('em') { <em><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: node.children }" /></em> }
          @case ('link') { <a [href]="node.href" target="_blank" rel="noopener noreferrer"><ng-container *ngTemplateOutlet="inlineTpl; context: { $implicit: node.children }" /></a> }
          @case ('placeholder') { <ng-container *ngTemplateOutlet="placeholderTpl; context: { $implicit: node.name, code: false }" /> }
          @case ('br') { <br> }
        }
      }
    </ng-template>

    <ng-template #partsTpl let-parts>
      @for (part of parts; track $index) {
        @if (part.placeholder) {
          <ng-container *ngTemplateOutlet="placeholderTpl; context: { $implicit: part.placeholder, code: true }" />
        } @else {<ng-container>{{ part }}</ng-container>}
      }
    </ng-template>

    <ng-template #placeholderTpl let-name let-code="code">
      @if (placeholder(); as custom) {
        <ng-container *ngTemplateOutlet="custom; context: { $implicit: name, code: code }" />
      } @else {<ng-container>{{ raw(name) }}</ng-container>}
    </ng-template>
  `,
  styles: `
    :host { display: block; line-height: 1.6; }
    :host > :first-child { margin-top: 0; }
    :host > :last-child { margin-bottom: 0; }
    p, ul, ol, pre, blockquote { margin: 0 0 12px; }
    ul, ol { padding-left: 22px; }
    li + li { margin-top: 4px; }
    .md-h { margin: 18px 0 8px; line-height: 1.3; }
    .md-h1 { font-size: 19px; }
    .md-h2 { font-size: 16px; }
    .md-h3 { font-size: 14.5px; }
    code { font-family: var(--font-mono); font-size: 0.92em; background: var(--surface-2); border: 1px solid var(--border);
      border-radius: 6px; padding: 1px 5px; overflow-wrap: anywhere; }
    pre.code code { border: 0; padding: 0; background: none; }
    blockquote { border-left: 3px solid var(--border); padding-left: 12px; color: var(--text-2); }
    hr { border: 0; border-top: 1px solid var(--border); margin: 16px 0; }
    a { color: var(--accent); }
    .md-table { overflow-x: auto; margin: 0 0 12px; border: 1px solid var(--border); border-radius: 8px; }
    .md-table table { width: 100%; font-size: 0.95em; }
  `,
})
export class MarkdownComponent {
  readonly source = input<string | null | undefined>('');
  /** Renders one placeholder; its context is the name and whether it sits in code. */
  readonly placeholder = input<TemplateRef<{ $implicit: string; code: boolean }> | null>(null);

  protected readonly blocks = computed(() => parseMarkdown(this.source()));

  protected raw(name: string): string {
    return `{{${name}}}`;
  }
}

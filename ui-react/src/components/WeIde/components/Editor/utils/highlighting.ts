import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { tags as t } from '@lezer/highlight';

export const customHighlightStyle = HighlightStyle.define([
  { tag: t.keyword, color: '#c678dd' },
  { tag: t.operator, color: '#56b6c2' },
  { tag: t.string, color: '#98c379' },
  { tag: t.number, color: '#d19a66' },
  { tag: t.propertyName, color: '#e06c75' },
  { tag: t.comment, color: '#5c6370', fontStyle: 'italic' },
  { tag: t.function(t.variableName), color: '#61afef' },
  { tag: t.definition(t.variableName), color: '#e06c75' },
  { tag: t.typeName, color: '#e5c07b' },
  { tag: t.className, color: '#e5c07b' },
  { tag: t.bracket, color: '#abb2bf' },
  { tag: [t.regexp, t.meta, t.changed, t.namespace], color: '#56b6c2' },
]);

export const highlightExtension = syntaxHighlighting(customHighlightStyle);
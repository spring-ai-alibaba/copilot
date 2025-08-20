import { KeyBinding } from '@codemirror/view';
import { historyKeymap } from '@codemirror/commands';
import { editorCommands } from './commands';

export const editorKeymap: KeyBinding[] = [
  { key: 'Mod-s', run: editorCommands.save },
  ...historyKeymap // Add undo/redo keyboard shortcuts
];
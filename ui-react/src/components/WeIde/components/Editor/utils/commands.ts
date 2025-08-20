import { EditorView } from '@codemirror/view';
import { useEditorStore } from '../../../stores/editorStore';
import { EditorSelection } from '@codemirror/state';
import { useFileStore } from '../../../stores/fileStore';

export const editorCommands = {
  save: (view: EditorView) => {
    const { setDirty, currentFile } = useEditorStore.getState();
    const { updateContent } = useFileStore.getState();
    
    const path = currentFile || '';
    if (!path) {
      console.warn('No file path found when trying to save');
      return false;
    }
    
    const content = view.state.doc.toString();
    updateContent(path, content);
    setDirty(path, false);

    return true;
  },
  
  jumpToLine: (view: EditorView, line: number) => {
    const doc = view.state.doc;
    const targetLine = Math.min(line, doc.lines);
    const linePos = doc.line(targetLine);
    
    view.dispatch({
      selection: EditorSelection.single(linePos.from),
      scrollIntoView: true,
      effects: EditorView.scrollIntoView(linePos.from, {
        y: 'center',
        yMargin: 50
      })
    });

    view.focus();
    return true;
  }
};
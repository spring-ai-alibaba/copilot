import { useCallback } from 'react';
import { useEditorStore } from '../stores/editorStore';

export function useUnsavedChanges() {
  const { isDirty } = useEditorStore();

  const checkUnsavedChanges = useCallback((paths?: string[]) => {
    const filesToCheck = paths || Object.keys(isDirty);
    const unsavedFiles = filesToCheck.filter(path => isDirty[path]);
    
    if (unsavedFiles.length > 0) {
      return window.confirm(
        `You have unsaved changes in ${unsavedFiles.length} file(s). Do you want to continue?`
      );
    }
    
    return true;
  }, [isDirty]);

  return { checkUnsavedChanges };
}
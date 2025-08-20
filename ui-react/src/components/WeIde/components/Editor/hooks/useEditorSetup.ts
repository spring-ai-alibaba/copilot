import { useEffect, useMemo, useRef } from "react";
import { EditorView, keymap } from "@codemirror/view";
import { EditorState, Extension } from "@codemirror/state";
import { defaultKeymap, indentWithTab, copyLineDown, copyLineUp } from "@codemirror/commands";
import { getLanguageExtension } from "../utils/language";
import { editorKeymap } from "../utils/keymap";
import { editorTheme } from "../config/theme";
import { autocompletion, completionKeymap } from "@codemirror/autocomplete";
import {
  createDiffExtension,
} from "../utils/diff";
import { useEditorStore } from "@/components/WeIde/stores/editorStore";


interface UseEditorSetupProps {
  fileName: string;
  fileContent: string;
  onDocChange: () => void;
  customExtensions?: Extension[];
}

export const useEditorSetup = ({
  fileName,
  fileContent,
  onDocChange,
  customExtensions = [],
}: UseEditorSetupProps) => {
  const editorRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView>();
  const prevContentRef = useRef<string>(fileContent);
  const { isDirty } = useEditorStore();
  const contentRef = useRef<{ [key: string]: string }>({});
  const autoParams = useMemo(
    () =>
      autocompletion({
        activateOnTyping: true,
        maxRenderedOptions: 10,
        defaultKeymap: true,
        icons: true,
      }),
    []
  );

  const extensions = useMemo(() => {
    return [
      createDiffExtension(),
      keymap.of([
        ...defaultKeymap,
        indentWithTab,
        {
          key: "Mod-c",
          run: (view) => {
            const selection = view.state.selection.main;
            const text = selection.empty
              ? view.state.doc.line(selection.head).text  // 如果没有选中文本，复制当前行
              : view.state.sliceDoc(selection.from, selection.to);  // 复制选中的文本
            navigator.clipboard.writeText(text);
            return true;
          }
        },
        {
          key: "Mod-v",
          run: (view) => {
            navigator.clipboard.readText().then(text => {
              view.dispatch(view.state.replaceSelection(text));
            });
            return true;
          }
        }
      ]),
      keymap.of(editorKeymap),
      getLanguageExtension(fileName),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          if(contentRef.current[fileName] !== update.state.doc.toString()){
            onDocChange();
          }
          contentRef.current[fileName] = update.state.doc.toString();
        }
      }),
      editorTheme,
      autoParams,
    
      EditorView.contentAttributes.of({
        contenteditable: "true",
        spellcheck: "false",
      }),
      
      EditorView.domEventHandlers({
        contextmenu: (event, view) => {
          return false; 
        }
      }),
      
      ...customExtensions,
    ].filter(Boolean);
  }, [fileName, customExtensions]);

  // init
  useEffect(() => {
    if (!editorRef.current) return;

    const state = EditorState.create({
      doc: fileContent,
      extensions: extensions,
    });

    const view = new EditorView({
      state,
      parent: editorRef.current,
    });

    viewRef.current = view;

    return () => view.destroy();
  }, [fileName]);

  // update
  useEffect(() => {
    const view = viewRef.current;
    if (!view || fileContent === prevContentRef.current) return;

    if(!isDirty?.[fileName]){
      contentRef.current[fileName] = fileContent;
    }

    view.dispatch({
      changes: {
        from: 0,
        to: view.state.doc.length,
        insert: isDirty?.[fileName] ?  contentRef.current[fileName] : fileContent,
      },
    });
    

    prevContentRef.current = fileContent;
  }, [fileContent, isDirty]);

  return {
    editorRef,
    viewRef,
  };
};

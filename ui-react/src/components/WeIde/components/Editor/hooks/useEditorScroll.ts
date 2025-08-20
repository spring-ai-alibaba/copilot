import { useEffect, useRef } from "react";
import { EditorView } from "@codemirror/view";

interface UseEditorScrollProps {
  view: EditorView | undefined;
  fileContent: string;
}

export const useEditorScroll = ({
  view,
  fileContent,
}: UseEditorScrollProps) => {
  const userScrolledUpRef = useRef(false);
  const prevContentRef = useRef(fileContent);

  useEffect(() => {
    if (!view || fileContent === prevContentRef.current) return;

    const scroller = view.scrollDOM;
    const handleScroll = () => {
      const isNearBottom =
        scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight <
        100;
      userScrolledUpRef.current = !isNearBottom;
    };

    scroller.addEventListener("scroll", handleScroll);

    view.dispatch({
      changes: {
        from: 0,
        to: view.state.doc.length,
        insert: fileContent,
      },
    });

    if (fileContent.length > prevContentRef.current.length) {
      const isNearBottom =
        scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight <
        300;

      if (isNearBottom && !userScrolledUpRef.current) {
        scroller.scrollTop = scroller.scrollHeight;
      }
    }

    prevContentRef.current = fileContent;
    return () => scroller.removeEventListener("scroll", handleScroll);
  }, [fileContent, view]);

  return {
    userScrolledUpRef,
  };
};

export interface EditorProps {
  fileName: string;
}

export interface EditorState {
  content: string;
  selection: {
    start: number;
    end: number;
  };
  scrollPosition: {
    top: number;
    left: number;
  };
  history: {
    past: string[];
    future: string[];
  };
}

export interface CodeHighlighterProps {
  code: string;
  language: string;
  scrollTop: number;
  scrollLeft: number;
}
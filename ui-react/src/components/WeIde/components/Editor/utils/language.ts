import { LanguageSupport } from "@codemirror/language";
import { javascript } from "@codemirror/lang-javascript";
import { Extension } from "@codemirror/state";
import { foldGutter, foldKeymap } from "@codemirror/language";
import { keymap } from "@codemirror/view";
import { highlightExtension } from "./highlighting";
import { searchKeymap } from "@codemirror/search";
import { lineNumbers } from "@codemirror/view";
import { history } from "@codemirror/commands";
import { markdown } from "@codemirror/lang-markdown";
import { python } from "@codemirror/lang-python";
import { css } from "@codemirror/lang-css";
import { json } from "@codemirror/lang-json";
import { html } from "@codemirror/lang-html";

export function getLanguageExtension(fileName: string): Extension[] {
  if (!fileName) return [javascript()];

  const extension = fileName.split(".").pop()?.toLowerCase();
  let languageSupport: LanguageSupport;

  switch (extension) {
    case "ts":
      languageSupport = javascript({ typescript: true });
      break;
    case "tsx":
      languageSupport = javascript({ typescript: true, jsx: true });
      break;
    case "jsx":
      languageSupport = javascript({ jsx: true });
      break;
    case "md":
      languageSupport = markdown();
      break;
    case "py":
      languageSupport = python();
      break;
    case "css":
      languageSupport = css();
      break;
    case "json":
      languageSupport = json();
      break;
    case "html":
      languageSupport = html();
      break;

    default:
      languageSupport = javascript();
  }

  return [
    languageSupport,
    highlightExtension,
    lineNumbers(),
    foldGutter(),
    history(), // Add history extension for undo/redo
    keymap.of([...foldKeymap, ...searchKeymap]),
  ];
}

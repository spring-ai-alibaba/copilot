import { EditorView } from "@codemirror/view";

export const editorTheme = EditorView.theme({
  "&": {
    height: "100%",
    width: "100%",
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    fontSize: "14px",
    fontFamily: '"Fira Code", monospace',
    backgroundColor: "#18181a",
  },
  "&.cm-focused": {
    outline: "none !important",
  },
  ".cm-scroller": {
    fontFamily: '"Fira Code", monospace',
    lineHeight: "1.6",
    height: "100%",
    overflow: "auto",
  },
  ".cm-content": {
    padding: "4px 0",
    minHeight: "100%",
    caretColor: "#fff !important",
  },
  ".cm-gutters": {
    backgroundColor: "#18181a !important",
    border: "none !important",
    minWidth: "32px",
    position: "sticky",
    left: 0,
    paddingRight: 0,
  },
  ".cm-gutter": {
    minHeight: "100%",
  },
  ".cm-lineNumbers": {
    color: "#858585",
    minWidth: "3ch",
    padding: "0 8px 0 0",
  },
  ".cm-activeLineGutter": {
    backgroundColor: "#282828 !important",
    color: "#c6c6c6",
  },
  ".cm-cursor": {
    borderLeft: "2px solid #fff !important",
    borderRight: "none !important",
    backgroundColor: "transparent !important",
    marginLeft: "-1px",
    width: "2px !important",
  },
  ".cm-line": {
    padding: "0 4px",
  },
  ".cm-activeLine": {
    backgroundColor: "#282828",
  },

  // 代码提示弹窗容器
  ".cm-tooltip": {
    backgroundColor: "#1a1a1c",
    border: "1px solid #454545",
    borderRadius: "6px",
    boxShadow: "0 4px 12px rgba(0, 0, 0, 0.25)",
    overflow: "hidden", // 确保内容不会超出圆角
    animation: "cm-appear 0.1s ease-out",
  },

  // 自动完成提示框
  ".cm-tooltip.cm-tooltip-autocomplete": {
    "& > ul": {
      backgroundColor: "#1a1a1c",
      border: "none",
      fontSize: "14px",
      maxHeight: "300px",
      padding: "6px 0",
      margin: 0,
    },
  },

  // 提示选项
  ".cm-tooltip-autocomplete ul li": {
    lineHeight: "1.5",
    padding: "6px 12px 6px 16px",
    margin: "0 4px",
    borderRadius: "4px",
    cursor: "pointer",
    transition: "all 0.1s ease",
    color: "#d4d4d4",
  },

  // 提示选项悬停效果
  ".cm-tooltip-autocomplete ul li:hover": {
    backgroundColor: "rgba(90, 93, 94, 0.31)",
  },

  // 当前选中的提示选项
  ".cm-tooltip-autocomplete ul li[aria-selected]": {
    backgroundColor: "rgba(9, 71, 113, 0.6)",
    color: "#ffffff",
  },

  // 提示选项中的匹配文本高亮
  ".cm-completionMatchedText": {
    color: "#4fc1ff",
    textDecoration: "none",
    fontWeight: "600",
    textShadow: "0 0 0.5px rgba(79, 193, 255, 0.3)",
  },

  // 提示选项的图标和类型标签
  ".cm-completionIcon": {
    marginRight: "10px",
    color: "#858585",
    fontSize: "0.9em",
    opacity: "0.85",
  },

  // 不同类型提示的图标样式 - 使用更现代的图标
  ".cm-completionIcon-function": {
    color: "#c586c0",
    "&:after": { content: "'λ'" },
  },
  ".cm-completionIcon-variable": {
    color: "#9cdcfe",
    "&:after": { content: "'α'" },
  },
  ".cm-completionIcon-class": {
    color: "#4ec9b0",
    "&:after": { content: "'⬡'" },
  },
  ".cm-completionIcon-interface": {
    color: "#4ec9b0",
    "&:after": { content: "'⬢'" },
  },
  ".cm-completionIcon-keyword": {
    color: "#569cd6",
    "&:after": { content: "'⌘'" },
  },
  ".cm-completionIcon-property": {
    color: "#9cdcfe",
    "&:after": { content: "'◇'" },
  },
  ".cm-completionIcon-method": {
    color: "#dcdcaa",
    "&:after": { content: "'⚡'" },
  },

  // 提示信息的详细说明框
  ".cm-tooltip.cm-completionInfo": {
    backgroundColor: "#2c2c2d",
    border: "1px solid #454545",
    padding: "12px",
    marginTop: "-1px",
    position: "absolute",
    minWidth: "300px",
    maxWidth: "400px",
    borderRadius: "6px",
    boxShadow: "0 4px 12px rgba(0, 0, 0, 0.2)",
    lineHeight: "1.6",
  },

  // 详细信息中的文档说明
  ".cm-completionInfo.cm-completionInfo-right": {
    "& p": {
      margin: "0 0 8px 0",
      color: "#d4d4d4",
      fontSize: "13px",
    },
    "& b": {
      color: "#9cdcfe",
      fontWeight: "600",
    },
    "& code": {
      backgroundColor: "rgba(255, 255, 255, 0.1)",
      padding: "2px 4px",
      borderRadius: "3px",
      fontSize: "12px",
    },
  },

  // 参数提示框
  ".cm-tooltip.cm-tooltip-signature": {
    backgroundColor: "#1a1a1c",
    border: "1px solid #454545",
    color: "#d4d4d4",
    padding: "8px 12px",
    borderRadius: "6px",
    boxShadow: "0 4px 12px rgba(0, 0, 0, 0.2)",
    fontSize: "13px",
  },

  // 参数提示中的当前参数高亮
  ".cm-tooltip-signature .cm-signature-activeParameter": {
    color: "#4fc1ff",
    fontWeight: "600",
    textDecoration: "underline",
    textUnderlineOffset: "2px",
  },

  // 提示框中的滚动条样式
  ".cm-tooltip-autocomplete ul::-webkit-scrollbar": {
    width: "6px",
  },
  ".cm-tooltip-autocomplete ul::-webkit-scrollbar-track": {
    background: "transparent",
    margin: "6px 0",
  },
  ".cm-tooltip-autocomplete ul::-webkit-scrollbar-thumb": {
    backgroundColor: "rgba(255, 255, 255, 0.2)",
    borderRadius: "3px",
    transition: "background-color 0.2s",
  },
  ".cm-tooltip-autocomplete ul::-webkit-scrollbar-thumb:hover": {
    backgroundColor: "rgba(255, 255, 255, 0.3)",
  },

  // 添加动画效果
  "@keyframes cm-appear": {
    from: {
      opacity: 0,
      transform: "translateY(-4px)",
    },
    to: {
      opacity: 1,
      transform: "translateY(0)",
    },
  },
});

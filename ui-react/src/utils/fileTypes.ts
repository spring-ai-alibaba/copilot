// Utility functions for handling file types and content
export const getLanguageClass = (fileName: string) => {
  if (fileName.endsWith(".html")) return "language-html";
  if (fileName.endsWith(".tsx") || fileName.endsWith(".ts"))
    return "language-typescript";
  if (fileName.endsWith(".css")) return "language-css";
  if (fileName.endsWith(".json")) return "language-json";
  return "language-plaintext";
};

export const getFileLanguage = (fileName: string) => {
  if (fileName.endsWith(".tsx")) return "TypeScript React";
  if (fileName.endsWith(".ts")) return "TypeScript";
  if (fileName.endsWith(".html")) return "HTML";
  if (fileName.endsWith(".css")) return "CSS";
  if (fileName.endsWith(".json")) return "JSON";
  return "Plain Text";
};

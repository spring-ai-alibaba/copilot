export function getLanguage(fileName: string): string {
  if (fileName.endsWith('.tsx')) return 'tsx';
  if (fileName.endsWith('.ts')) return 'typescript';
  if (fileName.endsWith('.jsx')) return 'jsx';
  if (fileName.endsWith('.js')) return 'javascript';
  if (fileName.endsWith('.html')) return 'html';
  if (fileName.endsWith('.css')) return 'css';
  if (fileName.endsWith('.json')) return 'json';
  return 'plaintext';
}
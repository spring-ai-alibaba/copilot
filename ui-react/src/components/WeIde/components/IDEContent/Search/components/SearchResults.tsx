import { FileCode, FileText } from 'lucide-react';

import '../styles/search.css';
import { cn } from '@/utils/cn';

interface SearchMatch {
  path: string;
  matches: Array<{
    line: number;
    content: string;
    index: number;
  }>;
}

interface SearchResultsProps {
  results: SearchMatch[];
  currentMatchIndex: number;
  onFileSelect: (path: string, line: number) => void;
  className?: string;
}

export function SearchResults({ 
  results, 
  currentMatchIndex,
  onFileSelect, 
  className 
}: SearchResultsProps) {
  if (results.length === 0) return null;

  let matchCounter = 0;

  return (
    <div className={cn("mt-2 space-y-1 overflow-y-auto max-h-[calc(100vh-120px)]", className)}>
      {results.map((result) => (
        <div key={result.path} className="space-y-0.5">
          <div className="flex items-center text-[13px] px-2 text-[#6F6F6F] dark:text-[#CCCCCC]">
            {result.path.endsWith('.tsx') || result.path.endsWith('.ts') ? (
              <FileCode className="w-3.5 h-3.5 mr-1.5 flex-shrink-0 text-[#3794FF] dark:text-[#4FC1FF]" />
            ) : (
              <FileText className="w-3.5 h-3.5 mr-1.5 flex-shrink-0 text-[#6F6F6F] dark:text-[#CCCCCC]" />
            )}
            <span className="truncate">{result.path}</span>
          </div>
          {result.matches.map((match) => {
            const isCurrentMatch = matchCounter === currentMatchIndex;
            matchCounter++;
            
            return (
              <div
                key={`${match.line}-${match.index}`}
                onClick={() => onFileSelect(result.path, match.line)}
                className={cn(
                  "ml-6 text-[12px] cursor-pointer rounded px-2 py-0.5",
                  "text-[#4B4B4B] dark:text-[#BBBBBB]",
                  "hover:bg-[#F0F0F0] dark:hover:bg-[#2A2D2E]",
                  isCurrentMatch && "bg-[#E4E6F1] dark:bg-[#094771] text-[#333333] dark:text-[#FFFFFF]"
                )}
              >
                <div className="flex items-center space-x-2">
                  <span className="w-8 text-[#A0A0A0] dark:text-[#858585]">{match.line}</span>
                  <span className="truncate">{match.content}</span>
                </div>
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}
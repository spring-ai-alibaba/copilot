import { useFileStore } from '@/components/WeIde/stores/fileStore';
import { useState, useCallback, useMemo } from 'react';


interface SearchMatch {
  path: string;
  matches: Array<{
    line: number;
    content: string;
    index: number;
  }>;
}

export function useFileSearch() {
  const [searchQuery, setSearchQuery] = useState('');
  const [currentMatchIndex, setCurrentMatchIndex] = useState(0);
  const { files } = useFileStore();

  const searchResults = useMemo(() => {
    if (!searchQuery) return [];

    const query = searchQuery.toLowerCase();
    const results: SearchMatch[] = [];

    Object.entries(files).forEach(([path, content]) => {
      const matches: SearchMatch['matches'] = [];
      const lines = content.split('\n');

      lines.forEach((line, lineIndex) => {
        let index = line.toLowerCase().indexOf(query);
        while (index !== -1) {
          matches.push({
            line: lineIndex + 1,
            content: line.trim(),
            index
          });
          index = line.toLowerCase().indexOf(query, index + 1);
        }
      });

      if (matches.length > 0) {
        results.push({ path, matches });
      }
    });

    return results;
  }, [files, searchQuery]);

  const totalMatches = useMemo(() => 
    searchResults.reduce((total, result) => total + result.matches.length, 0),
    [searchResults]
  );

  const handleSearch = useCallback((query: string) => {
    setSearchQuery(query);
    setCurrentMatchIndex(0);
  }, []);

  const navigateMatch = useCallback((direction: 'prev' | 'next') => {
    if (totalMatches === 0) return;

    setCurrentMatchIndex(current => {
      if (direction === 'next') {
        return (current + 1) % totalMatches;
      } else {
        return (current - 1 + totalMatches) % totalMatches;
      }
    });
  }, [totalMatches]);

  return {
    searchQuery,
    searchResults,
    currentMatchIndex,
    totalMatches,
    handleSearch,
    navigateMatch
  };
}
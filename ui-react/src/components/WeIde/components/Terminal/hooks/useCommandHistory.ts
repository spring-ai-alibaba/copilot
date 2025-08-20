import { useState, useCallback } from 'react';

const MAX_HISTORY = 100;

export function useCommandHistory() {
  const [history, setHistory] = useState<string[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  const addToHistory = useCallback((command: string) => {
    setHistory(prev => {
      const newHistory = [command, ...prev.slice(0, MAX_HISTORY - 1)];
      setHistoryIndex(-1);
      return newHistory;
    });
  }, []);

  const getPrevious = useCallback(() => {
    if (historyIndex + 1 < history.length) {
      setHistoryIndex(prev => prev + 1);
      return history[historyIndex + 1];
    }
    return null;
  }, [history, historyIndex]);

  const getNext = useCallback(() => {
    if (historyIndex > 0) {
      setHistoryIndex(prev => prev - 1);
      return history[historyIndex - 1];
    } else if (historyIndex === 0) {
      setHistoryIndex(-1);
      return '';
    }
    return null;
  }, [history, historyIndex]);

  return {
    history,
    addToHistory,
    getPrevious,
    getNext
  };
}
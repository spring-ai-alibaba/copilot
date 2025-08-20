import React from 'react';
import { create } from 'zustand';
import { cn } from '@/lib/utils';

interface LoadingStore {
  isLoading: boolean;
  setLoading: (loading: boolean) => void;
}

const useLoadingStore = create<LoadingStore>((set) => ({
  isLoading: false,
  setLoading: (loading) => set({ isLoading: loading }),
}));

export const useLoading = () => {
  const { isLoading, setLoading } = useLoadingStore();
  return { isLoading, setLoading };
};

export const Loading: React.FC = () => {
  const { isLoading } = useLoading();

  if (!isLoading) return null;

  return (
    <div
      className={cn(
        'fixed inset-0 z-50 flex items-center justify-center',
        'bg-background/80 backdrop-blur-sm'
      )}
    >
      <div className="flex flex-col items-center space-y-4">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        <span className="text-sm text-muted-foreground">加载中...</span>
      </div>
    </div>
  );
};
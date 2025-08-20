import { useEffect, useRef } from 'react';

interface DialogProps {
  title: string;
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
}

export function Dialog({ title, isOpen, onClose, children }: DialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isOpen) {
      dialogRef.current?.focus();
    }
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div
        ref={dialogRef}
        className="bg-[#1a1a1c] rounded-lg shadow-xl w-[400px] p-4"
        tabIndex={-1}
        onKeyDown={(e) => {
          if (e.key === 'Escape') onClose();
        }}
      >
        <h2 className="text-sm font-semibold mb-4">{title}</h2>
        {children}
      </div>
    </div>
  );
}
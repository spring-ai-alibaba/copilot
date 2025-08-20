import { useEffect, useRef, useState } from 'react';

interface InlineEditProps {
  value: string;
  onSubmit: (value: string) => void;
  onCancel: () => void;
}

export function InlineEdit({ value, onSubmit, onCancel }: InlineEditProps) {
  const [editValue, setEditValue] = useState(value);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      onSubmit(editValue);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onCancel();
    }
  };

  const handleBlur = () => {
    onSubmit(editValue);
  };

  return (
    <input
      ref={inputRef}
      type="text"
      value={editValue}
      onChange={(e) => setEditValue(e.target.value)}
      onKeyDown={handleKeyDown}
      onBlur={handleBlur}
      className="bg-[#3c3c3c] text-white px-1 rounded outline-none border border-[#007acc] w-full"
      onClick={(e) => e.stopPropagation()}
    />
  );
}
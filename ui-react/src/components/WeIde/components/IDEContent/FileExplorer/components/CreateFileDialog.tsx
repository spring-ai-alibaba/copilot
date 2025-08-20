import { useState } from 'react';
import { isValidFileName } from '../utils/fileSystem';

interface CreateFileDialogProps {
  path: string;
  onSubmit: (name: string) => void;
  onCancel: () => void;
}

export function CreateFileDialog({ path, onSubmit, onCancel }: CreateFileDialogProps) {
  const [fileName, setFileName] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!isValidFileName(fileName)) {
      setError('Invalid file name. Please avoid special characters.');
      return;
    }

    onSubmit(fileName);
  };

  return (
    <div className="fixed inset-0 bg-black/20 dark:bg-black/50 flex items-center justify-center z-50">
      <form 
        onSubmit={handleSubmit}
        className="bg-white dark:bg-[#1a1a1c] rounded-lg shadow-xl w-[400px] p-4 border border-[#e4e4e4] dark:border-[#454545]"
      >
        <h2 className="text-sm font-semibold mb-4 text-[#444444] dark:text-white">Create New File</h2>
        <div className="mb-4">
          <input
            type="text"
            value={fileName}
            onChange={(e) => {
              setFileName(e.target.value);
              setError('');
            }}
            placeholder="Enter file name"
            className="w-full px-3 py-2 bg-white dark:bg-[#3c3c3c] rounded border border-[#e4e4e4] dark:border-[#454545] text-[#444444] dark:text-white text-sm focus:border-[#0066b8] dark:focus:border-[#007acc] outline-none placeholder-[#767676] dark:placeholder-gray-400"
            autoFocus
          />
          {error && <p className="text-red-600 dark:text-red-500 text-xs mt-1">{error}</p>}
          {path && (
            <p className="text-xs text-[#767676] dark:text-gray-400 mt-1">
              Will be created in: {path}
            </p>
          )}
        </div>
        <div className="flex justify-end space-x-2">
          <button
            type="button"
            onClick={onCancel}
            className="px-3 py-1.5 text-sm text-[#444444] dark:text-white hover:bg-[#e8e8e8] dark:hover:bg-[#2d2d2d] rounded transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={!fileName.trim()}
            className="px-3 py-1.5 text-sm bg-[#0066b8] dark:bg-[#007acc] hover:bg-[#005ba4] dark:hover:bg-[#006bb3] text-white rounded disabled:opacity-50 transition-colors"
          >
            Create
          </button>
        </div>
      </form>
    </div>
  );
}
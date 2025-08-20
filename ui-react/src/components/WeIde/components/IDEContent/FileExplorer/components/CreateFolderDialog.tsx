import { useState } from 'react';
import { isValidFileName } from '../utils/fileSystem';

interface CreateFolderDialogProps {
  path: string;
  onSubmit: (name: string) => void;
  onCancel: () => void;
}

export function CreateFolderDialog({ path, onSubmit, onCancel }: CreateFolderDialogProps) {
  const [folderName, setFolderName] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!isValidFileName(folderName)) {
      setError('Invalid folder name. Please avoid special characters.');
      return;
    }

    onSubmit(folderName);
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <form 
        onSubmit={handleSubmit}
        className="bg-[#1a1a1c] rounded-lg shadow-xl w-[400px] p-4"
      >
        <h2 className="text-sm font-semibold mb-4">Create New Folder</h2>
        <div className="mb-4">
          <input
            type="text"
            value={folderName}
            onChange={(e) => {
              setFolderName(e.target.value);
              setError('');
            }}
            placeholder="Enter folder name"
            className="w-full px-3 py-2 bg-[#3c3c3c] rounded border border-[#454545] text-sm focus:border-[#007acc] outline-none"
            autoFocus
          />
          {error && <p className="text-red-500 text-xs mt-1">{error}</p>}
          {path && (
            <p className="text-xs text-gray-400 mt-1">
              Will be created in: {path}
            </p>
          )}
        </div>
        <div className="flex justify-end space-x-2">
          <button
            type="button"
            onClick={onCancel}
            className="px-3 py-1.5 text-sm hover:bg-[#2d2d2d] rounded"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={!folderName.trim()}
            className="px-3 py-1.5 text-sm bg-[#007acc] hover:bg-[#006bb3] rounded disabled:opacity-50"
          >
            Create
          </button>
        </div>
      </form>
    </div>
  );
}
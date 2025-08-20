import { useState } from 'react';

import { isValidFileName } from '../utils/fileSystem';
import { Dialog } from '../../../Dialog';

interface CreateDialogProps {
  type: 'file' | 'folder';
  isOpen: boolean;
  path: string;
  onSubmit: (name: string) => void;
  onClose: () => void;
}

export function CreateDialog({ type, isOpen, path, onSubmit, onClose }: CreateDialogProps) {
  const [name, setName] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!isValidFileName(name)) {
      setError('Invalid name. Please avoid special characters.');
      return;
    }

    onSubmit(name);
    setName('');
    setError('');
    onClose();
  };

  return (
    <Dialog
      title={`Create New ${type === 'file' ? 'File' : 'Folder'}`}
      isOpen={isOpen}
      onClose={onClose}
    >
      <form onSubmit={handleSubmit}>
        <div className="mb-4">
          <input
            type="text"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setError('');
            }}
            placeholder={`Enter ${type} name`}
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
            onClick={onClose}
            className="px-3 py-1.5 text-sm hover:bg-[#2d2d2d] rounded"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={!name.trim()}
            className="px-3 py-1.5 text-sm bg-[#007acc] hover:bg-[#006bb3] rounded disabled:opacity-50"
          >
            Create
          </button>
        </div>
      </form>
    </Dialog>
  );
}
import React from 'react';

interface MentionOption {
  id: string;
  icon: React.ReactNode;
  label: string;
  path?: string;
  description?: string;
}

interface MentionMenuProps {
  isVisible: boolean;
  options: MentionOption[];
  selectedIndex: number;
  onSelect: (option: MentionOption) => void;
  position: { top: number; left: number };
}


export const MentionMenu: React.FC<MentionMenuProps> = ({
  isVisible ,
  options,
  selectedIndex,
  onSelect,
  position,
}) => {
//   if (!isVisible) return null;

  return (
    <div
      className="w-64 bg-[#1a1a1c] rounded-lg shadow-lg border border-gray-700/50 overflow-hidden"
    >
      <div className="py-1">
        {options.map((option, index) => (
          <button
            key={option.id}
            className={`w-full px-3 py-2 flex items-center gap-3 text-left hover:bg-[#28292b] transition-colors ${
              index === selectedIndex ? 'bg-[#28292b]' : ''
            }`}
            onClick={() => onSelect(option)}
          >
            <div className="flex-shrink-0 w-6 h-6 flex items-center justify-center text-gray-400">
              {option.icon}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-sm text-gray-200">{option.label}</div>
              {option.description && (
                <div className="text-xs text-gray-400">{option.description}</div>
              )}
            </div>
          </button>
        ))}
      </div>
    </div>
  );
};

export type { MentionOption }; 
import React, {KeyboardEvent, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Button} from '@/components/ui/button';

interface UrlInputDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (url: string) => void;

}

export const UrlInputDialog: React.FC<UrlInputDialogProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const { t } = useTranslation();
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');

  const validateUrl = (url: string) => {
    try {
      const urlObject = new URL(url);
      return urlObject.protocol === 'http:' || urlObject.protocol === 'https:';
    } catch {
      return false;
    }
  };

  const handleSubmit = () => {
    if (!url.trim()) {
      setError(t('chat.urlInput.errorEmpty'));
      return;
    }

    if (!validateUrl(url)) {
      setError(t('chat.urlInput.errorInvalid'));
      return;
    }

    onSubmit(url);
    setUrl('');
    setError('');
    onClose();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    } else if (e.key === 'Escape') {
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[10010] overflow-y-auto">
      <div
        className="fixed inset-0 bg-black/35 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />

      {/* Dialog */}
      <div className="flex min-h-full items-center justify-center p-4">
        <div
          className="arc-dialog-panel relative w-full max-w-md transform p-5 text-left transition-all"
          onClick={(e) => e.stopPropagation()}
        >
          <h3 className="mb-1 text-sm font-semibold leading-6 text-foreground">
            {t('chat.urlInput.title')}
          </h3>
          <p className="text-xs leading-5 text-muted-foreground">
            {t('chat.urlInput.description', {
              defaultValue: '添加公开网页作为本轮对话的参考上下文。',
            })}
          </p>

          <div className="mt-4">
            <input
              type="url"
              value={url}
              onChange={(e) => {
                setUrl(e.target.value);
                setError('');
              }}
              onKeyDown={handleKeyDown}
              className="h-10 w-full rounded-xl border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-muted-foreground focus:border-foreground/20"
              placeholder={t('chat.urlInput.placeholder')}
              autoFocus
            />
            {error && (
              <p className="mt-2 text-xs text-destructive">
                {error}
              </p>
            )}
          </div>

          <div className="mt-5 flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button size="sm" onClick={handleSubmit}>
              {t('common.confirm')}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

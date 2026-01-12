import React, {useState} from 'react';
import {Button, Form, Input} from 'antd';
import {ReloadOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';

const {TextArea} = Input;

interface JsonEditorProps {
    value: string;
    onChange: (value: string) => void;
    onError?: (error: string) => void;
    placeholder?: string;
}

export function JsonEditor({value, onChange, onError, placeholder}: JsonEditorProps) {
    const {t} = useTranslation();
    const [error, setError] = useState<string>('');

    const handleFormat = () => {
        if (!value?.trim()) {
            setError('');
            onError?.('');
            return;
        }

        try {
            const parsed = JSON.parse(value);
            const formatted = JSON.stringify(parsed, null, 2);
            onChange(formatted);
            setError('');
            onError?.('');
        } catch (err: any) {
            const errorMsg = err.message || t('settings.mcp.jsonFormatError');
            setError(errorMsg);
            onError?.(errorMsg);
        }
    };

    const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        const newValue = e.target.value;
        onChange(newValue);

        if (newValue.trim()) {
            try {
                JSON.parse(newValue);
                setError('');
                onError?.('');
            } catch (err: any) {
                const errorMsg = err.message || t('settings.mcp.jsonFormatError');
                setError(errorMsg);
                onError?.(errorMsg);
            }
        } else {
            setError('');
            onError?.('');
        }
    };

    return (
        <div className="space-y-2">
            <div className="flex justify-end">
                <Button
                    size="small"
                    onClick={handleFormat}
                    icon={<ReloadOutlined/>}
                >
                    {t('common.format')}
                </Button>
            </div>
            <TextArea
                value={value}
                onChange={handleChange}
                placeholder={placeholder || '请输入JSON配置，例如：\n{\n  "key": "value"\n}'}
                rows={15}
                className="font-mono"
            />
            {error && (
                <div className="text-sm text-red-500">
                    {error}
                </div>
            )}
        </div>
    );
}

interface JsonEditorWrapperProps {
    form: any;
    fieldName: string;
    onError?: (error: string) => void;
    placeholder?: string;
}

export function JsonEditorWrapper({form, fieldName, onError, placeholder}: JsonEditorWrapperProps) {
    const value = Form.useWatch(fieldName, form) || '';

    return (
        <JsonEditor
            value={value}
            onChange={(newValue) => {
                form.setFieldsValue({[fieldName]: newValue});
            }}
            onError={onError}
            placeholder={placeholder}
        />
    );
}


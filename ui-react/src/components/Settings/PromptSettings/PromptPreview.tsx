import React, {useState} from 'react';
import {Button, Card, Divider, Form, Input, InputNumber, Modal, Select, Space, Tag} from 'antd';
import {CopyOutlined, FileTextOutlined, PlayCircleOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {PromptTemplate} from '@/api/prompts';

const { TextArea } = Input;
const { Option } = Select;

interface PromptPreviewProps {
  visible: boolean;
  prompt: PromptTemplate;
  onCancel: () => void;
}

export function PromptPreview({ visible, prompt, onCancel }: PromptPreviewProps) {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [renderedContent, setRenderedContent] = useState('');

  // 渲染提示词内容
  const renderPrompt = () => {
    try {
      const values = form.getFieldsValue();
      let content = prompt.content;

      // 替换变量
      prompt.variables.forEach(variable => {
        const value = values[variable.name] || variable.defaultValue || '';
        const regex = new RegExp(`{{${variable.name}}}`, 'g');
        content = content.replace(regex, value);
      });

      setRenderedContent(content);
    } catch (error) {
      console.error('Failed to render prompt:', error);
    }
  };

  // 复制内容
  const handleCopy = async (content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      // message.success(t('prompts.success.copied'));
    } catch (error) {
      console.error('Failed to copy:', error);
    }
  };

  // 渲染变量输入组件
  const renderVariableInput = (variable: any) => {
    const commonProps = {
      placeholder: variable.description || variable.label,
      onChange: renderPrompt
    };

    switch (variable.type) {
      case 'textarea':
        return <TextArea rows={3} {...commonProps} />;
      case 'number':
        return <InputNumber style={{ width: '100%' }} {...commonProps} />;
      case 'select':
        return (
          <Select {...commonProps}>
            {variable.options?.map((option: string) => (
              <Option key={option} value={option}>{option}</Option>
            ))}
          </Select>
        );
      default:
        return <Input {...commonProps} />;
    }
  };

  // 初始化表单默认值
  React.useEffect(() => {
    if (visible && prompt) {
      const defaultValues: any = {};
      prompt.variables.forEach(variable => {
        if (variable.defaultValue) {
          defaultValues[variable.name] = variable.defaultValue;
        }
      });
      form.setFieldsValue(defaultValues);
      renderPrompt();
    }
  }, [visible, prompt, form]);

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <FileTextOutlined />
          {t('prompts.preview')} - {prompt.title}
        </div>
      }
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={900}
      style={{ top: 20 }}
      bodyStyle={{ maxHeight: '70vh', overflow: 'auto' }}
    >
      <div className="grid grid-cols-2 gap-6">
        {/* 左侧：变量输入 */}
        <div>
          <Card 
            title={t('prompts.preview.variables')} 
            size="small"
            extra={
              <Button 
                type="primary" 
                icon={<PlayCircleOutlined />}
                onClick={renderPrompt}
                size="small"
              >
                {t('prompts.preview.render')}
              </Button>
            }
          >
            {prompt.variables.length > 0 ? (
              <Form
                form={form}
                layout="vertical"
                size="small"
              >
                {prompt.variables.map(variable => (
                  <Form.Item
                    key={variable.name}
                    name={variable.name}
                    label={
                      <div className="flex items-center gap-2">
                        <span>{variable.label}</span>
                        {variable.required && <span className="text-red-500">*</span>}
                      </div>
                    }
                    rules={variable.required ? [{ required: true, message: `${variable.label} is required` }] : []}
                  >
                    {renderVariableInput(variable)}
                  </Form.Item>
                ))}
              </Form>
            ) : (
              <div className="text-center text-gray-500 py-4">
                {t('prompts.preview.no_variables')}
              </div>
            )}
          </Card>

          {/* 提示词信息 */}
          <Card title={t('prompts.preview.info')} size="small" className="mt-4">
            <div className="space-y-3">
              <div>
                <span className="font-medium">{t('prompts.form.category')}: </span>
                <Tag color="blue">{prompt.category}</Tag>
              </div>
              
              {prompt.description && (
                <div>
                  <span className="font-medium">{t('prompts.form.description')}: </span>
                  <span className="text-gray-600">{prompt.description}</span>
                </div>
              )}

              {prompt.tags.length > 0 && (
                <div>
                  <span className="font-medium">{t('prompts.form.tags')}: </span>
                  <Space wrap>
                    {prompt.tags.map(tag => (
                      <Tag key={tag} size="small">{tag}</Tag>
                    ))}
                  </Space>
                </div>
              )}

              <div>
                <span className="font-medium">{t('prompts.usage_count')}: </span>
                <span>{prompt.usageCount}</span>
              </div>

              <div>
                <span className="font-medium">{t('prompts.created_by')}: </span>
                <span>{prompt.createdBy}</span>
              </div>

              <div>
                <span className="font-medium">{t('prompts.updated_at')}: </span>
                <span>{new Date(prompt.updatedAt).toLocaleString()}</span>
              </div>
            </div>
          </Card>
        </div>

        {/* 右侧：渲染结果 */}
        <div>
          <Card 
            title={t('prompts.preview.result')}
            size="small"
            extra={
              <Button 
                type="text" 
                icon={<CopyOutlined />}
                onClick={() => handleCopy(renderedContent)}
                size="small"
              >
                {t('prompts.copy')}
              </Button>
            }
          >
            <div className="rendered-content">
              <TextArea
                value={renderedContent}
                readOnly
                rows={15}
                style={{ 
                  fontFamily: 'monospace',
                  fontSize: '13px',
                  lineHeight: '1.5',
                  resize: 'none'
                }}
              />
            </div>
          </Card>

          {/* 原始模板 */}
          <Card title={t('prompts.preview.template')} size="small" className="mt-4">
            <TextArea
              value={prompt.content}
              readOnly
              rows={8}
              style={{ 
                fontFamily: 'monospace',
                fontSize: '12px',
                lineHeight: '1.4',
                resize: 'none',
                backgroundColor: '#f8f9fa'
              }}
            />
          </Card>
        </div>
      </div>

      <Divider />

      <div className="flex justify-end">
        <Button onClick={onCancel}>
          {t('common.close')}
        </Button>
      </div>
    </Modal>
  );
}

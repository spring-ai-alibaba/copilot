import React, { useState, useEffect } from 'react';
import { Modal, Form, Input, Select, Switch, Button, Space, Card, Divider } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { PromptTemplate, PromptCategory, PromptCreateRequest, PromptVariable } from '@/api/prompts';

const { TextArea } = Input;
const { Option } = Select;

interface PromptEditorProps {
  visible: boolean;
  prompt?: PromptTemplate | null;
  categories: PromptCategory[];
  onSave: (data: PromptCreateRequest) => void;
  onCancel: () => void;
}

export function PromptEditor({ visible, prompt, categories, onSave, onCancel }: PromptEditorProps) {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [variables, setVariables] = useState<PromptVariable[]>([]);

  // 添加变量
  const addVariable = () => {
    const newVariable: PromptVariable = {
      name: '',
      type: 'text',
      label: '',
      description: '',
      required: true,
      defaultValue: ''
    };
    setVariables([...variables, newVariable]);
  };

  // 删除变量
  const removeVariable = (index: number) => {
    const newVariables = variables.filter((_, i) => i !== index);
    setVariables(newVariables);
  };

  // 更新变量
  const updateVariable = (index: number, field: keyof PromptVariable, value: any) => {
    const newVariables = [...variables];
    newVariables[index] = { ...newVariables[index], [field]: value };
    setVariables(newVariables);
  };

  // 处理保存
  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      const data: PromptCreateRequest = {
        name: values.name,
        title: values.title,
        description: values.description,
        content: values.content,
        category: values.category,
        tags: values.tags || [],
        variables: variables.filter(v => v.name && v.label), // 过滤掉空的变量
        isPublic: values.isPublic || false
      };
      onSave(data);
    } catch (error) {
      console.error('Validation failed:', error);
    }
  };

  // 处理取消
  const handleCancel = () => {
    form.resetFields();
    setVariables([]);
    onCancel();
  };

  // 初始化表单数据
  useEffect(() => {
    if (visible) {
      if (prompt) {
        form.setFieldsValue({
          name: prompt.name,
          title: prompt.title,
          description: prompt.description,
          content: prompt.content,
          category: prompt.category,
          tags: prompt.tags,
          isPublic: prompt.isPublic
        });
        setVariables(prompt.variables || []);
      } else {
        form.resetFields();
        setVariables([]);
      }
    }
  }, [visible, prompt, form]);

  return (
    <Modal
      title={prompt ? t('prompts.edit_prompt') : t('prompts.create_prompt')}
      open={visible}
      onCancel={handleCancel}
      footer={null}
      width={800}
      style={{ top: 20 }}
      bodyStyle={{ maxHeight: '70vh', overflow: 'auto' }}
    >
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
      >
        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            name="name"
            label={t('prompts.form.name')}
            rules={[
              { required: true, message: t('prompts.form.name_required') },
              { pattern: /^[a-zA-Z0-9_-]+$/, message: t('prompts.form.name_pattern') }
            ]}
          >
            <Input placeholder={t('prompts.form.name_placeholder')} />
          </Form.Item>

          <Form.Item
            name="title"
            label={t('prompts.form.title')}
            rules={[{ required: true, message: t('prompts.form.title_required') }]}
          >
            <Input placeholder={t('prompts.form.title_placeholder')} />
          </Form.Item>
        </div>

        <Form.Item
          name="description"
          label={t('prompts.form.description')}
        >
          <TextArea 
            rows={2}
            placeholder={t('prompts.form.description_placeholder')}
          />
        </Form.Item>

        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            name="category"
            label={t('prompts.form.category')}
            rules={[{ required: true, message: t('prompts.form.category_required') }]}
          >
            <Select placeholder={t('prompts.form.category_placeholder')}>
              {categories.map(category => (
                <Option key={category.name} value={category.name}>
                  {category.title}
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="tags"
            label={t('prompts.form.tags')}
          >
            <Select
              mode="tags"
              placeholder={t('prompts.form.tags_placeholder')}
              tokenSeparators={[',']}
            />
          </Form.Item>
        </div>

        <Form.Item
          name="content"
          label={t('prompts.form.content')}
          rules={[{ required: true, message: t('prompts.form.content_required') }]}
        >
          <TextArea 
            rows={8}
            placeholder={t('prompts.form.content_placeholder')}
            className="font-mono"
          />
        </Form.Item>

        {/* 变量配置 */}
        <div className="variables-section">
          <div className="flex justify-between items-center mb-4">
            <h4 className="font-medium">{t('prompts.form.variables')}</h4>
            <Button 
              type="dashed" 
              icon={<PlusOutlined />}
              onClick={addVariable}
            >
              {t('prompts.form.add_variable')}
            </Button>
          </div>

          {variables.map((variable, index) => (
            <Card key={index} size="small" className="mb-3">
              <div className="grid grid-cols-2 gap-3 mb-3">
                <Input
                  placeholder={t('prompts.form.variable_name')}
                  value={variable.name}
                  onChange={(e) => updateVariable(index, 'name', e.target.value)}
                />
                <Input
                  placeholder={t('prompts.form.variable_label')}
                  value={variable.label}
                  onChange={(e) => updateVariable(index, 'label', e.target.value)}
                />
              </div>

              <div className="grid grid-cols-3 gap-3 mb-3">
                <Select
                  value={variable.type}
                  onChange={(value) => updateVariable(index, 'type', value)}
                >
                  <Option value="text">{t('prompts.variable_types.text')}</Option>
                  <Option value="textarea">{t('prompts.variable_types.textarea')}</Option>
                  <Option value="number">{t('prompts.variable_types.number')}</Option>
                  <Option value="select">{t('prompts.variable_types.select')}</Option>
                </Select>

                <Input
                  placeholder={t('prompts.form.default_value')}
                  value={variable.defaultValue}
                  onChange={(e) => updateVariable(index, 'defaultValue', e.target.value)}
                />

                <div className="flex items-center justify-between">
                  <Switch
                    checked={variable.required}
                    onChange={(checked) => updateVariable(index, 'required', checked)}
                    size="small"
                  />
                  <span className="text-sm">{t('prompts.form.required')}</span>
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => removeVariable(index)}
                    size="small"
                  />
                </div>
              </div>

              <TextArea
                placeholder={t('prompts.form.variable_description')}
                value={variable.description}
                onChange={(e) => updateVariable(index, 'description', e.target.value)}
                rows={1}
                size="small"
              />

              {variable.type === 'select' && (
                <div className="mt-3">
                  <Input
                    placeholder={t('prompts.form.select_options')}
                    value={variable.options?.join(', ')}
                    onChange={(e) => updateVariable(index, 'options', e.target.value.split(',').map(s => s.trim()).filter(Boolean))}
                  />
                </div>
              )}
            </Card>
          ))}
        </div>

        <Form.Item
          name="isPublic"
          valuePropName="checked"
        >
          <Switch />
          <span className="ml-2">{t('prompts.form.is_public')}</span>
        </Form.Item>

        <Divider />

        <div className="flex justify-end gap-3">
          <Button onClick={handleCancel}>
            {t('common.cancel')}
          </Button>
          <Button type="primary" onClick={handleSave}>
            {prompt ? t('common.update') : t('common.create')}
          </Button>
        </div>
      </Form>
    </Modal>
  );
}

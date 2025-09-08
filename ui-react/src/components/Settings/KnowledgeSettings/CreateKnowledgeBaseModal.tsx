import React from 'react';
import { Modal, Form, Input, Button } from 'antd';
import { useTranslation } from 'react-i18next';

interface CreateKnowledgeBaseModalProps {
  visible: boolean;
  onCancel: () => void;
  onOk: (data: { key: string; name: string; description?: string }) => void;
}

export function CreateKnowledgeBaseModal({ visible, onCancel, onOk }: CreateKnowledgeBaseModalProps) {
  const { t } = useTranslation();
  const [form] = Form.useForm();

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      onOk(values);
      form.resetFields();
    } catch (error) {
      console.error('Validation failed:', error);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  return (
    <Modal
      title={t('knowledge.create_kb')}
      open={visible}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          {t('common.cancel')}
        </Button>,
        <Button key="ok" type="primary" onClick={handleOk}>
          {t('common.create')}
        </Button>,
      ]}
      width={500}
    >
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        style={{ padding: '8px 0' }}
      >
        <Form.Item
          name="key"
          label={t('knowledge.kb_key')}
          rules={[
            { required: true, message: t('knowledge.kb_key_required') },
            {
              pattern: /^[a-zA-Z0-9_-]+$/,
              message: t('knowledge.kb_key_pattern')
            },
            {
              min: 2,
              max: 50,
              message: t('knowledge.kb_key_length')
            }
          ]}
          style={{ marginBottom: '24px' }}
        >
          <Input
            placeholder={t('knowledge.kb_key_placeholder')}
            maxLength={50}
            size="large"
          />
        </Form.Item>

        <Form.Item
          name="name"
          label={t('knowledge.kb_name')}
          rules={[
            { required: true, message: t('knowledge.kb_name_required') },
            {
              min: 2,
              max: 100,
              message: t('knowledge.kb_name_length')
            }
          ]}
          style={{ marginBottom: '24px' }}
        >
          <Input
            placeholder={t('knowledge.kb_name_placeholder')}
            maxLength={100}
            size="large"
          />
        </Form.Item>

        <Form.Item
          name="description"
          label={t('knowledge.kb_description')}
          rules={[
            {
              max: 500,
              message: t('knowledge.kb_description_length')
            }
          ]}
          style={{ marginBottom: '8px' }}
        >
          <Input.TextArea
            placeholder={t('knowledge.kb_description_placeholder')}
            rows={4}
            maxLength={500}
            showCount
            size="large"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

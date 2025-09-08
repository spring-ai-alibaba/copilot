import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { 
  Button, 
  Table, 
  Modal, 
  Form, 
  Input, 
  Select, 
  Switch, 
  message, 
  Popconfirm, 
  Tag, 
  Tooltip,
  Space,
  Card,
  Divider
} from 'antd';
import { 
  PlusOutlined, 
  EditOutlined, 
  DeleteOutlined, 
  ApiOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExperimentOutlined
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { 
  AIModel, 
  ModelCreateRequest, 
  ModelUpdateRequest,
  getModels, 
  createModel, 
  updateModel, 
  deleteModel,
  testModel
} from '@/api/models';
import './ModelSettings.css';

const { Option } = Select;
const { TextArea } = Input;

export default function ModelSettings() {
  const { t } = useTranslation();
  const [models, setModels] = useState<AIModel[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingModel, setEditingModel] = useState<AIModel | null>(null);
  const [testingModel, setTestingModel] = useState<string | null>(null);
  const [form] = Form.useForm();

  // 加载模型列表
  const loadModels = async () => {
    setLoading(true);
    try {
      const data = await getModels();
      setModels(data);
    } catch (error) {
      message.error(t('models.errors.load_failed'));
      console.error('Failed to load models:', error);
    } finally {
      setLoading(false);
    }
  };

  // 创建或更新模型
  const handleSave = async (values: any) => {
    try {
      const data: ModelCreateRequest = {
        name: values.name,
        title: values.title,
        type: values.type,
        platform: values.platform,
        setting: values.setting,
        remark: values.remark,
        isFree: values.isFree || false
      };

      if (editingModel) {
        await updateModel(editingModel.id, data);
        message.success(t('models.success.updated'));
      } else {
        await createModel(data);
        message.success(t('models.success.created'));
      }

      setModalVisible(false);
      setEditingModel(null);
      form.resetFields();
      loadModels();
    } catch (error) {
      message.error(editingModel ? t('models.errors.update_failed') : t('models.errors.create_failed'));
      console.error('Failed to save model:', error);
    }
  };

  // 删除模型
  const handleDelete = async (id: string) => {
    try {
      await deleteModel(id);
      message.success(t('models.success.deleted'));
      loadModels();
    } catch (error) {
      message.error(t('models.errors.delete_failed'));
      console.error('Failed to delete model:', error);
    }
  };

  // 切换模型启用状态
  const handleToggleEnabled = async (model: AIModel) => {
    try {
      await updateModel(model.id, { isEnabled: !model.isEnabled });
      message.success(t('models.success.status_updated'));
      loadModels();
    } catch (error) {
      message.error(t('models.errors.status_update_failed'));
      console.error('Failed to toggle model status:', error);
    }
  };

  // 测试模型连接
  const handleTestModel = async (id: string) => {
    setTestingModel(id);
    try {
      const result = await testModel(id);
      if (result.success) {
        message.success(result.message);
      } else {
        message.error(result.message);
      }
    } catch (error) {
      message.error(t('models.errors.test_failed'));
      console.error('Failed to test model:', error);
    } finally {
      setTestingModel(null);
    }
  };

  // 打开编辑模态框
  const handleEdit = (model: AIModel) => {
    setEditingModel(model);
    form.setFieldsValue({
      name: model.name,
      title: model.title,
      type: model.type,
      platform: model.platform,
      setting: model.setting,
      remark: model.remark,
      isFree: model.isFree
    });
    setModalVisible(true);
  };

  // 打开创建模态框
  const handleCreate = () => {
    setEditingModel(null);
    form.resetFields();
    setModalVisible(true);
  };

  // 获取模型类型标签
  const getTypeTag = (type: AIModel['type']) => {
    const typeConfig = {
      text: { color: 'blue', text: t('models.types.text') },
      image: { color: 'green', text: t('models.types.image') },
      embedding: { color: 'orange', text: t('models.types.embedding') },
      rerank: { color: 'purple', text: t('models.types.rerank') }
    };
    const config = typeConfig[type];
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  // 表格列定义
  const columns: ColumnsType<AIModel> = [
    {
      title: t('models.table.name'),
      dataIndex: 'title',
      key: 'title',
      render: (title, record) => (
        <div>
          <div className="font-medium">{title}</div>
          <div className="text-sm text-gray-500">{record.name}</div>
        </div>
      )
    },
    {
      title: t('models.table.type'),
      dataIndex: 'type',
      key: 'type',
      render: (type) => getTypeTag(type)
    },
    {
      title: t('models.table.platform'),
      dataIndex: 'platform',
      key: 'platform'
    },
    {
      title: t('models.table.status'),
      dataIndex: 'isEnabled',
      key: 'isEnabled',
      render: (isEnabled, record) => (
        <Switch
          checked={isEnabled}
          onChange={() => handleToggleEnabled(record)}
          checkedChildren={<CheckCircleOutlined />}
          unCheckedChildren={<CloseCircleOutlined />}
        />
      )
    },
    {
      title: t('models.table.free'),
      dataIndex: 'isFree',
      key: 'isFree',
      render: (isFree) => (
        <Tag color={isFree ? 'green' : 'orange'}>
          {isFree ? t('models.free') : t('models.paid')}
        </Tag>
      )
    },
    {
      title: t('models.table.actions'),
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Tooltip title={t('models.test_connection')}>
            <Button
              type="text"
              icon={<ExperimentOutlined />}
              loading={testingModel === record.id}
              onClick={() => handleTestModel(record.id)}
            />
          </Tooltip>
          <Tooltip title={t('common.edit')}>
            <Button
              type="text"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          {!record.isEnabled && (
            <Popconfirm
              title={t('models.confirm_delete')}
              onConfirm={() => handleDelete(record.id)}
            >
              <Tooltip title={t('common.delete')}>
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                />
              </Tooltip>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ];

  useEffect(() => {
    loadModels();
  }, []);

  return (
    <div className="model-settings">
      <Card>
        <div className="flex justify-between items-center mb-6">
          <div>
            <h3 className="text-lg font-semibold flex items-center gap-3">
              <ApiOutlined />
              {t('models.title')}
            </h3>
            <p className="text-sm text-gray-500 mt-1">
              {t('models.description')}
            </p>
          </div>
          <Button 
            type="primary" 
            icon={<PlusOutlined />}
            onClick={handleCreate}
          >
            {t('models.add_model')}
          </Button>
        </div>

        <Table
          columns={columns}
          dataSource={models}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => t('models.total_count', { count: total })
          }}
        />
      </Card>

      {/* 创建/编辑模型模态框 */}
      <Modal
        title={editingModel ? t('models.edit_model') : t('models.add_model')}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          setEditingModel(null);
          form.resetFields();
        }}
        footer={null}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSave}
          requiredMark={false}
        >
          <Form.Item
            name="name"
            label={t('models.form.name')}
            rules={[{ required: true, message: t('models.form.name_required') }]}
          >
            <Input placeholder={t('models.form.name_placeholder')} />
          </Form.Item>

          <Form.Item
            name="title"
            label={t('models.form.title')}
            rules={[{ required: true, message: t('models.form.title_required') }]}
          >
            <Input placeholder={t('models.form.title_placeholder')} />
          </Form.Item>

          <div className="grid grid-cols-2 gap-4">
            <Form.Item
              name="type"
              label={t('models.form.type')}
              rules={[{ required: true, message: t('models.form.type_required') }]}
            >
              <Select placeholder={t('models.form.type_placeholder')}>
                <Option value="text">{t('models.types.text')}</Option>
                <Option value="image">{t('models.types.image')}</Option>
                <Option value="embedding">{t('models.types.embedding')}</Option>
                <Option value="rerank">{t('models.types.rerank')}</Option>
              </Select>
            </Form.Item>

            <Form.Item
              name="platform"
              label={t('models.form.platform')}
              rules={[{ required: true, message: t('models.form.platform_required') }]}
            >
              <Input placeholder={t('models.form.platform_placeholder')} />
            </Form.Item>
          </div>

          <Form.Item
            name="setting"
            label={t('models.form.setting')}
            rules={[{ required: true, message: t('models.form.setting_required') }]}
          >
            <TextArea 
              rows={4}
              placeholder={t('models.form.setting_placeholder')}
            />
          </Form.Item>

          <Form.Item
            name="remark"
            label={t('models.form.remark')}
          >
            <TextArea 
              rows={2}
              placeholder={t('models.form.remark_placeholder')}
            />
          </Form.Item>

          <Form.Item
            name="isFree"
            valuePropName="checked"
          >
            <Switch checkedChildren={t('models.free')} unCheckedChildren={t('models.paid')} />
            <span className="ml-2">{t('models.form.is_free')}</span>
          </Form.Item>

          <Divider />

          <div className="flex justify-end gap-3">
            <Button onClick={() => setModalVisible(false)}>
              {t('common.cancel')}
            </Button>
            <Button type="primary" htmlType="submit">
              {editingModel ? t('common.update') : t('common.create')}
            </Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
}

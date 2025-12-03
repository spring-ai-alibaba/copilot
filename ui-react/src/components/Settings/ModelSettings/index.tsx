import React, {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
    Button,
    Divider,
    Form,
    Input,
    InputNumber,
    message,
    Modal,
    Popconfirm,
    Select,
    Switch,
    Tag,
    Tooltip,
    Empty,
    Spin
} from 'antd';
import {
    CheckCircleOutlined,
    CloseCircleOutlined,
    DeleteOutlined,
    EditOutlined,
    ExperimentOutlined,
    PlusOutlined,
    DownOutlined,
    UpOutlined
} from '@ant-design/icons';
import {AIModel, Provider, createModel, deleteModel, getModels, getProviders, getModelsByProvider, ModelCreateRequest, testModel, updateModel, checkProviderHealth, DiscoveredModel, LlmServiceProvider, LlmModel, getMyLlms, toggleModelStatus, deleteModelProvider, checkModelHealth, updateModelConfig, checkOpenAiCompatibleHealth} from '@/api/models';
import {eventEmitter} from '@/components/AiChat/utils/EventEmitter';
import './ModelSettings.css';


const { Option } = Select;
const { TextArea } = Input;

export default function ModelSettings() {
  const { t } = useTranslation();
  const [providers, setProviders] = useState<Provider[]>([]);
  const [models, setModels] = useState<LlmModel[]>([]);
  const [llmProviders, setLlmProviders] = useState<LlmServiceProvider[]>([]);
  const [expandedProviders, setExpandedProviders] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingModel, setEditingModel] = useState<AIModel | null>(null);
  const [testingModel, setTestingModel] = useState<string | null>(null);
  const [testingProvider, setTestingProvider] = useState<string | null>(null);
  const [selectedProvider, setSelectedProvider] = useState<Provider | null>(null);
  const [selectedModel, setSelectedModel] = useState<DiscoveredModel | null>(null);
  const [apiKey, setApiKey] = useState<string>('');
  const [baseUrl, setBaseUrl] = useState<string>('');
  const [modelName, setModelName] = useState<string>('');
  const [apiKeyModalVisible, setApiKeyModalVisible] = useState(false);
  const [addModelModalVisible, setAddModelModalVisible] = useState(false);
  const [addModelProviderCode, setAddModelProviderCode] = useState<string>('');
  const [addModelName, setAddModelName] = useState<string>('');
  const [checkingModelHealth, setCheckingModelHealth] = useState(false);
  const [editModelModalVisible, setEditModelModalVisible] = useState(false);
  const [editingLlmModel, setEditingLlmModel] = useState<LlmModel | null>(null);
  const [editingMaxToken, setEditingMaxToken] = useState<number>(0);
  const [updatingModelConfig, setUpdatingModelConfig] = useState(false);
  const [form] = Form.useForm();
  const [addModelForm] = Form.useForm();
  const [editModelForm] = Form.useForm();

  // 加载供应商列表
  const loadProviders = async () => {
    try {
      const data = await getProviders();
      setProviders(data);
      // 不在这里请求 /my_llms，只设置默认选中供应商
      if (data.length > 0 && !selectedProvider) {
        setSelectedProvider(data[0]);
      }
    } catch (error) {
      message.error(t('models.errors.load_failed'));
      console.error('Failed to load providers:', error);
    }
  };

  // 进入模型管理时只请求一次 /my_llms，缓存所有供应商下的模型
  const loadMyLlmsData = async () => {
    setLoading(true);
    try {
      const data = await getMyLlms();
      const list: LlmServiceProvider[] = Array.isArray(data) ? data : [];
      setLlmProviders(list);
    } catch (error) {
      message.error(t('models.errors.load_failed'));
      console.error('Failed to load models from /my_llms:', error);
    } finally {
      setLoading(false);
    }
  };

  // 根据已缓存的 /my_llms 数据和当前供应商，刷新右侧“已添加的模型”
  const loadModelsByProvider = (provider: Provider) => {
    if (!provider || llmProviders.length === 0) {
      setModels([]);
      setSelectedModel(null);
      return;
    }

    // 假设后端的 providerId 与前端的 providerCode 或名称对应
    const providerCode = provider.providerCode || provider.name;
    const current = llmProviders.find(item => item.providerId === providerCode);

    setModels(current?.models || []);
    setSelectedModel(null);
  };

  // 处理供应商选择
  const handleProviderSelect = (provider: Provider) => {
    setSelectedProvider(provider); 
    loadModelsByProvider(provider);
  };

  // 创建或更新模型
  const handleSave = async (values: any) => {
    try {
      if (!selectedProvider) {
        message.error('请先选择供应商');
        return;
      }

      const data: ModelCreateRequest = {
        name: values.name,
        title: values.title,
        type: values.type,
        providerId: selectedProvider.id.toString(),
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
      if (selectedProvider) {
        loadModelsByProvider(selectedProvider);
      }
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
      if (selectedProvider) {
        loadModelsByProvider(selectedProvider);
      }
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
      if (selectedProvider) {
        loadModelsByProvider(selectedProvider);
      }
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

  // 检测供应商健康状态
  const handleCheckProviderHealth = async (id: string) => {
    if (!selectedProvider) {
      message.error('请先选择供应商');
      return;
    }

    if (!apiKey) {
      message.error('请先输入 API 密钥');
      return;
    }

    if (!selectedProvider.providerCode) {
      message.error('当前供应商缺少 providerCode 配置');
      return;
    }

    // 检查是否为 OpenAiCompatible 供应商
    const isOpenAiCompatible = selectedProvider.providerCode === 'OpenAiCompatible';
    
    if (isOpenAiCompatible) {
      if (!baseUrl) {
        message.error('请输入 Base URL');
        return;
      }
      if (!modelName) {
        message.error('请输入模型名称');
        return;
      }
    }

    setTestingProvider(id);
    try {
      let result;
      if (isOpenAiCompatible) {
        result = await checkOpenAiCompatibleHealth({
          apiUrl: baseUrl,
          apiKey,
          testModelName: modelName,
        });
      } else {
        result = await checkProviderHealth(selectedProvider.providerCode, apiKey);
      }
      
      if (result.success) {
        message.success(result.message || '供应商健康状态正常');
        // 刷新供应商列表以更新状态
        loadProviders();
        // 刷新已添加的模型列表
        await loadMyLlmsData();
        // 关闭模态框并清空表单
        setApiKeyModalVisible(false);
        setApiKey('');
        setBaseUrl('');
        setModelName('');
      } else {
        message.error(result.message || '供应商健康状态异常');
        // 刷新供应商列表以更新状态
        loadProviders();
      }
    } catch (error: any) {
      message.error(error.message || t('models.errors.test_failed'));
      console.error('Failed to check provider health:', error);
    } finally {
      setTestingProvider(null);
    }
  };

  // 打开编辑模态框
  const handleEdit = (model: AIModel) => {
    setEditingModel(model);
    form.setFieldsValue({
      name: model.name,
      title: model.title,
      type: model.type,
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

  // 渲染侧边栏供应商列表项
  const renderSidebarProvider = (provider: Provider) => (
    <div
      key={provider.id}
      className={`sidebar-model-item ${selectedProvider?.id === provider.id ? 'active' : ''}`}
      onClick={() => handleProviderSelect(provider)}
    >
      <div className="sidebar-item-header">
        <div className="sidebar-item-title">{provider.name}</div>
        <div className="sidebar-item-actions">
          <Button
            type="link"
            size="small"
            className="sidebar-add-btn"
            icon={<PlusOutlined />}
            onClick={(e) => {
              e.stopPropagation();
              setSelectedProvider(provider);
              setApiKeyModalVisible(true);
            }}
          >
            添加
          </Button>
          {provider.status === 1 && <div className="status-indicator"></div>}
        </div>
      </div>
      {provider.tags && <div className="sidebar-item-subtitle">{provider.tags}</div>}
    </div>
  );

  // 切换某个供应商的展开/收起
  const toggleProviderExpanded = (providerId: string) => {
    setExpandedProviders((prev) =>
      prev.includes(providerId)
        ? prev.filter(id => id !== providerId)
        : [...prev, providerId]
    );
  };

  // 删除已添加的供应商
  const handleDeleteProvider = async (providerCode: string) => {
    try {
      await deleteModelProvider(providerCode);
      message.success('删除成功');
      // 刷新已添加的模型列表
      await loadMyLlmsData();
      await loadProviders();
    } catch (error) {
      message.error('删除失败');
      console.error('Failed to delete provider:', error);
    }
  };

  // 打开编辑模型配置弹窗
  const handleOpenEditModelModal = (model: LlmModel) => {
    setEditingLlmModel(model);
    setEditingMaxToken(model.maxTokens);
    editModelForm.setFieldsValue({
      maxToken: model.maxTokens
    });
    setEditModelModalVisible(true);
  };

  // 保存编辑的模型配置
  const handleSaveModelConfig = async () => {
    if (!editingLlmModel) return;

    try {
      await editModelForm.validateFields();
      const maxToken = editModelForm.getFieldValue('maxToken');
      
      if (maxToken === editingLlmModel.maxTokens) {
        message.info('配置未发生变化');
        return;
      }

      setUpdatingModelConfig(true);
      await updateModelConfig({
        id: editingLlmModel.id,
        maxToken: maxToken
      });

      message.success('模型配置更新成功');
      
      // 更新本地状态
      setLlmProviders(prev => prev.map(p => ({
        ...p,
        models: (p.models || []).map(m =>
          m.id === editingLlmModel.id
            ? { ...m, maxTokens: maxToken }
            : m
        ),
      })));

      setEditModelModalVisible(false);
      setEditingLlmModel(null);
      editModelForm.resetFields();
    } catch (error: any) {
      message.error(error.message || '更新模型配置失败');
      console.error('Failed to update model config:', error);
    } finally {
      setUpdatingModelConfig(false);
    }
  };

  // 切换某个模型的启用/停用状态：先调后端，再更新本地状态
  // /my_llms 中的 status: '1' 表示启用，'0' 表示停用
  const toggleLlmModelStatus = async (providerId: string, modelId: string) => {
    const targetProvider = llmProviders.find(p => p.providerId === providerId);
    const targetModel = targetProvider?.models?.find(m => m.id === modelId);
    if (!targetModel) return;

    const currentEnabled = targetModel.status === '1';
    const nextEnabled = !currentEnabled;

    try {
      const ok = await toggleModelStatus(modelId, nextEnabled);
      if (!ok) {
        message.error(t('models.errors.status_update_failed'));
        return;
      }

      setLlmProviders(prev => prev.map(p => {
        if (p.providerId !== providerId) return p;
        return {
          ...p,
          models: (p.models || []).map(m =>
            m.id === modelId
              ? { ...m, status: nextEnabled ? '1' : '0' }
              : m
          ),
        };
      }));

      message.success(t('models.success.status_updated'));
      
      // 发送事件通知模型状态已更改，触发聊天组件重新获取模型列表
      eventEmitter.emit('model:status-changed');
    } catch (error) {
      console.error('Failed to toggle llm model status:', error);
      message.error(t('models.errors.status_update_failed'));
    }
  };

  // 渲染右侧每个供应商卡片及其模型列表
  const renderProviderCard = (providerItem: LlmServiceProvider) => {
    const isExpanded = expandedProviders.includes(providerItem.providerId);

    // 从左侧 providers 中找到匹配的供应商信息，用于显示名称等
    const matchedProvider = providers.find(p => {
      const code = p.providerCode || p.name;
      return code === providerItem.providerId;
    });

    const displayName = matchedProvider?.name || providerItem.providerId;

    return (
      <div key={providerItem.providerId} className="provider-card">
        <div className="provider-card-header">
          <div className="provider-card-info">
            <div className="provider-card-logo-placeholder" />
            <span className="provider-card-name-text">{displayName}</span>
          </div>

          <div className="provider-card-actions">
            <Button
              type="text"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => {
                setAddModelProviderCode(providerItem.providerId);
                setAddModelName('');
                addModelForm.resetFields();
                setAddModelModalVisible(true);
              }}
            >
              新增模型
            </Button>

            <Button
              type="text"
              size="small"
              onClick={() => toggleProviderExpanded(providerItem.providerId)}
            >
              <span className="provider-card-toggle-text">展示更多模型</span>
              {isExpanded ? <UpOutlined /> : <DownOutlined />}
            </Button>

            <Popconfirm
              title="确定要删除该供应商吗？"
              description="删除后该供应商下的所有模型配置将被移除"
              onConfirm={() => handleDeleteProvider(providerItem.providerId)}
              okText="确定"
              cancelText="取消"
            >
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
              />
            </Popconfirm>
          </div>
        </div>

        {isExpanded && (
          <div className="provider-models-list">
            {(providerItem.models || []).map((model) => (
              <div key={model.id} className="provider-model-row">
                <div className="provider-model-info">
                  <span className="provider-model-name">{model.name || model.id}</span>
                  <span className="provider-model-meta">{model.type} · Max Tokens {model.maxTokens}</span>
                </div>
                <div className="provider-model-actions">
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => handleOpenEditModelModal(model)}
                  />
                  <Switch
                    size="small"
                    checked={model.status === '1'}
                    onChange={() => toggleLlmModelStatus(providerItem.providerId, model.id)}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  };

  // 初次进入模型管理：并行加载供应商列表和 /my_llms
  useEffect(() => {
    loadProviders();
    loadMyLlmsData();
  }, []);

  // 当供应商列表、缓存的 /my_llms 或当前选中供应商变化时，更新右侧“已添加的模型”
  useEffect(() => {
    if (!selectedProvider && providers.length > 0) {
      const provider = providers[0];
      setSelectedProvider(provider);
      loadModelsByProvider(provider);
    } else if (selectedProvider) {
      loadModelsByProvider(selectedProvider);
    }
  }, [providers, llmProviders, selectedProvider]);

  

  return (
    <div className="model-settings">
      <div className="model-settings-container">
        {/* 左侧 - 供应商列表 */}
        <div className="model-sidebar">
          <div className="sidebar-header">
            <h3 className="sidebar-title">{t('models.title')}</h3>
          </div>

          <div className="sidebar-list">
            {providers.length === 0 ? (
              <Empty description={t('models.no_data')} style={{ marginTop: 24, marginBottom: 24 }} />
            ) : (
              providers.map(provider => renderSidebarProvider(provider))
            )}
          </div>
        </div>

        {/* 右侧 - 已添加的模型总览（按供应商分组，独立于左侧选中状态） */}
        <div className="provider-detail-panel">
          <div className="provider-detail-content">
            <Spin spinning={loading}>
              <div className="models-section">
                <div className="models-header">
                  <h3 className="section-title">
                    已添加的模型
                  </h3>
                </div>

                <div className="provider-cards-list">
                  {llmProviders.length === 0 ? (
                    <Empty description={t('models.no_data')} style={{ marginTop: 24, marginBottom: 24 }} />
                  ) : (
                    llmProviders.map(item => renderProviderCard(item))
                  )}
                </div>
              </div>
            </Spin>
          </div>
        </div>
      </div>

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

      {/* API 密钥配置模态框 */}
      <Modal
        title={selectedProvider ? `${selectedProvider.name} 配置` : '供应商配置'}
        open={apiKeyModalVisible}
        onCancel={() => {
          setApiKeyModalVisible(false);
          setApiKey('');
          setBaseUrl('');
          setModelName('');
        }}
        footer={null}
        width={480}
      >
        <div className="api-config-section">
          {selectedProvider?.providerCode === 'OpenAiCompatible' ? (
            <>
              <div className="config-item" style={{ marginBottom: 16 }}>
                <div style={{ marginBottom: 8, fontWeight: 500 }}>Base URL</div>
                <Input
                  placeholder="请输入 Base URL，例如：https://api.example.com/v1"
                  className="config-input"
                  value={baseUrl}
                  onChange={(e) => setBaseUrl(e.target.value)}
                />
              </div>
              <div className="config-item" style={{ marginBottom: 16 }}>
                <div style={{ marginBottom: 8, fontWeight: 500 }}>API 密钥</div>
                <Input
                  placeholder="请输入 API 密钥"
                  type="password"
                  className="config-input"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                />
              </div>
              <div className="config-item" style={{ marginBottom: 16 }}>
                <div style={{ marginBottom: 8, fontWeight: 500 }}>模型名称</div>
                <Input
                  placeholder="请输入模型名称，例如：gpt-3.5-turbo"
                  className="config-input"
                  value={modelName}
                  onChange={(e) => setModelName(e.target.value)}
                />
              </div>
            </>
          ) : (
            <>
              <h3 className="section-title">API 密钥</h3>
              <div className="config-item">
                <Input
                  placeholder="请输入 API 密钥"
                  type="password"
                  className="config-input"
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                />
              </div>
            </>
          )}
          <Divider />
          <div className="flex justify-end gap-3">
            <Button onClick={() => {
              setApiKeyModalVisible(false);
              setApiKey('');
              setBaseUrl('');
              setModelName('');
            }}>
              {t('common.cancel')}
            </Button>
            <Button
              type="primary"
              loading={selectedProvider && testingProvider === selectedProvider.id.toString()}
              onClick={() => {
                if (selectedProvider) {
                  handleCheckProviderHealth(selectedProvider.id.toString());
                }
              }}
            >
              {t('common.detection')}
            </Button>
          </div>
        </div>
      </Modal>

      {/* 新增模型模态框 */}
      <Modal
        title="新增模型"
        open={addModelModalVisible}
        onCancel={() => {
          setAddModelModalVisible(false);
          setAddModelName('');
          addModelForm.resetFields();
        }}
        footer={null}
        width={480}
      >
        <Form
          form={addModelForm}
          layout="vertical"
          requiredMark={false}
        >
          <Form.Item
            name="modelName"
            label="模型名称"
            rules={[{ required: true, message: '请输入模型名称' }]}
          >
            <Input
              placeholder="请输入模型名称，例如：gpt-4"
              value={addModelName}
              onChange={(e) => setAddModelName(e.target.value)}
            />
          </Form.Item>

          <Divider />

          <div className="flex justify-end gap-3">
            <Button onClick={() => {
              setAddModelModalVisible(false);
              setAddModelName('');
              addModelForm.resetFields();
            }}>
              {t('common.cancel')}
            </Button>
            <Button
              type="primary"
              loading={checkingModelHealth}
              onClick={async () => {
                try {
                  await addModelForm.validateFields();
                  const modelName = addModelForm.getFieldValue('modelName');
                  if (!modelName) {
                    message.error('请输入模型名称');
                    return;
                  }
                  setCheckingModelHealth(true);
                  const result = await checkModelHealth(addModelProviderCode, modelName);
                  if (result.success) {
                    message.success(result.message || '该供应商支持此模型');
                    // 检测成功后刷新模型列表
                    await loadMyLlmsData();
                  } else {
                    message.error(result.message || '该供应商不支持此模型');
                  }
                  // 无论成功与否都关闭弹窗
                  setAddModelModalVisible(false);
                  setAddModelName('');
                  addModelForm.resetFields();
                } catch (error: any) {
                  message.error(error.message || '检测失败');
                  console.error('Failed to check model health:', error);
                  // 异常时也关闭弹窗
                  setAddModelModalVisible(false);
                  setAddModelName('');
                  addModelForm.resetFields();
                } finally {
                  setCheckingModelHealth(false);
                }
              }}
            >
              确定
            </Button>
          </div>
        </Form>
      </Modal>

      {/* 编辑模型配置弹窗 */}
      <Modal
        title="编辑模型配置"
        open={editModelModalVisible}
        onCancel={() => {
          setEditModelModalVisible(false);
          setEditingLlmModel(null);
          editModelForm.resetFields();
        }}
        footer={null}
        width={480}
      >
        <Form
          form={editModelForm}
          layout="vertical"
          requiredMark={false}
        >
          <div style={{ marginBottom: 16 }}>
            <span style={{ color: '#666' }}>模型名称：</span>
            <span style={{ fontWeight: 500 }}>{editingLlmModel?.name || editingLlmModel?.id}</span>
          </div>

          <Form.Item
            name="maxToken"
            label="最大 Token"
            rules={[
              { required: true, message: '请输入最大 Token 数' },
              { type: 'number', min: 1, message: '最大 Token 必须大于 0' }
            ]}
          >
            <InputNumber
              style={{ width: '100%' }}
              placeholder="请输入最大 Token 数"
              min={1}
              max={10000000}
            />
          </Form.Item>

          <Divider />

          <div className="flex justify-end gap-3">
            <Button onClick={() => {
              setEditModelModalVisible(false);
              setEditingLlmModel(null);
              editModelForm.resetFields();
            }}>
              {t('common.cancel')}
            </Button>
            <Button
              type="primary"
              loading={updatingModelConfig}
              onClick={handleSaveModelConfig}
            >
              保存
            </Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
}

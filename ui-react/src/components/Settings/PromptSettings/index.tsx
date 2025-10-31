import React, {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Badge, Button, Card, Input, message, Popconfirm, Select, Space, Tag, Tooltip} from 'antd';
import {
    ClockCircleOutlined,
    CopyOutlined,
    DeleteOutlined,
    EditOutlined,
    EyeOutlined,
    FileTextOutlined,
    PlusOutlined,
    TagsOutlined,
    UserOutlined
} from '@ant-design/icons';
import {
    createPrompt,
    deletePrompt,
    getPromptCategories,
    getPrompts,
    PromptCategory,
    PromptCreateRequest,
    PromptTemplate,
    updatePrompt
} from '@/api/prompts';
import {PromptEditor} from './PromptEditor';
import {PromptPreview} from './PromptPreview';
import './PromptSettings.css';

const { Search } = Input;
const { Option } = Select;

export default function PromptSettings() {
  const { t } = useTranslation();
  const [prompts, setPrompts] = useState<PromptTemplate[]>([]);
  const [categories, setCategories] = useState<PromptCategory[]>([]);
  const [filteredPrompts, setFilteredPrompts] = useState<PromptTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [editorVisible, setEditorVisible] = useState(false);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [editingPrompt, setEditingPrompt] = useState<PromptTemplate | null>(null);
  const [previewPrompt, setPreviewPrompt] = useState<PromptTemplate | null>(null);

  // 加载数据
  const loadData = async () => {
    setLoading(true);
    try {
      const [promptsData, categoriesData] = await Promise.all([
        getPrompts(),
        getPromptCategories()
      ]);
      setPrompts(promptsData);
      setCategories(categoriesData);
      setFilteredPrompts(promptsData);
    } catch (error) {
      message.error(t('prompts.errors.load_failed'));
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  // 过滤提示词
  const filterPrompts = () => {
    let filtered = prompts;

    // 按分类过滤
    if (selectedCategory) {
      filtered = filtered.filter(p => p.category === selectedCategory);
    }

    // 按搜索关键词过滤
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(p => 
        p.title.toLowerCase().includes(query) ||
        p.description?.toLowerCase().includes(query) ||
        p.tags.some(tag => tag.toLowerCase().includes(query))
      );
    }

    setFilteredPrompts(filtered);
  };

  // 创建或更新提示词
  const handleSave = async (data: PromptCreateRequest) => {
    try {
      if (editingPrompt) {
        await updatePrompt(editingPrompt.id, data);
        message.success(t('prompts.success.updated'));
      } else {
        await createPrompt(data);
        message.success(t('prompts.success.created'));
      }

      setEditorVisible(false);
      setEditingPrompt(null);
      loadData();
    } catch (error) {
      message.error(editingPrompt ? t('prompts.errors.update_failed') : t('prompts.errors.create_failed'));
      console.error('Failed to save prompt:', error);
    }
  };

  // 删除提示词
  const handleDelete = async (id: string) => {
    try {
      await deletePrompt(id);
      message.success(t('prompts.success.deleted'));
      loadData();
    } catch (error) {
      message.error(t('prompts.errors.delete_failed'));
      console.error('Failed to delete prompt:', error);
    }
  };

  // 复制提示词
  const handleCopy = async (prompt: PromptTemplate) => {
    try {
      await navigator.clipboard.writeText(prompt.content);
      message.success(t('prompts.success.copied'));
    } catch (error) {
      message.error(t('prompts.errors.copy_failed'));
    }
  };

  // 打开编辑器
  const handleEdit = (prompt?: PromptTemplate) => {
    setEditingPrompt(prompt || null);
    setEditorVisible(true);
  };

  // 打开预览
  const handlePreview = (prompt: PromptTemplate) => {
    setPreviewPrompt(prompt);
    setPreviewVisible(true);
  };

  // 获取分类名称
  const getCategoryName = (categoryId: string) => {
    const category = categories.find(c => c.name === categoryId);
    return category ? category.title : categoryId;
  };

  // 渲染提示词卡片
  const renderPromptCard = (prompt: PromptTemplate) => (
    <Card
      key={prompt.id}
      className="prompt-card"
      size="small"
      actions={[
        <Tooltip title={t('prompts.preview')}>
          <Button 
            type="text" 
            icon={<EyeOutlined />}
            onClick={() => handlePreview(prompt)}
          />
        </Tooltip>,
        <Tooltip title={t('prompts.copy')}>
          <Button 
            type="text" 
            icon={<CopyOutlined />}
            onClick={() => handleCopy(prompt)}
          />
        </Tooltip>,
        !prompt.isSystem && (
          <Tooltip title={t('common.edit')}>
            <Button 
              type="text" 
              icon={<EditOutlined />}
              onClick={() => handleEdit(prompt)}
            />
          </Tooltip>
        ),
        !prompt.isSystem && (
          <Popconfirm
            title={t('prompts.confirm_delete')}
            onConfirm={() => handleDelete(prompt.id)}
          >
            <Tooltip title={t('common.delete')}>
              <Button 
                type="text" 
                danger 
                icon={<DeleteOutlined />}
              />
            </Tooltip>
          </Popconfirm>
        )
      ].filter(Boolean)}
    >
      <div className="prompt-header">
        <div className="prompt-title">
          {prompt.title}
          {prompt.isSystem && (
            <Tag color="blue" size="small" className="ml-2">
              {t('prompts.system')}
            </Tag>
          )}
        </div>
        <div className="prompt-meta">
          <Space size="small">
            <Tag color="geekblue">{getCategoryName(prompt.category)}</Tag>
            <span className="usage-count">
              <UserOutlined /> {prompt.usageCount}
            </span>
          </Space>
        </div>
      </div>

      {prompt.description && (
        <p className="prompt-description">{prompt.description}</p>
      )}

      <div className="prompt-tags">
        {prompt.tags.map(tag => (
          <Tag key={tag} size="small">{tag}</Tag>
        ))}
      </div>

      <div className="prompt-footer">
        <Space size="small" className="text-xs text-gray-500">
          <span>
            <ClockCircleOutlined /> {new Date(prompt.updatedAt).toLocaleDateString()}
          </span>
          {prompt.variables.length > 0 && (
            <span>
              <TagsOutlined /> {t('prompts.variables_count', { count: prompt.variables.length })}
            </span>
          )}
        </Space>
      </div>
    </Card>
  );

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    filterPrompts();
  }, [prompts, selectedCategory, searchQuery]);

  return (
    <div className="prompt-settings">
      <div className="flex gap-6 h-full">
        {/* 左侧：分类和搜索 */}
        <div className="w-1/4 border-r border-gray-200 dark:border-gray-600 pr-6">
          <div className="mb-6">
            <h3 className="text-lg font-semibold flex items-center gap-3 mb-4">
              <FileTextOutlined />
              {t('prompts.title')}
            </h3>
            <Button 
              type="primary" 
              icon={<PlusOutlined />}
              onClick={() => handleEdit()}
              block
            >
              {t('prompts.create_prompt')}
            </Button>
          </div>

          <div className="mb-4">
            <Search
              placeholder={t('prompts.search_placeholder')}
              allowClear
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <div className="categories">
            <h4 className="font-medium mb-3">{t('prompts.categories')}</h4>
            <div className="space-y-2">
              <div
                className={`category-item ${selectedCategory === '' ? 'active' : ''}`}
                onClick={() => setSelectedCategory('')}
              >
                <span>{t('prompts.all_categories')}</span>
                <Badge count={prompts.length} />
              </div>
              {categories.map(category => (
                <div
                  key={category.id}
                  className={`category-item ${selectedCategory === category.name ? 'active' : ''}`}
                  onClick={() => setSelectedCategory(category.name)}
                >
                  <span>{category.title}</span>
                  <Badge count={category.promptCount} />
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 右侧：提示词列表 */}
        <div className="flex-1">
          <div className="mb-4 flex justify-between items-center">
            <div>
              <h4 className="font-medium">
                {selectedCategory ? getCategoryName(selectedCategory) : t('prompts.all_prompts')}
              </h4>
              <p className="text-sm text-gray-500">
                {t('prompts.showing_count', { count: filteredPrompts.length })}
              </p>
            </div>
            <Select
              placeholder={t('prompts.sort_by')}
              style={{ width: 150 }}
              defaultValue="updated"
            >
              <Option value="updated">{t('prompts.sort.updated')}</Option>
              <Option value="created">{t('prompts.sort.created')}</Option>
              <Option value="usage">{t('prompts.sort.usage')}</Option>
              <Option value="name">{t('prompts.sort.name')}</Option>
            </Select>
          </div>

          <div className="prompt-grid">
            {filteredPrompts.map(renderPromptCard)}
          </div>

          {filteredPrompts.length === 0 && !loading && (
            <div className="empty-state">
              <FileTextOutlined className="text-4xl text-gray-300 mb-4" />
              <p className="text-gray-500">
                {searchQuery || selectedCategory 
                  ? t('prompts.no_results') 
                  : t('prompts.no_prompts')
                }
              </p>
            </div>
          )}
        </div>
      </div>

      {/* 提示词编辑器 */}
      <PromptEditor
        visible={editorVisible}
        prompt={editingPrompt}
        categories={categories}
        onSave={handleSave}
        onCancel={() => {
          setEditorVisible(false);
          setEditingPrompt(null);
        }}
      />

      {/* 提示词预览 */}
      {previewPrompt && (
        <PromptPreview
          visible={previewVisible}
          prompt={previewPrompt}
          onCancel={() => {
            setPreviewVisible(false);
            setPreviewPrompt(null);
          }}
        />
      )}
    </div>
  );
}

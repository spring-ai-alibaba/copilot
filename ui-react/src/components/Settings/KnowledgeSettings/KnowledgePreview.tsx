import React, {useEffect, useState} from 'react';
import {Button, Card, Divider, Input, message, Modal, Spin, Tag} from 'antd';
import {ClockCircleOutlined, FileTextOutlined, SearchOutlined} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import {getDocumentChunks, KnowledgeChunk, KnowledgeDocument, searchKnowledge} from '@/api/knowledge';

const { Search } = Input;

interface KnowledgePreviewProps {
  visible: boolean;
  onCancel: () => void;
  document: KnowledgeDocument;
  kbKey: string;
}

export function KnowledgePreview({ visible, onCancel, document, kbKey }: KnowledgePreviewProps) {
  const { t } = useTranslation();
  const [chunks, setChunks] = useState<KnowledgeChunk[]>([]);
  const [searchResults, setSearchResults] = useState<KnowledgeChunk[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<'chunks' | 'search'>('chunks');
  const [searchQuery, setSearchQuery] = useState('');

  // 加载文档分块
  const loadChunks = async () => {
    setLoading(true);
    try {
      const data = await getDocumentChunks(kbKey, document.id);
      setChunks(data);
    } catch (error) {
      message.error(t('knowledge.errors.load_chunks_failed'));
      console.error('Failed to load chunks:', error);
    } finally {
      setLoading(false);
    }
  };

  // 搜索知识库
  const handleSearch = async (query: string) => {
    if (!query.trim()) {
      setSearchResults([]);
      return;
    }

    setSearchLoading(true);
    try {
      const data = await searchKnowledge(kbKey, query, 10);
      setSearchResults(data);
    } catch (error) {
      message.error(t('knowledge.errors.search_failed'));
      console.error('Failed to search knowledge:', error);
    } finally {
      setSearchLoading(false);
    }
  };

  // 高亮搜索关键词
  const highlightText = (text: string, query: string) => {
    if (!query.trim()) return text;

    const regex = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
    const parts = text.split(regex);

    return parts.map((part, index) =>
      regex.test(part) ? (
        <mark key={index} className="bg-yellow-200 dark:bg-yellow-600">
          {part}
        </mark>
      ) : (
        part
      )
    );
  };

  // 渲染分块内容
  const renderChunkContent = (chunk: KnowledgeChunk, isSearchResult = false) => (
    <Card
      key={chunk.id}
      size="small"
      className="mb-4"
      bodyStyle={{ padding: '20px' }}
      title={
        <div className="flex justify-between items-center">
          <span className="text-sm">
            {t('knowledge.chunk_index', {
              current: chunk.chunkIndex + 1,
              total: chunk.totalChunks
            })}
          </span>
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <ClockCircleOutlined />
            {new Date(chunk.createdAt).toLocaleString()}
          </div>
        </div>
      }
    >
      <div className="text-sm leading-relaxed whitespace-pre-wrap p-2 bg-gray-50 dark:bg-gray-800 rounded-md">
        {isSearchResult && searchQuery ?
          highlightText(chunk.content, searchQuery) :
          chunk.content
        }
      </div>

      {chunk.metadata && Object.keys(chunk.metadata).length > 0 && (
        <>
          <Divider className="my-3" />
          <div className="text-xs text-gray-500">
            <div className="font-medium mb-1">{t('knowledge.metadata')}:</div>
            <div className="flex flex-wrap gap-1">
              {Object.entries(chunk.metadata).map(([key, value]) => (
                <Tag key={key} size="small">
                  {key}: {String(value)}
                </Tag>
              ))}
            </div>
          </div>
        </>
      )}
    </Card>
  );

  useEffect(() => {
    if (visible) {
      loadChunks();
      setActiveTab('chunks');
      setSearchQuery('');
      setSearchResults([]);
    }
  }, [visible, document.id, kbKey]);

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <FileTextOutlined />
          {t('knowledge.document_preview')} - {document.fileName}
        </div>
      }
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={800}
      style={{ top: 20 }}
      bodyStyle={{ height: '70vh', overflow: 'hidden' }}
    >
      <div className="h-full flex flex-col">
        {/* 文档信息 */}
        <Card size="small" className="mb-6" bodyStyle={{ padding: '20px' }}>
          <div className="grid grid-cols-2 gap-6 text-sm">
            <div>
              <span className="font-medium">{t('knowledge.file_size')}:</span> {(document.fileSize / 1024).toFixed(1)} KB
            </div>
            <div>
              <span className="font-medium">{t('knowledge.file_type')}:</span> {document.fileType}
            </div>
            <div>
              <span className="font-medium">{t('knowledge.chunks')}:</span> {document.chunkCount}
            </div>
            <div>
              <span className="font-medium">{t('knowledge.uploaded_by')}:</span> {document.uploadedBy}
            </div>
          </div>
        </Card>

        {/* 标签页切换 */}
        <div className="flex gap-3 mb-6 tab-buttons">
          <Button
            type={activeTab === 'chunks' ? 'primary' : 'default'}
            onClick={() => setActiveTab('chunks')}
          >
            {t('knowledge.document_chunks')}
          </Button>
          <Button
            type={activeTab === 'search' ? 'primary' : 'default'}
            onClick={() => setActiveTab('search')}
          >
            {t('knowledge.search_knowledge')}
          </Button>
        </div>

        {/* 搜索功能 */}
        {activeTab === 'search' && (
          <div className="mb-6">
            <Search
              placeholder={t('knowledge.search_placeholder')}
              allowClear
              enterButton={<SearchOutlined />}
              size="large"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onSearch={handleSearch}
              loading={searchLoading}
            />
          </div>
        )}

        {/* 内容区域 */}
        <div className="flex-1 overflow-y-auto">
          {activeTab === 'chunks' ? (
            <Spin spinning={loading}>
              {chunks.length > 0 ? (
                <div>
                  {chunks.map(chunk => renderChunkContent(chunk))}
                </div>
              ) : (
                <div className="text-center text-gray-500 py-8">
                  {t('knowledge.no_chunks')}
                </div>
              )}
            </Spin>
          ) : (
            <Spin spinning={searchLoading}>
              {searchResults.length > 0 ? (
                <div>
                  <div className="mb-3 text-sm text-gray-500">
                    {t('knowledge.search_results_count', { count: searchResults.length })}
                  </div>
                  {searchResults.map(chunk => renderChunkContent(chunk, true))}
                </div>
              ) : searchQuery ? (
                <div className="text-center text-gray-500 py-8">
                  {t('knowledge.no_search_results')}
                </div>
              ) : (
                <div className="text-center text-gray-500 py-8">
                  {t('knowledge.enter_search_query')}
                </div>
              )}
            </Spin>
          )}
        </div>
      </div>
    </Modal>
  );
}

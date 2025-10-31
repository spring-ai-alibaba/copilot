import React, {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import './KnowledgeSettings.css';
import type {UploadProps} from 'antd';
import {Button, Input, List, message, Popconfirm, Spin, Tag, Tooltip, Upload} from 'antd';
import {
    DatabaseOutlined,
    DeleteOutlined,
    EyeOutlined,
    FileTextOutlined,
    PlusOutlined,
    ReloadOutlined,
    UploadOutlined
} from '@ant-design/icons';
import {
    createKnowledgeBase,
    deleteDocument,
    deleteKnowledgeBase,
    getKnowledgeBases,
    getKnowledgeDocuments,
    KnowledgeBase,
    KnowledgeDocument,
    reprocessDocument,
    uploadDocument
} from '@/api/knowledge';
import {KnowledgePreview} from './KnowledgePreview';
import {CreateKnowledgeBaseModal} from './CreateKnowledgeBaseModal';

const { Search } = Input;

export default function KnowledgeSettings() {
  const { t } = useTranslation();
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [selectedKB, setSelectedKB] = useState<KnowledgeBase | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [loading, setLoading] = useState(false);
  const [documentsLoading, setDocumentsLoading] = useState(false);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState<KnowledgeDocument | null>(null);

  // 加载知识库列表
  const loadKnowledgeBases = async () => {
    setLoading(true);
    try {
      const data = await getKnowledgeBases();
      setKnowledgeBases(data);
    } catch (error) {
      message.error(t('knowledge.errors.load_failed'));
      console.error('Failed to load knowledge bases:', error);
    } finally {
      setLoading(false);
    }
  };

  // 加载文档列表
  const loadDocuments = async (kbKey: string) => {
    setDocumentsLoading(true);
    try {
      const data = await getKnowledgeDocuments(kbKey);
      setDocuments(data);
    } catch (error) {
      message.error(t('knowledge.errors.load_documents_failed'));
      console.error('Failed to load documents:', error);
    } finally {
      setDocumentsLoading(false);
    }
  };

  // 创建知识库
  const handleCreateKB = async (data: { key: string; name: string; description?: string }) => {
    try {
      await createKnowledgeBase(data);
      message.success(t('knowledge.success.kb_created'));
      setCreateModalVisible(false);
      loadKnowledgeBases();
    } catch (error) {
      message.error(t('knowledge.errors.create_failed'));
      console.error('Failed to create knowledge base:', error);
    }
  };

  // 删除知识库
  const handleDeleteKB = async (kbKey: string) => {
    try {
      await deleteKnowledgeBase(kbKey);
      message.success(t('knowledge.success.kb_deleted'));
      if (selectedKB?.key === kbKey) {
        setSelectedKB(null);
        setDocuments([]);
      }
      loadKnowledgeBases();
    } catch (error) {
      message.error(t('knowledge.errors.delete_failed'));
      console.error('Failed to delete knowledge base:', error);
    }
  };

  // 上传文档
  const uploadProps: UploadProps = {
    name: 'file',
    multiple: false,
    showUploadList: false,
    beforeUpload: (file) => {
      if (!selectedKB) {
        message.error(t('knowledge.errors.no_kb_selected'));
        return false;
      }

      // 检查文件类型
      const allowedTypes = ['.pdf', '.txt', '.doc', '.docx', '.md'];
      const fileExtension = file.name.toLowerCase().substring(file.name.lastIndexOf('.'));
      if (!allowedTypes.includes(fileExtension)) {
        message.error(t('knowledge.errors.unsupported_file_type'));
        return false;
      }

      // 检查文件大小 (10MB)
      if (file.size > 10 * 1024 * 1024) {
        message.error(t('knowledge.errors.file_too_large'));
        return false;
      }

      return true;
    },
    customRequest: async ({ file, onSuccess, onError }) => {
      if (!selectedKB) return;

      try {
        const response = await uploadDocument(selectedKB.key, file as File);
        if (response.success) {
          message.success(t('knowledge.success.document_uploaded'));
          loadDocuments(selectedKB.key);
          loadKnowledgeBases(); // 刷新统计信息
          onSuccess?.(response);
        } else {
          throw new Error(response.message);
        }
      } catch (error) {
        message.error(t('knowledge.errors.upload_failed'));
        console.error('Failed to upload document:', error);
        onError?.(error as Error);
      }
    },
  };

  // 删除文档
  const handleDeleteDocument = async (documentId: string) => {
    if (!selectedKB) return;

    try {
      await deleteDocument(selectedKB.key, documentId);
      message.success(t('knowledge.success.document_deleted'));
      loadDocuments(selectedKB.key);
      loadKnowledgeBases(); // 刷新统计信息
    } catch (error) {
      message.error(t('knowledge.errors.delete_document_failed'));
      console.error('Failed to delete document:', error);
    }
  };

  // 重新处理文档
  const handleReprocessDocument = async (documentId: string) => {
    if (!selectedKB) return;

    try {
      const response = await reprocessDocument(selectedKB.key, documentId);
      if (response.success) {
        message.success(t('knowledge.success.document_reprocessed'));
        loadDocuments(selectedKB.key);
      } else {
        throw new Error(response.message);
      }
    } catch (error) {
      message.error(t('knowledge.errors.reprocess_failed'));
      console.error('Failed to reprocess document:', error);
    }
  };

  // 预览文档
  const handlePreviewDocument = (document: KnowledgeDocument) => {
    setSelectedDocument(document);
    setPreviewVisible(true);
  };

  // 选择知识库
  const handleSelectKB = (kb: KnowledgeBase) => {
    setSelectedKB(kb);
    loadDocuments(kb.key);
  };

  // 获取状态标签
  const getStatusTag = (status: KnowledgeDocument['processStatus']) => {
    const statusConfig = {
      PENDING: { color: 'orange', text: t('knowledge.status.pending') },
      PROCESSING: { color: 'blue', text: t('knowledge.status.processing') },
      COMPLETED: { color: 'green', text: t('knowledge.status.completed') },
      FAILED: { color: 'red', text: t('knowledge.status.failed') },
    };

    const config = statusConfig[status];
    return <Tag color={config.color}>{config.text}</Tag>;
  };

  useEffect(() => {
    loadKnowledgeBases();
  }, []);

  return (
    <div className="knowledge-settings">
      <div className="flex gap-6 h-full">
        {/* 左侧：知识库列表 */}
        <div className="w-1/3 border-r border-gray-200 dark:border-gray-600 pr-6">
          <div className="flex justify-between items-center mb-6">
            <h3 className="text-lg font-semibold flex items-center gap-3">
              <DatabaseOutlined />
              {t('knowledge.knowledge_bases')}
            </h3>
            <Button 
              type="primary" 
              icon={<PlusOutlined />}
              onClick={() => setCreateModalVisible(true)}
            >
              {t('knowledge.create_kb')}
            </Button>
          </div>

          <Spin spinning={loading}>
            <List
              dataSource={knowledgeBases}
              renderItem={(kb) => (
                <List.Item
                  className={`cursor-pointer p-4 rounded-lg border mb-3 transition-colors ${
                    selectedKB?.key === kb.key
                      ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                      : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'
                  }`}
                  onClick={() => handleSelectKB(kb)}
                  actions={[
                    <Popconfirm
                      title={t('knowledge.confirm_delete_kb')}
                      onConfirm={(e) => {
                        e?.stopPropagation();
                        handleDeleteKB(kb.key);
                      }}
                      onClick={(e) => e?.stopPropagation()}
                    >
                      <Button 
                        type="text" 
                        danger 
                        icon={<DeleteOutlined />}
                        size="small"
                      />
                    </Popconfirm>
                  ]}
                >
                  <List.Item.Meta
                    title={<div className="text-base font-medium mb-2">{kb.name}</div>}
                    description={
                      <div className="space-y-2">
                        <div className="text-sm text-gray-600 dark:text-gray-300 leading-relaxed">
                          {kb.description}
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400">
                          {t('knowledge.document_count', { count: kb.documentCount })} •
                          {t('knowledge.chunk_count', { count: kb.chunkCount })}
                        </div>
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </Spin>
        </div>

        {/* 右侧：文档列表 */}
        <div className="flex-1 pl-2">
          {selectedKB ? (
            <>
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-lg font-semibold flex items-center gap-3">
                  <FileTextOutlined />
                  {t('knowledge.documents')} - {selectedKB.name}
                </h3>
                <Upload {...uploadProps}>
                  <Button type="primary" icon={<UploadOutlined />}>
                    {t('knowledge.upload_document')}
                  </Button>
                </Upload>
              </div>

              <Spin spinning={documentsLoading}>
                <List
                  dataSource={documents}
                  renderItem={(doc) => (
                    <List.Item
                      className="border border-gray-200 dark:border-gray-600 rounded-lg p-4 mb-3 hover:border-gray-300 dark:hover:border-gray-500 transition-colors"
                      actions={[
                        <Tooltip title={t('knowledge.preview')}>
                          <Button 
                            type="text" 
                            icon={<EyeOutlined />}
                            onClick={() => handlePreviewDocument(doc)}
                          />
                        </Tooltip>,
                        <Tooltip title={t('knowledge.reprocess')}>
                          <Button 
                            type="text" 
                            icon={<ReloadOutlined />}
                            onClick={() => handleReprocessDocument(doc.id)}
                            disabled={doc.processStatus === 'PROCESSING'}
                          />
                        </Tooltip>,
                        <Popconfirm
                          title={t('knowledge.confirm_delete_document')}
                          onConfirm={() => handleDeleteDocument(doc.id)}
                        >
                          <Button 
                            type="text" 
                            danger 
                            icon={<DeleteOutlined />}
                          />
                        </Popconfirm>
                      ]}
                    >
                      <List.Item.Meta
                        title={
                          <div className="flex items-center gap-3 mb-2">
                            <span className="text-base font-medium">{doc.fileName}</span>
                            {getStatusTag(doc.processStatus)}
                          </div>
                        }
                        description={
                          <div className="text-sm text-gray-600 dark:text-gray-300 space-y-2">
                            <div className="flex flex-wrap gap-4">
                              <span>{t('knowledge.file_size')}: {(doc.fileSize / 1024).toFixed(1)} KB</span>
                              <span>{t('knowledge.file_type')}: {doc.fileType}</span>
                              <span>{t('knowledge.chunks')}: {doc.chunkCount}</span>
                            </div>
                            <div className="flex flex-wrap gap-4 text-xs text-gray-500 dark:text-gray-400">
                              <span>{t('knowledge.uploaded_by')}: {doc.uploadedBy}</span>
                              <span>{t('knowledge.uploaded_at')}: {new Date(doc.uploadedAt).toLocaleString()}</span>
                            </div>
                            {doc.processStatusRemark && (
                              <div className="text-red-500 dark:text-red-400 mt-2 p-2 bg-red-50 dark:bg-red-900/20 rounded">
                                {doc.processStatusRemark}
                              </div>
                            )}
                          </div>
                        }
                      />
                    </List.Item>
                  )}
                />
              </Spin>
            </>
          ) : (
            <div className="flex items-center justify-center h-full text-gray-500 dark:text-gray-400">
              <div className="text-center p-8">
                <DatabaseOutlined className="text-5xl mb-6 text-gray-300 dark:text-gray-600" />
                <p className="text-lg">{t('knowledge.select_kb_hint')}</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 创建知识库模态框 */}
      <CreateKnowledgeBaseModal
        visible={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        onOk={handleCreateKB}
      />

      {/* 文档预览模态框 */}
      {selectedDocument && selectedKB && (
        <KnowledgePreview
          visible={previewVisible}
          onCancel={() => setPreviewVisible(false)}
          document={selectedDocument}
          kbKey={selectedKB.key}
        />
      )}
    </div>
  );
}

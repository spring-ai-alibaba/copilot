import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from 'i18next';
import KnowledgeSettings from './index';

// 模拟 i18n
i18n.init({
  lng: 'zh',
  resources: {
    zh: {
      translation: {
        knowledge: {
          knowledge_bases: '知识库',
          documents: '文档',
          create_kb: '创建知识库',
          upload_document: '上传文档',
          select_kb_hint: '请选择一个知识库查看文档',
          document_count: '文档数：{{count}}',
          chunk_count: '分块数：{{count}}',
        },
        common: {
          cancel: '取消',
          create: '创建',
        }
      }
    }
  }
});

// 模拟 API
jest.mock('../../../api/knowledge', () => ({
  getKnowledgeBases: jest.fn(() => Promise.resolve([
    {
      id: '1',
      key: 'test-kb',
      name: '测试知识库',
      description: '这是一个测试知识库',
      createdBy: 'test_user',
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
      documentCount: 2,
      chunkCount: 10
    }
  ])),
  getKnowledgeDocuments: jest.fn(() => Promise.resolve([
    {
      id: 'doc1',
      fileName: '测试文档.pdf',
      fileSize: 1024000,
      fileType: 'PDF',
      uploadedBy: 'test_user',
      uploadedAt: '2024-01-01T00:00:00Z',
      processStatus: 'COMPLETED',
      chunkCount: 5
    }
  ])),
  createKnowledgeBase: jest.fn(() => Promise.resolve({
    id: '2',
    key: 'new-kb',
    name: '新知识库',
    description: '新创建的知识库',
    createdBy: 'test_user',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    documentCount: 0,
    chunkCount: 0
  })),
  deleteKnowledgeBase: jest.fn(() => Promise.resolve()),
  uploadDocument: jest.fn(() => Promise.resolve({ success: true, message: '上传成功' })),
  deleteDocument: jest.fn(() => Promise.resolve()),
  getDocumentChunks: jest.fn(() => Promise.resolve([
    {
      id: 'chunk1',
      content: '这是一个测试分块内容',
      metadata: { fileName: '测试文档.pdf', page: 1 },
      createdAt: '2024-01-01T00:00:00Z',
      chunkIndex: 0,
      totalChunks: 1
    }
  ])),
  searchKnowledge: jest.fn(() => Promise.resolve([
    {
      id: 'chunk1',
      content: '搜索结果内容',
      metadata: { fileName: '测试文档.pdf' },
      createdAt: '2024-01-01T00:00:00Z',
      chunkIndex: 0,
      totalChunks: 1
    }
  ])),
  reprocessDocument: jest.fn(() => Promise.resolve({ success: true, message: '重新处理成功' }))
}));

const renderWithI18n = (component: React.ReactElement) => {
  return render(
    <I18nextProvider i18n={i18n}>
      {component}
    </I18nextProvider>
  );
};

describe('KnowledgeSettings', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders knowledge settings component', async () => {
    renderWithI18n(<KnowledgeSettings />);
    
    // 检查主要元素是否渲染
    expect(screen.getByText('知识库')).toBeInTheDocument();
    expect(screen.getByText('创建知识库')).toBeInTheDocument();
    
    // 等待数据加载
    await waitFor(() => {
      expect(screen.getByText('测试知识库')).toBeInTheDocument();
    });
  });

  test('displays knowledge base list', async () => {
    renderWithI18n(<KnowledgeSettings />);
    
    await waitFor(() => {
      expect(screen.getByText('测试知识库')).toBeInTheDocument();
      expect(screen.getByText('这是一个测试知识库')).toBeInTheDocument();
    });
  });

  test('shows documents when knowledge base is selected', async () => {
    renderWithI18n(<KnowledgeSettings />);
    
    // 等待知识库加载
    await waitFor(() => {
      expect(screen.getByText('测试知识库')).toBeInTheDocument();
    });
    
    // 点击知识库
    fireEvent.click(screen.getByText('测试知识库'));
    
    // 等待文档加载
    await waitFor(() => {
      expect(screen.getByText('测试文档.pdf')).toBeInTheDocument();
    });
  });

  test('opens create knowledge base modal', async () => {
    renderWithI18n(<KnowledgeSettings />);
    
    // 点击创建知识库按钮
    fireEvent.click(screen.getByText('创建知识库'));
    
    // 检查模态框是否打开
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
  });

  test('shows hint when no knowledge base is selected', () => {
    renderWithI18n(<KnowledgeSettings />);
    
    expect(screen.getByText('请选择一个知识库查看文档')).toBeInTheDocument();
  });
});

describe('CreateKnowledgeBaseModal', () => {
  test('validates required fields', async () => {
    renderWithI18n(<KnowledgeSettings />);
    
    // 打开创建模态框
    fireEvent.click(screen.getByText('创建知识库'));
    
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
    
    // 尝试提交空表单
    fireEvent.click(screen.getByText('创建'));
    
    // 应该显示验证错误
    await waitFor(() => {
      expect(screen.getByText('Please enter knowledge base key')).toBeInTheDocument();
    });
  });
});

describe('KnowledgePreview', () => {
  test('displays document chunks', async () => {
    renderWithI18n(<KnowledgeSettings />);
    
    // 等待知识库加载并选择
    await waitFor(() => {
      expect(screen.getByText('测试知识库')).toBeInTheDocument();
    });
    
    fireEvent.click(screen.getByText('测试知识库'));
    
    // 等待文档加载
    await waitFor(() => {
      expect(screen.getByText('测试文档.pdf')).toBeInTheDocument();
    });
    
    // 点击预览按钮
    const previewButton = screen.getByLabelText('eye');
    fireEvent.click(previewButton);
    
    // 检查预览模态框
    await waitFor(() => {
      expect(screen.getByText('Document Preview - 测试文档.pdf')).toBeInTheDocument();
    });
  });
});

// 集成测试
describe('Knowledge Management Integration', () => {
  test('complete workflow: create KB, upload document, preview', async () => {
    renderWithI18n(<KnowledgeSettings />);
    
    // 1. 创建知识库
    fireEvent.click(screen.getByText('创建知识库'));
    
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
    
    // 填写表单
    fireEvent.change(screen.getByPlaceholderText('e.g.: my-knowledge-base'), {
      target: { value: 'test-kb-2' }
    });
    fireEvent.change(screen.getByPlaceholderText('e.g.: My Knowledge Base'), {
      target: { value: '测试知识库2' }
    });
    
    fireEvent.click(screen.getByText('创建'));
    
    // 2. 等待创建完成并选择新知识库
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    
    // 3. 验证工作流程完成
    expect(screen.getByText('知识库')).toBeInTheDocument();
  });
});

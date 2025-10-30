// 知识库管理API接口
import {
    isDevelopmentMode,
    mockChunks,
    mockDelay,
    mockDocuments,
    mockKnowledgeBases,
    mockSearchResults
} from './mockKnowledgeData';

export interface KnowledgeBase {
  id: string;
  key: string;
  name: string;
  description?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  documentCount: number;
  chunkCount: number;
}

export interface KnowledgeDocument {
  id: string;
  fileName: string;
  fileSize: number;
  fileType: string;
  uploadedBy: string;
  uploadedAt: string;
  processStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  processStatusRemark?: string;
  chunkCount: number;
}

export interface KnowledgeChunk {
  id: string;
  content: string;
  metadata: Record<string, any>;
  createdAt: string;
  chunkIndex: number;
  totalChunks: number;
}

export interface UploadResponse {
  success: boolean;
  message: string;
  documentId?: string;
}

// API基础URL - 这里需要根据实际后端地址调整
const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080';

// 获取知识库列表
export const getKnowledgeBases = async (): Promise<KnowledgeBase[]> => {
  // 开发模式使用模拟数据
  if (isDevelopmentMode) {
    await mockDelay(500);
    return mockKnowledgeBases;
  }

  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取知识库列表失败: ${response.statusText}`);
  }

  return response.json();
};

// 创建知识库
export const createKnowledgeBase = async (data: {
  key: string;
  name: string;
  description?: string;
}): Promise<KnowledgeBase> => {
  // 开发模式使用模拟数据
  if (isDevelopmentMode) {
    await mockDelay(800);
    const newKB: KnowledgeBase = {
      id: Date.now().toString(),
      key: data.key,
      name: data.name,
      description: data.description,
      createdBy: 'current_user',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      documentCount: 0,
      chunkCount: 0
    };
    mockKnowledgeBases.push(newKB);
    return newKB;
  }

  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`创建知识库失败: ${response.statusText}`);
  }

  return response.json();
};

// 删除知识库
export const deleteKnowledgeBase = async (kbKey: string): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases/${kbKey}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(`删除知识库失败: ${response.statusText}`);
  }
};

// 获取知识库文档列表
export const getKnowledgeDocuments = async (kbKey: string): Promise<KnowledgeDocument[]> => {
  // 开发模式使用模拟数据
  if (isDevelopmentMode) {
    await mockDelay(300);
    return mockDocuments[kbKey] || [];
  }

  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases/${kbKey}/documents`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取文档列表失败: ${response.statusText}`);
  }

  return response.json();
};

// 上传文档到知识库
export const uploadDocument = async (kbKey: string, file: File): Promise<UploadResponse> => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases/${kbKey}/upload`, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`上传文档失败: ${response.statusText}`);
  }

  return response.json();
};

// 删除文档
export const deleteDocument = async (kbKey: string, documentId: string): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases/${kbKey}/documents/${documentId}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(`删除文档失败: ${response.statusText}`);
  }
};

// 获取文档的分块信息
export const getDocumentChunks = async (kbKey: string, documentId: string): Promise<KnowledgeChunk[]> => {
  // 开发模式使用模拟数据
  if (isDevelopmentMode) {
    await mockDelay(400);
    return mockChunks[documentId] || [];
  }

  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases/${kbKey}/documents/${documentId}/chunks`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取文档分块失败: ${response.statusText}`);
  }

  return response.json();
};

// 搜索知识库
export const searchKnowledge = async (kbKey: string, query: string, topK: number = 5): Promise<KnowledgeChunk[]> => {
  // 开发模式使用模拟数据
  if (isDevelopmentMode) {
    await mockDelay(600);
    // 简单的关键词匹配
    const results = mockSearchResults[query] || [];
    return results.slice(0, topK);
  }

  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases/${kbKey}/search`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, topK }),
  });

  if (!response.ok) {
    throw new Error(`搜索知识库失败: ${response.statusText}`);
  }

  return response.json();
};

// 重新处理文档
export const reprocessDocument = async (kbKey: string, documentId: string): Promise<UploadResponse> => {
  const response = await fetch(`${API_BASE_URL}/api/rag/knowledge-bases/${kbKey}/documents/${documentId}/reprocess`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`重新处理文档失败: ${response.statusText}`);
  }

  return response.json();
};

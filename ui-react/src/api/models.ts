// 模型管理API接口

export interface AIModel {
  id: string;
  name: string;
  title: string;
  type: 'text' | 'image' | 'embedding' | 'rerank';
  platform: string;
  setting: string;
  remark?: string;
  isFree: boolean;
  isEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ModelCreateRequest {
  name: string;
  title: string;
  type: 'text' | 'image' | 'embedding' | 'rerank';
  platform: string;
  setting: string;
  remark?: string;
  isFree: boolean;
}

export interface ModelUpdateRequest extends Partial<ModelCreateRequest> {
  isEnabled?: boolean;
}

import { apiUrl } from './base';

// 模拟数据
const mockModels: AIModel[] = [
  {
    id: '1',
    name: 'gpt-4',
    title: 'GPT-4',
    type: 'text',
    platform: 'OpenAI',
    setting: '{"apiKey": "sk-xxx", "baseUrl": "https://api.openai.com/v1"}',
    remark: '最强大的文本生成模型',
    isFree: false,
    isEnabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z'
  },
  {
    id: '2',
    name: 'gpt-3.5-turbo',
    title: 'GPT-3.5 Turbo',
    type: 'text',
    platform: 'OpenAI',
    setting: '{"apiKey": "sk-xxx", "baseUrl": "https://api.openai.com/v1"}',
    remark: '快速且经济的文本生成模型',
    isFree: false,
    isEnabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z'
  },
  {
    id: '3',
    name: 'claude-3-sonnet',
    title: 'Claude 3 Sonnet',
    type: 'text',
    platform: 'Anthropic',
    setting: '{"apiKey": "sk-ant-xxx", "baseUrl": "https://api.anthropic.com"}',
    remark: 'Anthropic的高质量对话模型',
    isFree: false,
    isEnabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z'
  },
  {
    id: '4',
    name: 'dall-e-3',
    title: 'DALL-E 3',
    type: 'image',
    platform: 'OpenAI',
    setting: '{"apiKey": "sk-xxx", "baseUrl": "https://api.openai.com/v1"}',
    remark: '高质量图像生成模型',
    isFree: false,
    isEnabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z'
  },
  {
    id: '5',
    name: 'text-embedding-ada-002',
    title: 'Text Embedding Ada 002',
    type: 'embedding',
    platform: 'OpenAI',
    setting: '{"apiKey": "sk-xxx", "baseUrl": "https://api.openai.com/v1"}',
    remark: '文本向量化模型',
    isFree: false,
    isEnabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z'
  }
];

const isDevelopmentMode = process.env.NODE_ENV === 'development';
const mockDelay = (ms: number = 1000) => new Promise(resolve => setTimeout(resolve, ms));

// 获取模型列表
export const getModels = async (): Promise<AIModel[]> => {
  if (isDevelopmentMode) {
    await mockDelay(500);
    return [...mockModels];
  }

  const response = await fetch(apiUrl('/api/models'), {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取模型列表失败: ${response.statusText}`);
  }

  return response.json();
};

// 创建模型
export const createModel = async (data: ModelCreateRequest): Promise<AIModel> => {
  if (isDevelopmentMode) {
    await mockDelay(800);
    const newModel: AIModel = {
      id: Date.now().toString(),
      ...data,
      isEnabled: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
    mockModels.push(newModel);
    return newModel;
  }

  const response = await fetch(apiUrl('/api/models'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`创建模型失败: ${response.statusText}`);
  }

  return response.json();
};

// 更新模型
export const updateModel = async (id: string, data: ModelUpdateRequest): Promise<AIModel> => {
  if (isDevelopmentMode) {
    await mockDelay(600);
    const index = mockModels.findIndex(m => m.id === id);
    if (index === -1) {
      throw new Error('模型不存在');
    }
    mockModels[index] = {
      ...mockModels[index],
      ...data,
      updatedAt: new Date().toISOString()
    };
    return mockModels[index];
  }

  const response = await fetch(apiUrl(`/api/models/${id}`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`更新模型失败: ${response.statusText}`);
  }

  return response.json();
};

// 删除模型
export const deleteModel = async (id: string): Promise<void> => {
  if (isDevelopmentMode) {
    await mockDelay(400);
    const index = mockModels.findIndex(m => m.id === id);
    if (index !== -1) {
      mockModels.splice(index, 1);
    }
    return;
  }

  const response = await fetch(apiUrl(`/api/models/${id}`), {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(`删除模型失败: ${response.statusText}`);
  }
};

// 测试模型连接
export const testModel = async (id: string): Promise<{ success: boolean; message: string }> => {
  if (isDevelopmentMode) {
    await mockDelay(2000);
    // 模拟随机成功/失败
    const success = Math.random() > 0.3;
    return {
      success,
      message: success ? '模型连接测试成功' : '模型连接测试失败：API密钥无效'
    };
  }

  const response = await fetch(apiUrl(`/api/models/${id}/test`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`测试模型失败: ${response.statusText}`);
  }

  return response.json();
};

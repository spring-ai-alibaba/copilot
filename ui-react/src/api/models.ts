export interface Provider {
  id: number;
  name: string;
  logo?: string | null;
  tags?: string | null;
  sortOrder?: number;
  status: number;
  providerCode?: string;
  baseUrl?: string;
  createdTime?: string;
  updatedTime?: string;
}

export interface DiscoveredModel {
  modelId: string;
  modelName: string;
  modelType: string; 
  maxTokens?: number;
  supportedModalities?: string[];
  supportsFunctionCalling?: boolean;
  description?: string;
}

export interface AIModel {
  id: string;
  name: string;
  title: string;
  type: 'text' | 'image' | 'embedding' | 'rerank';
  providerId: string;
  providerName: string;
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
  providerId: string;
  setting: string;
  remark?: string;
  isFree: boolean;
}

export interface ModelUpdateRequest extends Partial<ModelCreateRequest> {
  isEnabled?: boolean;
}

export interface ProviderHealthResponse {
  success?: boolean;
  healthy?: boolean;
  message: string;
  status?: 'ON' | 'OFF';
  responseTime?: number;
  error?: string | null;
  testModelName?: string;
  maxTokens?: number;
  providerName?: string;
}

// 当前用户在各供应商下已配置的大模型信息（对应 /my_llms 接口）
export interface LlmModel {
  id: string;
  name?: string;
  type: string;
  maxTokens: number;
  status: string;
  extra?: Record<string, any>;
}

export interface LlmServiceProvider {
  providerId: string;
  models: LlmModel[];
  metadata?: string;
}

import { apiUrl } from './base';

// 获取供应商列表
export const getProviders = async (): Promise<Provider[]> => {
  const response = await fetch(apiUrl('/api/model-provider/list'), {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取供应商列表失败: ${response.statusText}`);
  }

  const result = await response.json();
  const data = result?.data || result;
  const list = Array.isArray(data) ? data : [data];

  return list.map((item: any) => ({
    id: item.id,
    name: item.name || item.providerName || '',
    logo: item.logo ?? null,
    tags: item.tags ?? null,
    sortOrder: item.sortOrder,
    status: typeof item.status === 'number' ? item.status : (item.enabled ? 1 : 0),
    providerCode: item.providerCode || '',
    baseUrl: item.baseUrl,
    createdTime: item.createdTime,
    updatedTime: item.updatedTime,
  }));
};

// 发现供应商的模型列表
export interface DiscoverModelsRequest {
  providerId: string;
  baseUrl?: string;
}

// 获取指定供应商的模型列表（从 discover 接口）
export const getModelsByProvider = async (provider: Provider): Promise<DiscoveredModel[]> => {
  // 构建查询参数
  const params = provider.id.toString()
  
  // 如果 baseUrl 存在，添加到查询参数
  // if (provider.baseUrl) {
  //   params.append('baseUrl', provider.baseUrl);
  // }

  const response = await fetch(apiUrl(`/api/model-provider/discover/${params.toString()}`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取模型列表失败: ${response.statusText}`);
  }

  const result = await response.json();

  let data: DiscoveredModel[];
  
  if (result && typeof result === 'object') {
    if (Array.isArray(result.data)) {
      data = result.data;
    } else if (Array.isArray(result)) {
      data = result;
    } else if (result.data && Array.isArray(result.data)) {
      data = result.data;
    } else {
      // 如果都不是数组，尝试转换为数组
      data = Array.isArray(result) ? result : [result];
    }
  } else {
    data = [];
  }
  
  console.log('Processed models array:', data); // 调试信息
  return data;
};

// 获取所有模型列表
export const getModels = async (): Promise<AIModel[]> => {
  const response = await fetch(apiUrl('/api/models'), {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取模型列表失败: ${response.statusText}`);
  }

  const result = await response.json();
  return result?.data || result;
};

// 获取当前用户在各供应商下已配置的大模型列表
export const getMyLlms = async (): Promise<LlmServiceProvider[]> => {
  const response = await fetch(apiUrl('/api/model-provider/my_llms'), {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取已配置模型失败: ${response.statusText}`);
  }

  const result = await response.json();
  const data = result?.data ?? result;

  if (Array.isArray(data)) {
    return data;
  }

  // 如果后端返回的是单个对象而不是数组，也做兼容
  return data ? [data] : [];
};

// 启用/禁用模型（对应后端 /api/model/{id}/toggle?enabled=...）
export const toggleModelStatus = async (id: string, enabled: boolean): Promise<boolean> => {
  const params = new URLSearchParams({ enabled: String(enabled) });

  const response = await fetch(apiUrl(`/api/model/${id}/toggle?${params.toString()}`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`更新模型状态失败: ${response.statusText}`);
  }

  const result = await response.json();
  return result?.data ?? result ?? false;
};

// 创建模型
export const createModel = async (data: ModelCreateRequest): Promise<AIModel> => {
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

  const result = await response.json();
  return result?.data || result;
};

// 更新模型
export const updateModel = async (id: string, data: ModelUpdateRequest): Promise<AIModel> => {
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

  const result = await response.json();
  return result?.data || result;
};

// 删除模型
export const deleteModel = async (id: string): Promise<void> => {
  const response = await fetch(apiUrl(`/api/models/${id}`), {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(`删除模型失败: ${response.statusText}`);
  }
};

// 测试模型连接
export const testModel = async (id: string): Promise<{ success: boolean; message: string }> => {
  const response = await fetch(apiUrl(`/api/models/${id}/test`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`测试模型失败: ${response.statusText}`);
  }

  const result = await response.json();
  return result?.data || result;
};

// 删除供应商（已添加的模型）
export const deleteModelProvider = async (providerCode: string): Promise<void> => {
  const response = await fetch(apiUrl(`/api/model-provider/delete/${providerCode}`), {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`删除供应商失败: ${response.statusText}`);
  }
};

// 检测供应商健康状态
export const checkProviderHealth = async (
  providerCode: string,
  apiKey: string,
): Promise<ProviderHealthResponse> => {
  const params = new URLSearchParams({
    providerCode,
    apiKey,
  });

  const response = await fetch(apiUrl(`/api/model-provider/health?${params.toString()}`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`检测供应商健康状态失败: ${response.statusText}`);
  }

  const result = await response.json();
  const data = result?.data || result;
  // 统一返回格式，将 healthy 映射到 success
  return {
    ...data,
    success: data.healthy ?? data.success ?? false,
  };
};

// 检测 OpenAI Compatible 供应商健康状态
export interface OpenAiCompatibleHealthRequest {
  apiUrl: string;
  apiKey: string;
  testModelName: string;
}

export const checkOpenAiCompatibleHealth = async (
  data: OpenAiCompatibleHealthRequest,
): Promise<ProviderHealthResponse> => {
  const response = await fetch(apiUrl('/api/model-provider/openai-compatible/health'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`检测供应商健康状态失败: ${response.statusText}`);
  }

  const result = await response.json();
  const responseData = result?.data || result;
  // 统一返回格式，将 healthy 映射到 success
  return {
    ...responseData,
    success: responseData.healthy ?? responseData.success ?? false,
  };
};

// 更新模型配置（修改最大token等）
export interface ModelConfigUpdateRequest {
  id: string;
  maxToken: number;
  modelName?: string;
}

export const updateModelConfig = async (data: ModelConfigUpdateRequest): Promise<boolean> => {
  const response = await fetch(apiUrl('/api/model-provider/model'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`更新模型配置失败: ${response.statusText}`);
  }

  const result = await response.json();
  if (result?.code !== 200 && result?.code !== 0 && result?.data !== true) {
    throw new Error(result?.msg || result?.message || '更新模型配置失败');
  }
  return result?.data ?? true;
};

// 检测模型健康状态
export interface ModelHealthCheckResult {
  error: null;
  healthy: boolean;
  success: boolean;
  message?: string;
  status?: string;
}

export const checkModelHealth = async (
  providerCode: string,
  modelName: string,
): Promise<ModelHealthCheckResult> => {
  const params = new URLSearchParams({
    providerCode,
    modelName,
  });

  const response = await fetch(apiUrl(`/api/model-provider/model-health?${params.toString()}`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`检测模型健康状态失败: ${response.statusText}`);
  }

  const result = await response.json();
  return result?.data || result;
};

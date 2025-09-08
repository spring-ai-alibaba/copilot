// 提示词管理API接口

export interface PromptTemplate {
  id: string;
  name: string;
  title: string;
  description?: string;
  content: string;
  category: string;
  tags: string[];
  variables: PromptVariable[];
  isPublic: boolean;
  isSystem: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  usageCount: number;
}

export interface PromptVariable {
  name: string;
  type: 'text' | 'number' | 'select' | 'textarea';
  label: string;
  description?: string;
  required: boolean;
  defaultValue?: string;
  options?: string[]; // for select type
}

export interface PromptCreateRequest {
  name: string;
  title: string;
  description?: string;
  content: string;
  category: string;
  tags: string[];
  variables: PromptVariable[];
  isPublic: boolean;
}

export interface PromptUpdateRequest extends Partial<PromptCreateRequest> {}

export interface PromptCategory {
  id: string;
  name: string;
  title: string;
  description?: string;
  promptCount: number;
}

// 模拟数据
const mockCategories: PromptCategory[] = [
  { id: '1', name: 'coding', title: '编程开发', description: '编程相关的提示词', promptCount: 8 },
  { id: '2', name: 'writing', title: '文案写作', description: '写作和文案相关的提示词', promptCount: 12 },
  { id: '3', name: 'analysis', title: '数据分析', description: '数据分析和报告相关的提示词', promptCount: 6 },
  { id: '4', name: 'translation', title: '翻译润色', description: '翻译和语言处理相关的提示词', promptCount: 4 },
  { id: '5', name: 'creative', title: '创意设计', description: '创意和设计相关的提示词', promptCount: 10 }
];

const mockPrompts: PromptTemplate[] = [
  {
    id: '1',
    name: 'code-review',
    title: '代码审查助手',
    description: '帮助审查代码质量、发现潜在问题并提供改进建议',
    content: `请作为一个资深的软件工程师，审查以下代码：

代码语言：{{language}}
代码内容：
\`\`\`{{language}}
{{code}}
\`\`\`

请从以下几个方面进行审查：
1. 代码质量和可读性
2. 性能优化建议
3. 安全性问题
4. 最佳实践建议
5. 潜在的bug或错误

请提供具体的改进建议和修改后的代码示例。`,
    category: 'coding',
    tags: ['代码审查', '质量检查', '最佳实践'],
    variables: [
      {
        name: 'language',
        type: 'select',
        label: '编程语言',
        description: '选择要审查的代码语言',
        required: true,
        options: ['JavaScript', 'Python', 'Java', 'TypeScript', 'Go', 'Rust', 'C++']
      },
      {
        name: 'code',
        type: 'textarea',
        label: '代码内容',
        description: '粘贴需要审查的代码',
        required: true
      }
    ],
    isPublic: true,
    isSystem: true,
    createdBy: 'system',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    usageCount: 156
  },
  {
    id: '2',
    name: 'api-documentation',
    title: 'API文档生成器',
    description: '根据代码自动生成API文档',
    content: `请为以下API接口生成详细的文档：

接口名称：{{apiName}}
请求方法：{{method}}
接口路径：{{path}}
代码实现：
\`\`\`
{{code}}
\`\`\`

请生成包含以下内容的API文档：
1. 接口描述和用途
2. 请求参数说明（包括类型、是否必填、示例值）
3. 响应数据格式
4. 错误码说明
5. 使用示例（curl命令和代码示例）
6. 注意事项

请使用Markdown格式输出。`,
    category: 'coding',
    tags: ['API文档', '接口文档', '自动生成'],
    variables: [
      {
        name: 'apiName',
        type: 'text',
        label: 'API名称',
        description: '接口的名称或功能描述',
        required: true
      },
      {
        name: 'method',
        type: 'select',
        label: '请求方法',
        required: true,
        options: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']
      },
      {
        name: 'path',
        type: 'text',
        label: '接口路径',
        description: '例如：/api/users/{id}',
        required: true
      },
      {
        name: 'code',
        type: 'textarea',
        label: '代码实现',
        description: '接口的实现代码',
        required: true
      }
    ],
    isPublic: true,
    isSystem: true,
    createdBy: 'system',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    usageCount: 89
  },
  {
    id: '3',
    name: 'product-description',
    title: '产品描述文案',
    description: '为产品生成吸引人的营销文案',
    content: `请为以下产品创作一份吸引人的产品描述文案：

产品名称：{{productName}}
产品类型：{{productType}}
目标用户：{{targetAudience}}
主要功能：{{features}}
产品优势：{{advantages}}
价格区间：{{priceRange}}

请生成包含以下内容的产品文案：
1. 吸引人的标题（2-3个选项）
2. 产品核心卖点（3-5个要点）
3. 详细产品描述（200-300字）
4. 用户痛点和解决方案
5. 行动号召语（CTA）

文案风格要求：{{style}}
请确保文案具有说服力，能够激发用户的购买欲望。`,
    category: 'writing',
    tags: ['产品文案', '营销文案', '电商'],
    variables: [
      {
        name: 'productName',
        type: 'text',
        label: '产品名称',
        required: true
      },
      {
        name: 'productType',
        type: 'text',
        label: '产品类型',
        description: '例如：智能手机、护肤品、在线课程等',
        required: true
      },
      {
        name: 'targetAudience',
        type: 'text',
        label: '目标用户',
        description: '例如：25-35岁职场女性',
        required: true
      },
      {
        name: 'features',
        type: 'textarea',
        label: '主要功能',
        description: '列出产品的主要功能特点',
        required: true
      },
      {
        name: 'advantages',
        type: 'textarea',
        label: '产品优势',
        description: '相比竞品的优势',
        required: true
      },
      {
        name: 'priceRange',
        type: 'text',
        label: '价格区间',
        description: '例如：100-500元',
        required: false
      },
      {
        name: 'style',
        type: 'select',
        label: '文案风格',
        required: true,
        defaultValue: '专业可信',
        options: ['专业可信', '活泼有趣', '简洁直接', '情感共鸣', '高端奢华']
      }
    ],
    isPublic: true,
    isSystem: true,
    createdBy: 'system',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    usageCount: 234
  },
  {
    id: '4',
    name: 'data-analysis-report',
    title: '数据分析报告',
    description: '根据数据生成专业的分析报告',
    content: `请基于以下数据生成一份专业的数据分析报告：

分析主题：{{topic}}
数据时间范围：{{timeRange}}
数据来源：{{dataSource}}
关键指标：{{metrics}}

数据内容：
{{data}}

请生成包含以下部分的分析报告：
1. 执行摘要
2. 数据概览
3. 关键发现和趋势分析
4. 深入洞察
5. 结论和建议
6. 附录（如有必要）

分析重点：{{focus}}
报告用途：{{purpose}}

请确保报告逻辑清晰，数据支撑充分，结论客观准确。`,
    category: 'analysis',
    tags: ['数据分析', '报告生成', '商业智能'],
    variables: [
      {
        name: 'topic',
        type: 'text',
        label: '分析主题',
        description: '例如：用户行为分析、销售趋势分析等',
        required: true
      },
      {
        name: 'timeRange',
        type: 'text',
        label: '数据时间范围',
        description: '例如：2024年1月-3月',
        required: true
      },
      {
        name: 'dataSource',
        type: 'text',
        label: '数据来源',
        description: '例如：Google Analytics、内部数据库等',
        required: true
      },
      {
        name: 'metrics',
        type: 'textarea',
        label: '关键指标',
        description: '列出需要分析的关键指标',
        required: true
      },
      {
        name: 'data',
        type: 'textarea',
        label: '数据内容',
        description: '粘贴或描述具体的数据',
        required: true
      },
      {
        name: 'focus',
        type: 'text',
        label: '分析重点',
        description: '希望重点关注的方面',
        required: false
      },
      {
        name: 'purpose',
        type: 'select',
        label: '报告用途',
        required: true,
        options: ['管理层汇报', '客户展示', '内部分析', '决策支持', '绩效评估']
      }
    ],
    isPublic: true,
    isSystem: true,
    createdBy: 'system',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    usageCount: 67
  }
];

const isDevelopmentMode = process.env.NODE_ENV === 'development';
const mockDelay = (ms: number = 1000) => new Promise(resolve => setTimeout(resolve, ms));

// 获取提示词分类
export const getPromptCategories = async (): Promise<PromptCategory[]> => {
  if (isDevelopmentMode) {
    await mockDelay(300);
    return [...mockCategories];
  }

  const response = await fetch(`${API_BASE_URL}/api/prompts/categories`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取提示词分类失败: ${response.statusText}`);
  }

  return response.json();
};

// 获取提示词列表
export const getPrompts = async (category?: string): Promise<PromptTemplate[]> => {
  if (isDevelopmentMode) {
    await mockDelay(500);
    if (category) {
      return mockPrompts.filter(p => p.category === category);
    }
    return [...mockPrompts];
  }

  const url = category 
    ? `${API_BASE_URL}/api/prompts?category=${category}`
    : `${API_BASE_URL}/api/prompts`;

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`获取提示词列表失败: ${response.statusText}`);
  }

  return response.json();
};

// 创建提示词
export const createPrompt = async (data: PromptCreateRequest): Promise<PromptTemplate> => {
  if (isDevelopmentMode) {
    await mockDelay(800);
    const newPrompt: PromptTemplate = {
      id: Date.now().toString(),
      ...data,
      isSystem: false,
      createdBy: 'current_user',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      usageCount: 0
    };
    mockPrompts.push(newPrompt);
    return newPrompt;
  }

  const response = await fetch(`${API_BASE_URL}/api/prompts`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`创建提示词失败: ${response.statusText}`);
  }

  return response.json();
};

// 更新提示词
export const updatePrompt = async (id: string, data: PromptUpdateRequest): Promise<PromptTemplate> => {
  if (isDevelopmentMode) {
    await mockDelay(600);
    const index = mockPrompts.findIndex(p => p.id === id);
    if (index === -1) {
      throw new Error('提示词不存在');
    }
    mockPrompts[index] = {
      ...mockPrompts[index],
      ...data,
      updatedAt: new Date().toISOString()
    };
    return mockPrompts[index];
  }

  const response = await fetch(`${API_BASE_URL}/api/prompts/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`更新提示词失败: ${response.statusText}`);
  }

  return response.json();
};

// 删除提示词
export const deletePrompt = async (id: string): Promise<void> => {
  if (isDevelopmentMode) {
    await mockDelay(400);
    const index = mockPrompts.findIndex(p => p.id === id);
    if (index !== -1) {
      mockPrompts.splice(index, 1);
    }
    return;
  }

  const response = await fetch(`${API_BASE_URL}/api/prompts/${id}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(`删除提示词失败: ${response.statusText}`);
  }
};

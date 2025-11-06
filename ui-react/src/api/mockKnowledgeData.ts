// 模拟数据，用于开发和测试
import {KnowledgeBase, KnowledgeChunk, KnowledgeDocument} from './knowledge';

export const mockKnowledgeBases: KnowledgeBase[] = [
  {
    id: '1',
    key: 'tech-docs',
    name: '技术文档库',
    description: '存储技术相关的文档和资料',
    createdBy: 'admin',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-20T15:30:00Z',
    documentCount: 5,
    chunkCount: 23
  },
  {
    id: '2',
    key: 'product-manual',
    name: '产品手册',
    description: '产品使用说明和操作指南',
    createdBy: 'admin',
    createdAt: '2024-01-10T09:00:00Z',
    updatedAt: '2024-01-18T14:20:00Z',
    documentCount: 3,
    chunkCount: 15
  },
  {
    id: '3',
    key: 'company-policy',
    name: '公司政策',
    description: '公司内部政策和规章制度',
    createdBy: 'hr',
    createdAt: '2024-01-05T08:00:00Z',
    updatedAt: '2024-01-12T16:45:00Z',
    documentCount: 8,
    chunkCount: 42
  }
];

export const mockDocuments: Record<string, KnowledgeDocument[]> = {
  'tech-docs': [
    {
      id: 'doc1',
      fileName: 'Spring Boot 开发指南.pdf',
      fileSize: 2048576, // 2MB
      fileType: 'PDF',
      uploadedBy: 'developer1',
      uploadedAt: '2024-01-15T10:30:00Z',
      processStatus: 'COMPLETED',
      chunkCount: 8
    },
    {
      id: 'doc2',
      fileName: 'React 最佳实践.md',
      fileSize: 512000, // 500KB
      fileType: 'Markdown',
      uploadedBy: 'developer2',
      uploadedAt: '2024-01-16T14:20:00Z',
      processStatus: 'COMPLETED',
      chunkCount: 6
    },
    {
      id: 'doc3',
      fileName: 'API 设计规范.docx',
      fileSize: 1024000, // 1MB
      fileType: 'Word',
      uploadedBy: 'architect',
      uploadedAt: '2024-01-18T09:15:00Z',
      processStatus: 'PROCESSING'
    },
    {
      id: 'doc4',
      fileName: '数据库设计文档.txt',
      fileSize: 256000, // 250KB
      fileType: 'Text',
      uploadedBy: 'dba',
      uploadedAt: '2024-01-19T11:00:00Z',
      processStatus: 'FAILED',
      processStatusRemark: '文档格式不支持，请检查文件内容',
      chunkCount: 0
    },
    {
      id: 'doc5',
      fileName: '部署手册.pdf',
      fileSize: 3072000, // 3MB
      fileType: 'PDF',
      uploadedBy: 'devops',
      uploadedAt: '2024-01-20T15:30:00Z',
      processStatus: 'PENDING',
      chunkCount: 0
    }
  ],
  'product-manual': [
    {
      id: 'doc6',
      fileName: '用户操作手册.pdf',
      fileSize: 1536000, // 1.5MB
      fileType: 'PDF',
      uploadedBy: 'product_manager',
      uploadedAt: '2024-01-12T10:00:00Z',
      processStatus: 'COMPLETED',
      chunkCount: 10
    },
    {
      id: 'doc7',
      fileName: '功能说明书.docx',
      fileSize: 768000, // 750KB
      fileType: 'Word',
      uploadedBy: 'product_manager',
      uploadedAt: '2024-01-14T16:30:00Z',
      processStatus: 'COMPLETED',
      chunkCount: 5
    },
    {
      id: 'doc8',
      fileName: '常见问题解答.md',
      fileSize: 128000, // 125KB
      fileType: 'Markdown',
      uploadedBy: 'support',
      uploadedAt: '2024-01-18T14:20:00Z',
      processStatus: 'COMPLETED',
      chunkCount: 0
    }
  ],
  'company-policy': [
    {
      id: 'doc9',
      fileName: '员工手册.pdf',
      fileSize: 2560000, // 2.5MB
      fileType: 'PDF',
      uploadedBy: 'hr',
      uploadedAt: '2024-01-05T08:30:00Z',
      processStatus: 'COMPLETED',
      chunkCount: 15
    },
    {
      id: 'doc10',
      fileName: '考勤制度.docx',
      fileSize: 384000, // 375KB
      fileType: 'Word',
      uploadedBy: 'hr',
      uploadedAt: '2024-01-08T09:45:00Z',
      processStatus: 'COMPLETED',
      chunkCount: 6
    }
  ]
};

export const mockChunks: Record<string, KnowledgeChunk[]> = {
  'doc1': [
    {
      id: 'chunk1',
      content: 'Spring Boot 是一个基于 Spring 框架的开源 Java 框架，它简化了 Spring 应用程序的创建和部署过程。Spring Boot 提供了自动配置、起步依赖、内嵌服务器等特性，使开发者能够快速构建生产级别的应用程序。',
      metadata: {
        fileName: 'Spring Boot 开发指南.pdf',
        page: 1,
        section: '简介'
      },
      createdAt: '2024-01-15T10:35:00Z',
      chunkIndex: 0,
      totalChunks: 8
    },
    {
      id: 'chunk2',
      content: '要开始使用 Spring Boot，首先需要创建一个新的项目。可以使用 Spring Initializr (https://start.spring.io/) 来快速生成项目骨架。选择所需的依赖项，如 Spring Web、Spring Data JPA、Spring Security 等。',
      metadata: {
        fileName: 'Spring Boot 开发指南.pdf',
        page: 2,
        section: '快速开始'
      },
      createdAt: '2024-01-15T10:35:00Z',
      chunkIndex: 1,
      totalChunks: 8
    },
    {
      id: 'chunk3',
      content: 'Spring Boot 的自动配置是其核心特性之一。它根据类路径中的依赖项自动配置应用程序。例如，如果类路径中有 H2 数据库，Spring Boot 会自动配置内存数据库。这大大减少了配置文件的编写工作。',
      metadata: {
        fileName: 'Spring Boot 开发指南.pdf',
        page: 3,
        section: '自动配置'
      },
      createdAt: '2024-01-15T10:35:00Z',
      chunkIndex: 2,
      totalChunks: 8
    }
  ],
  'doc2': [
    {
      id: 'chunk4',
      content: 'React 是一个用于构建用户界面的 JavaScript 库。它采用组件化的开发方式，使代码更加模块化和可重用。React 的核心概念包括组件、状态、属性和生命周期。',
      metadata: {
        fileName: 'React 最佳实践.md',
        section: 'React 基础'
      },
      createdAt: '2024-01-16T14:25:00Z',
      chunkIndex: 0,
      totalChunks: 6
    },
    {
      id: 'chunk5',
      content: '在 React 开发中，应该遵循一些最佳实践：1. 使用函数组件和 Hooks；2. 保持组件的单一职责；3. 合理使用 useEffect 避免内存泄漏；4. 使用 TypeScript 提高代码质量；5. 编写单元测试确保代码可靠性。',
      metadata: {
        fileName: 'React 最佳实践.md',
        section: '最佳实践'
      },
      createdAt: '2024-01-16T14:25:00Z',
      chunkIndex: 1,
      totalChunks: 6
    }
  ]
};

// 模拟搜索结果
export const mockSearchResults: Record<string, KnowledgeChunk[]> = {
  'Spring': [
    {
      id: 'chunk1',
      content: 'Spring Boot 是一个基于 Spring 框架的开源 Java 框架，它简化了 Spring 应用程序的创建和部署过程。Spring Boot 提供了自动配置、起步依赖、内嵌服务器等特性，使开发者能够快速构建生产级别的应用程序。',
      metadata: {
        fileName: 'Spring Boot 开发指南.pdf',
        page: 1,
        section: '简介'
      },
      createdAt: '2024-01-15T10:35:00Z',
      chunkIndex: 0,
      totalChunks: 8
    },
    {
      id: 'chunk3',
      content: 'Spring Boot 的自动配置是其核心特性之一。它根据类路径中的依赖项自动配置应用程序。例如，如果类路径中有 H2 数据库，Spring Boot 会自动配置内存数据库。这大大减少了配置文件的编写工作。',
      metadata: {
        fileName: 'Spring Boot 开发指南.pdf',
        page: 3,
        section: '自动配置'
      },
      createdAt: '2024-01-15T10:35:00Z',
      chunkIndex: 2,
      totalChunks: 8
    }
  ],
  'React': [
    {
      id: 'chunk4',
      content: 'React 是一个用于构建用户界面的 JavaScript 库。它采用组件化的开发方式，使代码更加模块化和可重用。React 的核心概念包括组件、状态、属性和生命周期。',
      metadata: {
        fileName: 'React 最佳实践.md',
        section: 'React 基础'
      },
      createdAt: '2024-01-16T14:25:00Z',
      chunkIndex: 0,
      totalChunks: 6
    },
    {
      id: 'chunk5',
      content: '在 React 开发中，应该遵循一些最佳实践：1. 使用函数组件和 Hooks；2. 保持组件的单一职责；3. 合理使用 useEffect 避免内存泄漏；4. 使用 TypeScript 提高代码质量；5. 编写单元测试确保代码可靠性。',
      metadata: {
        fileName: 'React 最佳实践.md',
        section: '最佳实践'
      },
      createdAt: '2024-01-16T14:25:00Z',
      chunkIndex: 1,
      totalChunks: 6
    }
  ]
};

// 开发模式标识
export const isDevelopmentMode = process.env.NODE_ENV === 'development';

// 模拟延迟函数
export const mockDelay = (ms: number = 1000) => 
  new Promise(resolve => setTimeout(resolve, ms));

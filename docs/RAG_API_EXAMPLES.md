# RAG 模块 API 使用示例

本文档提供了 RAG 模块各个 API 接口的详细使用示例。

## 1. 向量库配置管理

### 1.1 创建向量库配置

```bash
curl -X POST http://localhost:6039/api/v1/vector-store-configs \
  -H "Content-Type: application/json" \
  -d '{
    "configName": "生产环境Milvus",
    "configKey": "prod_milvus",
    "storeType": "milvus",
    "host": "localhost",
    "port": 19530,
    "username": "root",
    "password": "milvus",
    "databaseName": "production",
    "collectionName": "knowledge_vectors",
    "embeddingDimension": 1536,
    "indexType": "IVF_FLAT",
    "metricType": "COSINE",
    "enabled": true,
    "isDefault": false,
    "description": "生产环境的Milvus向量数据库配置"
  }'
```

### 1.2 测试向量库连接

```bash
curl -X POST http://localhost:6039/api/v1/vector-store-configs/1/test-connection
```

### 1.3 设置默认配置

```bash
curl -X PUT http://localhost:6039/api/v1/vector-store-configs/1/set-default
```

## 2. 知识库管理

### 2.1 创建知识库

```bash
curl -X POST http://localhost:6039/api/v1/knowledge-bases \
  -H "Content-Type: application/json" \
  -d '{
    "kbName": "技术文档知识库",
    "kbKey": "tech_docs_kb",
    "description": "存储技术文档和API文档的知识库",
    "vectorStoreConfigId": 1,
    "embeddingModel": "text-embedding-v3",
    "chunkSize": 500,
    "chunkOverlap": 50,
    "enabled": true,
    "createdBy": "admin"
  }'
```

### 2.2 查询知识库列表

```bash
# 分页查询
curl -X GET "http://localhost:6039/api/v1/knowledge-bases?page=1&size=10&keyword=技术"

# 查询所有启用的知识库
curl -X GET http://localhost:6039/api/v1/knowledge-bases/enabled
```

### 2.3 根据键查询知识库

```bash
curl -X GET http://localhost:6039/api/v1/knowledge-bases/by-key/tech_docs_kb
```

## 3. 文档上传和处理

### 3.1 上传单个文档

```bash
curl -X POST http://localhost:6039/api/v1/rag/tech_docs_kb/upload-file \
  -F "file=@/path/to/document.pdf" \
  -F "uploadedBy=admin"
```

### 3.2 批量上传文档

```bash
curl -X POST http://localhost:6039/api/v1/rag/tech_docs_kb/upload-files \
  -F "files=@/path/to/doc1.pdf" \
  -F "files=@/path/to/doc2.docx" \
  -F "files=@/path/to/doc3.txt" \
  -F "uploadedBy=admin"
```

### 3.3 添加文本内容

```bash
curl -X POST http://localhost:6039/api/v1/rag/tech_docs_kb/add-text \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "content=Spring AI 是一个用于构建 AI 应用程序的框架，它提供了与各种 AI 模型的集成能力。&createdBy=admin"
```

## 4. 智能问答

### 4.1 阻塞式问答

```bash
curl -X POST http://localhost:6039/api/v1/rag/tech_docs_kb/chat \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "query=什么是Spring AI？&topK=5"
```

### 4.2 流式问答

```bash
curl -X POST http://localhost:6039/api/v1/rag/tech_docs_kb/chat-stream \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "query=如何使用RAG功能？&topK=5"
```

### 4.3 带历史消息的问答

```bash
curl -X POST http://localhost:6039/api/v1/rag/tech_docs_kb/chat-with-history \
  -H "Content-Type: application/json" \
  -d '{
    "query": "那么如何集成Milvus？",
    "topK": 5,
    "historyMessages": [
      "用户: 什么是向量数据库？",
      "助手: 向量数据库是专门用于存储和检索高维向量数据的数据库系统..."
    ]
  }' \
  --get --data-urlencode "query=那么如何集成Milvus？" --data-urlencode "topK=5"
```

## 5. 相似性搜索

### 5.1 基础搜索

```bash
curl -X GET "http://localhost:6039/api/v1/rag/tech_docs_kb/search?query=向量数据库&topK=5"
```

### 5.2 高级搜索（带参数）

```bash
curl -X GET "http://localhost:6039/api/v1/rag/tech_docs_kb/search?query=Spring AI集成&topK=10"
```

## 6. 文档管理

### 6.1 删除文档

```bash
curl -X DELETE http://localhost:6039/api/v1/rag/tech_docs_kb/documents/123
```

### 6.2 重新处理文档

```bash
curl -X POST http://localhost:6039/api/v1/rag/tech_docs_kb/documents/123/reprocess
```

### 6.3 清空知识库

```bash
curl -X DELETE http://localhost:6039/api/v1/rag/tech_docs_kb/clear
```

## 7. 统计信息

### 7.1 获取知识库统计

```bash
curl -X GET http://localhost:6039/api/v1/rag/tech_docs_kb/stats
```

响应示例：
```json
{
  "kbKey": "tech_docs_kb",
  "kbName": "技术文档知识库",
  "documentCount": 25,
  "chunkCount": 1250,
  "totalFileSize": 52428800,
  "vectorStoreType": "milvus"
}
```

## 8. JavaScript 前端集成示例

### 8.1 上传文档

```javascript
async function uploadDocument(kbKey, file) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('uploadedBy', 'current_user');
  
  const response = await fetch(`/api/v1/rag/${kbKey}/upload-file`, {
    method: 'POST',
    body: formData
  });
  
  return await response.text();
}
```

### 8.2 智能问答

```javascript
async function askQuestion(kbKey, question, topK = 5) {
  const params = new URLSearchParams({
    query: question,
    topK: topK
  });
  
  const response = await fetch(`/api/v1/rag/${kbKey}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params
  });
  
  return await response.text();
}
```

### 8.3 流式问答

```javascript
async function askQuestionStream(kbKey, question, topK = 5) {
  const params = new URLSearchParams({
    query: question,
    topK: topK
  });
  
  const response = await fetch(`/api/v1/rag/${kbKey}/chat-stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params
  });
  
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    
    const chunk = decoder.decode(value);
    console.log('Received chunk:', chunk);
    // 处理流式数据
  }
}
```

### 8.4 相似性搜索

```javascript
async function searchSimilar(kbKey, query, topK = 5) {
  const params = new URLSearchParams({
    query: query,
    topK: topK
  });
  
  const response = await fetch(`/api/v1/rag/${kbKey}/search?${params}`);
  return await response.json();
}
```

## 9. Python 客户端示例

### 9.1 基础配置

```python
import requests
import json

BASE_URL = "http://localhost:6039/api/v1"

class RAGClient:
    def __init__(self, base_url=BASE_URL):
        self.base_url = base_url
    
    def upload_document(self, kb_key, file_path, uploaded_by="system"):
        url = f"{self.base_url}/rag/{kb_key}/upload-file"
        
        with open(file_path, 'rb') as f:
            files = {'file': f}
            data = {'uploadedBy': uploaded_by}
            response = requests.post(url, files=files, data=data)
        
        return response.text
    
    def ask_question(self, kb_key, question, top_k=5):
        url = f"{self.base_url}/rag/{kb_key}/chat"
        data = {'query': question, 'topK': top_k}
        response = requests.post(url, data=data)
        return response.text
    
    def search_similar(self, kb_key, query, top_k=5):
        url = f"{self.base_url}/rag/{kb_key}/search"
        params = {'query': query, 'topK': top_k}
        response = requests.get(url, params=params)
        return response.json()
```

### 9.2 使用示例

```python
# 创建客户端
client = RAGClient()

# 上传文档
result = client.upload_document("tech_docs_kb", "/path/to/document.pdf")
print(f"Upload result: {result}")

# 智能问答
answer = client.ask_question("tech_docs_kb", "什么是Spring AI？")
print(f"Answer: {answer}")

# 相似性搜索
similar_docs = client.search_similar("tech_docs_kb", "向量数据库")
print(f"Similar documents: {json.dumps(similar_docs, indent=2, ensure_ascii=False)}")
```

## 10. 错误处理

### 10.1 常见错误响应

```json
{
  "error": "知识库不存在: invalid_kb_key",
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400
}
```

### 10.2 错误处理示例

```javascript
async function handleRAGRequest(kbKey, question) {
  try {
    const response = await fetch(`/api/v1/rag/${kbKey}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({ query: question, topK: 5 })
    });
    
    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`RAG request failed: ${errorText}`);
    }
    
    return await response.text();
  } catch (error) {
    console.error('RAG request error:', error);
    return '抱歉，处理您的问题时出现错误。';
  }
}
```

## 11. 性能优化建议

1. **批量操作**: 尽量使用批量上传接口处理多个文档
2. **缓存策略**: 对于相同的查询，可以在客户端实现缓存
3. **流式处理**: 对于长文本生成，使用流式接口提供更好的用户体验
4. **参数调优**: 根据实际需求调整 `topK` 和相似度阈值
5. **异步处理**: 文档上传和处理使用异步方式，避免阻塞用户界面

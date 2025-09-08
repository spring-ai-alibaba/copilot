# RAG 模块部署指南

本指南将帮助您快速部署和使用新的 RAG（检索增强生成）模块。

## 前置条件

### 1. 环境要求
- Java 17+
- MySQL 8.0+
- Milvus 2.3.0+（向量数据库）
- Maven 3.6+

### 2. API Key 准备
- 阿里云通义千问 API Key
- 设置环境变量：`AI_DASHSCOPE_API_KEY=your_api_key`

## 部署步骤

### 1. 数据库初始化

```sql
-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS spring_ai_copilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 执行初始化脚本
-- 执行 scripts/sql/init_model_config.sql
-- 执行 scripts/sql/init_rag_tables.sql
```

### 2. Milvus 部署

使用 Docker Compose 部署 Milvus：

```yaml
# docker-compose.yml
version: '3.5'

services:
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.5
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - ${DOCKER_VOLUME_DIRECTORY:-.}/volumes/etcd:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 30s
      timeout: 20s
      retries: 3

  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    ports:
      - "9001:9001"
      - "9000:9000"
    volumes:
      - ${DOCKER_VOLUME_DIRECTORY:-.}/volumes/minio:/minio_data
    command: minio server /minio_data --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  milvus:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.3.0
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - ${DOCKER_VOLUME_DIRECTORY:-.}/volumes/milvus:/var/lib/milvus
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      start_period: 90s
      timeout: 20s
      retries: 3
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - "etcd"
      - "minio"

networks:
  default:
    name: milvus
```

启动 Milvus：
```bash
docker-compose up -d
```

### 3. 应用配置

更新 `application.yml` 配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/spring_ai_copilot?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}

copilot:
  rag:
    file-upload:
      storage-root: ./data/rag/files
      max-file-size: 10485760  # 10MB
    document-processing:
      default-chunk-size: 500
      default-chunk-overlap: 50
    vectorization:
      default-embedding-model: text-embedding-v3
      embedding-dimension: 1536
    retrieval:
      default-top-k: 5
      similarity-threshold: 0.7
```

### 4. 启动应用

```bash
# 编译项目
mvn clean compile

# 启动应用
mvn spring-boot:run -pl copilot-admin
```

## 使用示例

### 1. 创建知识库

```bash
curl -X POST http://localhost:6039/api/v1/knowledge-bases \
  -H "Content-Type: application/json" \
  -d '{
    "kbName": "Spring AI 知识库",
    "kbKey": "spring_ai_kb",
    "description": "Spring AI 相关文档知识库",
    "vectorStoreConfigId": 1,
    "chunkSize": 500,
    "chunkOverlap": 50
  }'
```

### 2. 上传文档

```bash
curl -X POST http://localhost:6039/api/v1/rag/spring_ai_kb/upload-file \
  -F "file=@/path/to/your/document.pdf" \
  -F "uploadedBy=admin"
```

### 3. 智能问答

```bash
# 阻塞式问答
curl -X POST http://localhost:6039/api/v1/rag/spring_ai_kb/chat \
  -d "query=什么是Spring AI?&topK=5"

# 流式问答
curl -X POST http://localhost:6039/api/v1/rag/spring_ai_kb/chat-stream \
  -d "query=如何使用RAG功能?&topK=5"
```

### 4. 相似性搜索

```bash
curl -X GET "http://localhost:6039/api/v1/rag/spring_ai_kb/search?query=向量数据库&topK=5"
```

## 监控和维护

### 1. 健康检查

```bash
# 应用健康检查
curl http://localhost:6039/actuator/health

# Milvus 健康检查
curl http://localhost:9091/healthz
```

### 2. 日志监控

```yaml
# 在 application.yml 中配置日志级别
logging:
  level:
    com.alibaba.cloud.ai.copilot.rag: DEBUG
    org.springframework.ai: INFO
```

### 3. 性能优化

- 根据文档类型调整分块大小
- 优化向量维度和索引类型
- 配置合适的相似度阈值
- 启用批量处理提高效率

## 故障排除

### 常见问题

1. **Milvus 连接失败**
   - 检查 Milvus 服务状态
   - 验证连接配置
   - 确认防火墙设置

2. **文档上传失败**
   - 检查文件大小限制
   - 验证文件格式支持
   - 确认存储目录权限

3. **向量化失败**
   - 检查 API Key 配置
   - 验证网络连接
   - 查看错误日志

4. **检索结果不准确**
   - 调整相似度阈值
   - 优化分块策略
   - 考虑启用重排序

### 性能调优

1. **数据库优化**
   - 为常用查询字段添加索引
   - 定期清理无用数据
   - 配置连接池参数

2. **向量库优化**
   - 选择合适的索引类型
   - 调整向量维度
   - 配置内存和CPU资源

3. **应用优化**
   - 启用异步处理
   - 配置合理的线程池
   - 使用缓存减少重复计算

## 扩展功能

### 1. 支持更多向量数据库

可以通过实现 `VectorStoreFactory` 来支持 Elasticsearch、PgVector 等其他向量数据库。

### 2. 自定义文档处理器

扩展 `DocumentProcessor` 来支持更多文档格式或自定义处理逻辑。

### 3. 集成重排序模型

可以集成 BGE、ColBERT 等重排序模型来提高检索精度。

## 安全考虑

1. **API 访问控制**
   - 实现认证和授权机制
   - 限制 API 调用频率
   - 记录操作审计日志

2. **数据安全**
   - 加密敏感数据
   - 定期备份数据
   - 控制文件访问权限

3. **网络安全**
   - 使用 HTTPS 协议
   - 配置防火墙规则
   - 定期更新依赖版本

# 知识库管理功能

## 功能概述

知识库管理功能允许用户创建、管理和搜索知识库，支持文档上传、处理和预览。

## 主要功能

### 1. 知识库管理
- 创建知识库：支持设置知识库标识、名称和描述
- 删除知识库：支持删除整个知识库及其所有文档
- 查看知识库列表：显示知识库的基本信息和统计数据

### 2. 文档管理
- 文档上传：支持 PDF、TXT、DOC、DOCX、MD 格式
- 文档删除：删除指定文档及其分块数据
- 文档重新处理：重新解析和分块文档
- 文档状态跟踪：显示文档处理状态（待处理、处理中、已完成、失败）

### 3. 文档预览
- 分块预览：查看文档的所有分块内容
- 元数据查看：显示分块的元数据信息
- 搜索功能：在知识库中搜索相关内容
- 关键词高亮：搜索结果中高亮显示关键词

## 组件结构

```
KnowledgeSettings/
├── index.tsx                    # 主组件
├── CreateKnowledgeBaseModal.tsx # 创建知识库模态框
├── KnowledgePreview.tsx         # 文档预览组件
├── KnowledgeSettings.css        # 样式文件
└── README.md                    # 说明文档
```

## API 接口

### 知识库接口
- `GET /api/rag/knowledge-bases` - 获取知识库列表
- `POST /api/rag/knowledge-bases` - 创建知识库
- `DELETE /api/rag/knowledge-bases/{kbKey}` - 删除知识库

### 文档接口
- `GET /api/rag/knowledge-bases/{kbKey}/documents` - 获取文档列表
- `POST /api/rag/knowledge-bases/{kbKey}/upload` - 上传文档
- `DELETE /api/rag/knowledge-bases/{kbKey}/documents/{documentId}` - 删除文档
- `POST /api/rag/knowledge-bases/{kbKey}/documents/{documentId}/reprocess` - 重新处理文档

### 分块和搜索接口
- `GET /api/rag/knowledge-bases/{kbKey}/documents/{documentId}/chunks` - 获取文档分块
- `POST /api/rag/knowledge-bases/{kbKey}/search` - 搜索知识库

## 使用方法

1. 在设置页面中选择"知识库"标签页
2. 点击"创建知识库"按钮创建新的知识库
3. 选择知识库后，可以上传文档
4. 点击文档的预览按钮查看文档内容和搜索功能

## 配置说明

### 环境变量
- `REACT_APP_API_BASE_URL`: 后端API地址，默认为 `http://localhost:8080`

### 文件限制
- 支持的文件格式：PDF、TXT、DOC、DOCX、MD
- 最大文件大小：10MB

### 知识库标识规则
- 只能包含字母、数字、下划线和连字符
- 长度限制：2-50个字符
- 必须唯一

## 样式特性

- 响应式设计：支持移动端和桌面端
- 暗色主题：自动适配暗色主题
- 动画效果：平滑的过渡动画
- 状态指示：清晰的状态标识和反馈

## 国际化支持

支持中英文双语，翻译文件位于：
- `src/locale/zh.json` - 中文翻译
- `src/locale/en.json` - 英文翻译

## 注意事项

1. 确保后端API服务正常运行
2. 检查网络连接和API地址配置
3. 文档上传前会进行格式和大小验证
4. 删除操作不可恢复，请谨慎操作
5. 大文件上传可能需要较长时间，请耐心等待

## 故障排除

### 常见问题

1. **无法加载知识库列表**
   - 检查API服务是否运行
   - 确认API地址配置正确
   - 查看浏览器控制台错误信息

2. **文档上传失败**
   - 检查文件格式是否支持
   - 确认文件大小不超过限制
   - 检查网络连接状态

3. **搜索功能无响应**
   - 确认知识库中有已处理的文档
   - 检查搜索关键词是否有效
   - 查看后端日志排查问题

4. **样式显示异常**
   - 清除浏览器缓存
   - 检查CSS文件是否正确加载
   - 确认主题设置正确

## 开发说明

### 添加新功能
1. 在API文件中添加新的接口定义
2. 在组件中实现相应的UI和逻辑
3. 添加相应的翻译文本
4. 更新样式文件
5. 编写测试用例

### 自定义样式
可以通过修改 `KnowledgeSettings.css` 文件来自定义样式，支持：
- 颜色主题定制
- 布局调整
- 动画效果修改
- 响应式断点调整

### 扩展API
如需添加新的API接口，请在 `src/api/knowledge.ts` 中添加相应的函数，并确保：
- 正确的错误处理
- 适当的类型定义
- 统一的响应格式
- 完整的文档注释

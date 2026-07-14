# 版本更新日志

## [v1.3.0] - 2025-07-XX

### 新增功能
- **会话置顶**：`Conversation` 实体新增 `pinned` 字段，`ChatController` 新增 `PUT /api/chat/conversations/{id}/pin` 端点，前端会话列表支持置顶排序、📌图标标识
- **消息复制**：AI 回答底部新增📋复制按钮，一键复制消息内容到剪贴板
- **文档状态筛选**：`KnowledgeController` GET /documents 新增 `status` 查询参数，前端知识库管理页添加状态下拉筛选
- **文档描述**：`Document` 实体新增 `description` 字段，编辑文档弹窗支持填写描述
- **系统仪表板**：新建 `AdminDashboardView` 页面，展示用户/文档/分块/会话/消息全局统计卡片
- **操作日志**：新建 `OperationLog` 实体 + `OperationLogService`（异步记录），`AdminController` 新增 `GET /api/admin/logs` 端点，仪表板页展示操作日志分页表格
- **系统统计 API**：`AdminController` 新增 `GET /api/admin/stats` 端点，返回全局统计数据

### 数据库迁移
- **V5__doc_chat_system_enhancement.sql**：`conversations` 表新增 `pinned` 列；`documents` 表新增 `description` 列；创建 `operation_logs` 表 + 索引

### 优化
- 会话列表查询改为置顶优先排序（`pinned DESC, updatedAt DESC`）
- 前端管理员用户菜单新增“系统管理”入口
- 知识库编辑弹窗新增文档描述字段

---

## [v1.2.0] - 2025-01-XX

### Bug 修复
- **修复 SSE URL 硬编码**：`frontend/src/api/chat.ts` 中 `sendMessage` 的 fetch URL 从硬编码 `http://localhost:8080/api` 改为环境变量 `VITE_API_BASE_URL`，Docker/生产环境不再失效
- **修复重复向量化调用**：`RAGService.preparePrompt()` 新增重载方法接受已计算向量，`ChatController` 传入已有向量避免同一问题被向量化 2 次
- **补充 Document 实体 aiMode 字段**：`Document.java` 添加 `aiMode` 字段映射，与 V2 数据库迁移保持一致

### 新增功能
- **Logout 退出登录**：新增 `TokenBlacklistService`（内存级 ConcurrentHashMap + 定时清理），`JwtAuthFilter` 增加黑名单检查，前端 `auth store` 先调后端再清本地
- **用户资料编辑**：`User` 实体新增 `nickname`、`avatar` 字段，`UserController` 新增 `PUT /api/user/profile` 端点，前端 `ProfileView` 添加编辑表单
- **管理员用户管理**：新建 `AdminController`，提供用户列表、禁用/启用、角色修改接口（`/api/admin/users/**`），前端新建 `AdminUsersView` 管理页面
- **消息反馈（点赞/踩）**：`Message` 实体新增 `feedback` 字段，`ChatController` 新增 `PUT /api/chat/messages/{id}/feedback` 端点，前端 AI 回答底部添加反馈按钮
- **对话搜索**：`ConversationRepository` 新增基于 PostgreSQL 全文搜索的 `searchByKeyword` 查询，`ChatController` 新增 `GET /api/chat/conversations/search` 端点，前端会话列表添加搜索框
- **对话导出 Markdown**：`ConversationService` 新增 `exportConversation` 方法将会话格式化为 Markdown，`ChatController` 新增 `GET /api/chat/conversations/{id}/export` 端点，前端会话菜单添加导出选项

### 数据库迁移
- **V4__user_profile_and_feedback.sql**：`users` 表新增 `nickname`、`avatar` 字段；`messages` 表新增 `feedback` 字段；`conversations` 表新增 `message_count` 字段；创建消息内容全文搜索 gin 索引

### 优化
- RAG 向量化性能优化：避免同一问题重复调用 Embedding 服务
- `JwtAuthFilter` 增加 Token 黑名单校验，支持主动失效
- `SecurityConfig` 细化权限配置，管理员接口独立控制

---

## [v1.1.0] - 2025-01-XX

### 新增功能
- **文档标签系统**：V3 迁移添加 `ai_mode` 列，支持标注文档所属 AI 模式
- **RAG 过程可视化**：前端实时展示向量化、语义搜索、提示词构建、AI 生成四个步骤
- **双模式切换**：离线 Ollama ↔ 在线 DashScope 一键切换

### 基础设施
- Docker Compose 一键部署（PostgreSQL + 后端 + 前端 + Nginx）
- Flyway 数据库版本管理
- 敏感配置分离保护（.gitignore 排除 application-secrets.yml）

---

## [v1.0.0] - 2025-01-XX

### 核心功能
- 知识库文档管理（上传/解析/分块/向量化）
- RAG 智能问答（语义搜索 + Prompt 增强 + AI 生成 + 引用来源）
- SSE 流式打字机效果
- 多用户多会话管理
- JWT 双 Token 认证（access 30min + refresh 7days）
- Admin/User 权限分离

### 技术栈
- Spring Boot 3.4 + Spring Security + Spring AI
- Vue 3 + Element Plus + TypeScript + Vite + Pinia
- PostgreSQL 17 + pgvector 0.8.4
- Ollama (Qwen2.5:7b + nomic-embed-text) / DashScope (qwen-plus + text-embedding-v2)
- Apache Tika 2.9 文档解析

# RAG 企业级知识库问答系统

> 毕设项目 | 基于 Spring Boot + Vue 3 的 RAG 知识库问答系统，支持本地离线 + 云端在线双模式

## 项目简介

面向电商平台商品信息的 RAG（Retrieval-Augmented Generation，检索增强生成）企业级知识库问答系统。支持用户上传商品相关文档，系统自动解析、向量化存储，用户在浏览器中提问时，AI 会基于知识库内容生成带引用来源的精准回答。

**核心特点**：
- 🏠 **离线模式**：完全本地运行，无需联网，所有 AI 模型（Ollama + Qwen2.5）均在本地执行
- ☁️ **在线模式**：对接阿里云百炼 DashScope API，使用云端大模型，效果更强大
- 🔄 **一键切换**：前端界面按钮实时切换离线/在线模式，无需重启服务

## 技术栈

| 层面 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 3.4 + Spring Security | JWT 无状态认证 + 角色权限控制 |
| AI 集成 | Spring AI 1.0.0-M6 | 统一 AI 调用抽象层（离线 + 在线） |
| 离线 AI | Ollama + Qwen2.5:7b + nomic-embed-text | 本地运行，完全离线 |
| 在线 AI | 阿里云百炼 DashScope（qwen-plus + text-embedding-v2） | 云端 API 调用 |
| 数据库 | PostgreSQL 17 + pgvector 0.8.4 | 业务数据 + 向量存储一体化 |
| 前端 | Vue 3 + Element Plus + TypeScript | 现代化 UI 框架 |
| 文档解析 | Apache Tika 2.9 | 支持 PDF/Word/Excel/Markdown/TXT |
| 构建工具 | Maven + Vite | 后端 Maven，前端 Vite |
| 硬件适配 | Apple M1 + 16GB 统一内存 | 本地运行 7B 模型 |

## 功能特性

### 核心功能
- **知识库管理**（仅管理员）：上传文档 → 自动解析 → 智能分块 → 向量化存储
- **RAG 智能问答**：语义搜索 + Prompt 增强 + AI 生成 + 引用来源标注
- **双模式切换**：离线 Ollama ↔ 在线 DashScope，前端按钮一键切换
- **流式打字机效果**：SSE 逐字推送，打字机效果
- **多用户多会话**：独立会话管理，历史记录持久化
- **JWT 认证**：access_token + refresh_token 双 Token 机制
- **权限分离**：Admin（知识库管理 + 问答）/ User（仅问答）

### 用户体系
- **退出登录**：Token 黑名单机制，主动失效已登录 Token
- **用户资料编辑**：昵称、头像设置
- **管理员用户管理**：用户列表、禁用/启用、角色切换
- **系统仪表板**：全局统计卡片（用户/文档/分块/会话/消息） + 操作日志分页
- **操作日志审计**：异步记录用户关键操作，支持分页查询
- **消息反馈**：AI 回答点赞/踩，支持取消反馈
- **消息复制**：一键复制 AI 回答内容到剪贴板
- **对话搜索**：基于 PostgreSQL 全文搜索按消息内容查找会话
- **对话导出**：将会话内容导出为 Markdown 文件下载
- **会话置顶**：重要会话置顶显示，📌图标标识

### 性能优化
- pgvector IVFFlat 近似最近邻索引
- 批量 Embedding 调用（16 条/批）
- 异步文档处理（上传即返回）
- 前端路由懒加载 + KeepAlive 缓存
- 中文优化文本切分（段落边界 + 滑动窗口重叠）

### 附加功能
- 多种文档格式支持（PDF / Word / Excel / Markdown / TXT）
- Markdown 渲染 + 代码语法高亮
- Token 自动续期（无感刷新）
- 知识库统计看板
- 响应式布局
- 敏感配置分离保护（API Key 不泄露）

## 系统架构

```
浏览器 (Vue 3 + Element Plus) :5173
    │  HTTP/REST + JWT
    ▼
Spring Boot 3 后端 :8080
    ├── DynamicAiProvider（运行时切换）
    │   ├── OllamaAiProvider（离线模式）
    │   └── DashScopeAiProvider（在线模式）
    ├── Controller (Auth/User/Knowledge/Chat/AiMode)
    ├── Service (RAG/Chat/Document/Embedding)
    ├── Repository (JPA + pgvector 向量搜索)
    │
    ├── JDBC → PostgreSQL + pgvector :5432
    ├── HTTP → Ollama :11434（离线模式）
    └── HTTP → DashScope API（在线模式）
```

## 快速开始

### 🐳 Docker 一键部署（推荐）

```bash
# 前提：安装 Docker Desktop、Ollama 本地已运行并下载模型

# 1. 进入项目目录
cd rag-knowledge-qa-system

# 2. （可选）配置环境变量
cp .env.example .env

# 3. 一键启动
docker compose up -d

# 4. 浏览器访问
open http://localhost:5173

# 5. 停止
docker compose down
```

> Docker 会启动 PostgreSQL + 后端 + 前端，Ollama 继续在本地运行。数据通过 volume 持久化，重启不会丢失。

### 手动安装（开发调试用）

### 环境要求
- Java 17+
- Maven 3.8+
- Node.js 20+
- PostgreSQL 17 + pgvector 扩展
- Ollama（仅离线模式需要）

### 安装步骤

```bash
# 1. 安装 PostgreSQL 和 pgvector
brew install postgresql@17 pgvector
brew services start postgresql@17

# 2. 创建数据库
psql -U $(whoami) -d postgres -c "CREATE USER ragkb WITH PASSWORD 'ragkb123';"
psql -U $(whoami) -d postgres -c "CREATE DATABASE ragkb OWNER ragkb;"
psql -U $(whoami) -d ragkb -c "CREATE EXTENSION vector;"

# 3. 安装 Ollama 并下载模型（离线模式）
brew install ollama
ollama serve
ollama pull qwen2.5:7b      # 对话模型 (~4.7GB)
ollama pull bge-m3           # 向量模型 (~1.2GB)

# 4. 配置私密信息
cp backend/src/main/resources/application-secrets.example.yml \
   backend/src/main/resources/application-secrets.yml
# 编辑 application-secrets.yml，填入你的数据库密码和 API Key
# 在线模式需要: https://dashscope.aliyun.com/ 申请 API Key

# 5. 启动后端 (端口 8080)
cd backend
mvn spring-boot:run

# 6. 启动前端 (端口 5173)
cd frontend
npm install
npm run dev

# 7. 浏览器访问
open http://localhost:5173
```

### 默认账号
| 角色 | 用户名 | 密码 | 权限 |
|------|--------|------|------|
| 管理员 | admin | 123456 | 知识库管理 + 问答 |
| 普通用户 | 自行注册 | — | 仅问答 |

### 模式切换
- 前端界面左上角有模式指示灯
- 🟢 **离线模式**（Ollama 本地）：适合无网络环境，完全免费
- 🔵 **在线模式**（DashScope 云端）：效果更强，需要 API Key
- 点击指示灯即可实时切换，无需重启服务

## 项目结构

```
rag-knowledge-qa-system/
├── docker-compose.yml                    # Docker 一键部署编排
├── .env.example                          # Docker 环境变量模板
├── backend/                              # Spring Boot 3 后端
│   ├── Dockerfile                        # 后端容器镜像
│   ├── pom.xml                           # Maven 依赖配置
│   └── src/main/
│       ├── java/com/example/ragkb/
│       │   ├── config/                   # Security / JWT / CORS / 异步 / AI模式
│       │   │   ├── SecurityConfig.java       # Spring Security 权限配置
│       │   │   ├── JwtTokenProvider.java     # JWT 生成和验证
│       │   │   ├── JwtAuthFilter.java        # JWT 认证过滤器
│       │   │   ├── WebConfig.java            # CORS 跨域配置
│       │   │   ├── AsyncConfig.java          # 异步任务线程池
│       │   │   ├── AiModeConfig.java         # 在线模式 Bean 配置
│       │   │   └── DataInitializer.java      # 初始 admin 账号创建
│       │   ├── controller/               # REST API 控制器
│       │   │   ├── AuthController.java        # 登录/注册/刷新Token/退出
│       │   │   ├── UserController.java        # 用户信息/修改密码/编辑资料
│       │   │   ├── AdminController.java       # 管理员用户管理/系统统计/操作日志
│       │   │   ├── KnowledgeController.java   # 知识库文档管理
│       │   │   ├── ChatController.java        # 问答 SSE 流/会话管理/搜索/导出/置顶
│       │   │   └── AiModeController.java      # AI 模式查询/切换
│       │   ├── service/                  # 业务逻辑层
│       │   │   ├── AiProvider.java            # AI 提供者接口
│       │   │   ├── OllamaAiProvider.java      # Ollama 离线实现
│       │   │   ├── DashScopeAiProvider.java   # DashScope 在线实现
│       │   │   ├── DynamicAiProvider.java     # 运行时动态切换
│       │   │   ├── RAGService.java            # RAG 核心编排
│       │   │   ├── EmbeddingService.java      # 向量化服务
│       │   │   ├── DocumentService.java       # 文档处理服务
│       │   │   ├── ConversationService.java   # 会话管理服务
│       │   │   ├── AuthService.java           # 认证服务
│       │   │   ├── UserService.java           # 用户服务
│       │   │   ├── OperationLogService.java   # 操作日志异步记录服务
│       │   │   └── TokenBlacklistService.java # Token 黑名单服务
│       │   ├── repository/               # 数据访问层
│       │   │   ├── UserRepository.java
│       │   │   ├── DocumentRepository.java
│       │   │   ├── ChunkRepository.java
│       │   │   ├── ChunkEmbeddingRepository.java  # 向量搜索原生SQL
│       │   │   ├── ConversationRepository.java
│       │   │   ├── MessageRepository.java
│       │   │   └── OperationLogRepository.java    # 操作日志查询
│       │   ├── model/
│       │   │   ├── entity/               # 实体类（7 张表）
│       │   │   ├── dto/                  # 数据传输对象
│       │   │   └── enums/                # 枚举
│       │   ├── exception/                # 全局异常处理
│       │   └── util/                     # 文本切分工具
│       └── resources/
│           ├── application.yml           # 主配置（公开）
│           ├── application-secrets.yml   # 私密配置（gitignore）
│           ├── application-secrets.example.yml  # 私密配置模板
│           └── db/migration/             # Flyway 数据库迁移
│               ├── V1__init_schema.sql       # 基础表结构
│               ├── V2__online_embedding.sql   # 在线模式向量表
│               ├── V3__document_tags.sql      # 文档标签
│               ├── V4__user_profile_and_feedback.sql  # 用户资料+反馈
│               └── V5__doc_chat_system_enhancement.sql  # 置顶+描述+操作日志
├── frontend/                             # Vue 3 前端
│   ├── Dockerfile                        # 前端容器镜像
│   ├── nginx.conf                        # Nginx 反向代理配置
│   └── src/
│       ├── api/                          # Axios 封装 + API 模块
│       │   ├── auth.ts                   # 认证 API
│       │   ├── chat.ts                   # 问答 API
│       │   ├── knowledge.ts              # 知识库 API
│       │   ├── user.ts                   # 用户 API
│       │   ├── admin.ts                  # 管理员 API
│       │   └── aimode.ts                 # AI 模式 API
│       ├── router/                       # 路由配置 + 守卫
│       ├── stores/                       # Pinia 状态管理
│       ├── views/                        # 页面
│       │   ├── LoginView.vue             # 登录页
│       │   ├── RegisterView.vue          # 注册页
│       │   ├── ChatView.vue              # 问答主页（含模式切换/搜索/反馈/置顶/复制）
│       │   ├── ProfileView.vue           # 个人中心（含资料编辑）
│       │   ├── AdminKnowledgeView.vue    # 知识库管理（含状态筛选）
│       │   ├── AdminUsersView.vue        # 用户管理（仅管理员）
│       │   ├── AdminDashboardView.vue    # 系统仪表板（统计+操作日志）
│       │   └── NotFoundView.vue          # 404 页面
│       ├── components/                   # 公共组件
│       └── styles/                       # 全局样式 + Element Plus 主题
├── data/documents/                       # 上传文档存储（自动创建）
├── CHANGELOG.md                          # 版本更新日志
├── CLAUDE.md                             # AI 开发指南
├── README.md                             # 本文件
└── 项目说明.md                            # 详细技术说明文档
```

## 数据库表设计

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| users | 用户表 | username, password_hash(BCrypt), role(ADMIN/USER), nickname, avatar |
| documents | 知识库文档 | title, file_type, status(PENDING→PROCESSING→COMPLETED), ai_mode |
| chunks | 文档分块 | content, chunk_index (500字符/块，100字符重叠) |
| chunk_embeddings | 离线向量表(pgvector) | embedding vector(768), IVFFlat 索引 |
| chunk_embeddings_online | 在线向量表(pgvector) | embedding vector(1536), IVFFlat 索引 |
| conversations | 会话表 | user_id, title, message_count, pinned |
| messages | 消息表 | role(USER/ASSISTANT), content, references_data(TEXT), feedback(like/dislike) |
| operation_logs | 操作日志表 | user_id, action, target_type, target_id, detail, ip_address |

## API 接口概览

| 模块 | 端点 | 权限 | 说明 |
|------|------|------|------|
| 认证 | `POST /api/auth/login` | 匿名 | 用户登录 |
| 认证 | `POST /api/auth/register` | 匿名 | 用户注册 |
| 认证 | `POST /api/auth/refresh` | 匿名 | 刷新 Token |
| 认证 | `POST /api/auth/logout` | USER+ | 退出登录（Token 失效） |
| 用户 | `GET /api/user/me` | USER+ | 当前用户信息 |
| 用户 | `PUT /api/user/password` | USER+ | 修改密码 |
| 用户 | `PUT /api/user/profile` | USER+ | 编辑资料（昵称/头像） |
| 管理员 | `GET /api/admin/users` | ADMIN | 用户列表 |
| 管理员 | `PUT /api/admin/users/{id}/toggle` | ADMIN | 禁用/启用用户 |
| 管理员 | `PUT /api/admin/users/{id}/role` | ADMIN | 修改用户角色 |
| 管理员 | `GET /api/admin/stats` | ADMIN | 系统全局统计 |
| 管理员 | `GET /api/admin/logs` | ADMIN | 操作日志（分页） |
| 知识库 | `POST /api/knowledge/documents/upload` | ADMIN | 上传文档 |
| 知识库 | `GET /api/knowledge/documents` | ADMIN | 文档列表 |
| 知识库 | `DELETE /api/knowledge/documents/{id}` | ADMIN | 删除文档 |
| 知识库 | `GET /api/knowledge/stats` | ADMIN | 统计信息 |
| 问答 | `POST /api/chat/send` | USER+ | 发送问题（SSE流式） |
| 问答 | `GET /api/chat/conversations` | USER+ | 会话列表 |
| 问答 | `DELETE /api/chat/conversations/{id}` | USER+ | 删除会话 |
| 问答 | `PUT /api/chat/messages/{id}/feedback` | USER+ | 消息反馈（点赞/踩） |
| 问答 | `GET /api/chat/conversations/search` | USER+ | 搜索会话 |
| 问答 | `GET /api/chat/conversations/{id}/export` | USER+ | 导出 Markdown |
| 问答 | `PUT /api/chat/conversations/{id}/pin` | USER+ | 会话置顶/取消置顶 |
| AI模式 | `GET /api/ai-mode` | USER+ | 查询当前模式 |
| AI模式 | `POST /api/ai-mode/switch` | USER+ | 切换离线/在线 |

## RAG 工作流程

```
文档入库流程：
上传 → Tika 解析文本 → 中文友好切分(500字/块,100字重叠)
→ Embedding 向量化(离线768维/在线1536维) → 写入 pgvector 向量表

问答流程：
提问 → 问题向量化 → pgvector 余弦相似度搜索 Top-5
→ System Prompt + 检索结果 + 历史对话(3轮) + 用户问题
→ LLM 流式生成 → SSE 逐字推送前端 → 打字机效果 + 引用标注
```

## 代码仓库安全与提交流程

本仓库已配置为**同时推送到 Gitee 与 GitHub**：`git push origin master` 会通过双 push-url 自动同步两个平台（`github` 远程作为单推 GitHub 的备用）。

### 敏感信息保护（.gitignore 规则）

`.gitignore` 已排除以下**严禁提交**的文件/目录，提交前务必确认它们永远不会进入 Git 历史：

| 忽略规则 | 排除原因 |
|----------|----------|
| `.env`、`application-secrets.yml` | 数据库密码、DashScope API Key、JWT 签名密钥等机密配置 |
| `.claude/` | 本地 AI 编码工具缓存与配置 |
| `.workbuddy/` | 本地 Agent 记忆（含个人偏好、项目私记），泄露即个人信息外泄 |
| `.qoder/` | 本地 IDE/AI 工具目录，含本地状态 |

> ⚠️ **切勿**使用 `git add -f` 强制添加上述文件。每次 `git add -A` 后，建议先 `git status` 扫一眼，确认这些目录/文件**没有**出现在待提交列表中。
>
> 这两行（`.workbuddy/`、`.qoder/`）是后续为防本地 Agent 目录泄露而补的规则——这类目录默认不含项目代码，纯属本地状态。

## License

MIT

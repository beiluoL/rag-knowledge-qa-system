# RAG 企业级电商知识库问答系统

> 毕设项目 | 基于 Spring Boot + Vue 3 + Ollama 的本地离线 RAG 知识库问答系统

## 项目简介

面向电商平台商品信息的 RAG（Retrieval-Augmented Generation，检索增强生成）企业级知识库问答系统。支持用户上传商品相关文档，系统自动解析、向量化存储，用户在浏览器中提问时，AI 会基于知识库内容生成带引用来源的精准回答。

**核心特点**：完全离线运行，无需联网调用外部 API，所有 AI 模型均在本地执行。

## 技术栈

| 层面 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 3.4 + Spring Security | JWT 无状态认证 + 角色权限控制 |
| AI 集成 | Spring AI 1.0.0-M6 + Ollama | 本地 AI 模型调用 |
| 数据库 | PostgreSQL 17 + pgvector 0.8.4 | 业务数据 + 向量存储一体化 |
| 对话模型 | Qwen2.5:7b（通义千问） | 中文电商问答 |
| 向量模型 | bge-m3（智源 BGE） | 1024 维中文语义向量 |
| 前端 | Vue 3 + Element Plus + TypeScript | 现代化 UI 框架 |
| 文档解析 | Apache Tika 2.9 | 支持 PDF/Word/Excel/Markdown/TXT |
| 硬件适配 | Apple M1 + 16GB 统一内存 | 本地运行 7B 模型 |

## 功能特性

### 核心功能
- **知识库管理**（仅管理员）：上传文档 → 自动解析 → 智能分块 → 向量化存储
- **RAG 智能问答**：语义搜索 + Prompt 增强 + AI 生成 + 引用来源标注
- **流式打字机效果**：SSE 逐字推送，0.5 秒首字延迟
- **多用户多会话**：独立会话管理，历史记录持久化
- **JWT 认证**：access_token + refresh_token 双 Token 机制
- **权限分离**：Admin（知识库管理 + 问答）/ User（仅问答）

### 性能优化
- pgvector IVFFlat 近似最近邻索引
- 批量 Embedding 调用（16 条/批）
- 异步文档处理（上传即返回）
- 前端路由懒加载 + KeepAlive 缓存
- 中文优化文本切分（段落边界 + 滑动窗口重叠）

### 附加功能
- 多种文档格式支持（PDF / Word / Excel / Markdown / TXT / CSV）
- Markdown 渲染 + 代码语法高亮
- Token 自动续期（无感刷新）
- 知识库统计看板
- 响应式布局（移动端可用）

## 系统架构

```
浏览器 (Vue 3 + Element Plus) :5173
    │  HTTP/REST + JWT
    ▼
Spring Boot 3 后端 :8080
    ├── Controller (Auth/User/Knowledge/Chat)
    ├── Service (RAG/Chat/Document/Embedding)
    ├── Repository (JPA + pgvector 向量搜索)
    │
    ├── JDBC → PostgreSQL + pgvector :5432
    └── HTTP → Ollama :11434
                 ├── Qwen2.5:7b（对话生成）
                 └── bge-m3（向量嵌入）
```

## 快速开始

### 环境要求
- Java 17+
- Maven 3.8+
- Node.js 20+
- PostgreSQL 17 + pgvector 扩展
- Ollama（本地 AI 模型运行器）

### 安装步骤

```bash
# 1. 安装 PostgreSQL 和 pgvector
brew install postgresql@17 pgvector
brew services start postgresql@17

# 2. 创建数据库
psql -U $(whoami) -d postgres -c "CREATE USER ragkb WITH PASSWORD 'ragkb123';"
psql -U $(whoami) -d postgres -c "CREATE DATABASE ragkb OWNER ragkb;"
psql -U $(whoami) -d ragkb -c "CREATE EXTENSION vector;"

# 3. 安装 Ollama 并下载模型
brew install ollama
ollama serve
ollama pull qwen2.5:7b      # 对话模型 (4.7GB)
ollama pull bge-m3           # 向量模型 (1.2GB)

# 4. 启动后端 (端口 8080)
cd backend
mvn spring-boot:run

# 5. 启动前端 (端口 5173)
cd frontend
npm install
npm run dev

# 6. 浏览器访问
open http://localhost:5173
```

### 默认账号
| 角色 | 用户名 | 密码 | 权限 |
|------|--------|------|------|
| 管理员 | admin | 123456 | 知识库管理 + 问答 |
| 普通用户 | 自行注册 | — | 仅问答 |

## 项目结构

```
rag-knowledge-qa-system/
├── backend/                          # Spring Boot 3 后端
│   ├── pom.xml                       # Maven 依赖配置
│   └── src/main/
│       ├── java/com/example/ragkb/
│       │   ├── config/               # Security / JWT / CORS / 异步
│       │   ├── controller/           # Auth / User / Knowledge / Chat
│       │   ├── service/              # RAG / Document / Embedding / Chat
│       │   ├── repository/           # JPA + pgvector 向量搜索
│       │   ├── model/entity/         # 实体类（6 张表）
│       │   ├── model/dto/            # 数据传输对象
│       │   ├── model/enums/          # 枚举
│       │   ├── exception/            # 全局异常处理
│       │   └── util/                 # 文本切分工具
│       └── resources/
│           ├── application.yml       # 应用配置
│           └── db/migration/         # Flyway 数据库迁移
├── frontend/                         # Vue 3 前端
│   └── src/
│       ├── api/                      # Axios 封装 + API 模块
│       ├── router/                   # 路由配置 + 守卫
│       ├── stores/                   # Pinia 状态管理
│       ├── views/                    # 页面（登录/注册/问答/管理/个人中心）
│       ├── styles/                   # 全局样式 + Element Plus 主题
│       └── components/               # 公共组件
├── data/documents/                   # 上传文档存储（自动创建）
├── CLAUDE.md                         # 详细设计文档
└── README.md                         # 本文件
```

## 数据库表设计

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| users | 用户表 | username, password_hash(BCrypt), role(ADMIN/USER) |
| documents | 知识库文档 | title, file_type, status(PENDING→PROCESSING→COMPLETED) |
| chunks | 文档分块 | content, chunk_index (500字符/块，100字符重叠) |
| chunk_embeddings | 向量表(pgvector) | embedding vector(1024), IVFFlat 索引 |
| conversations | 会话表 | user_id, title (自动截取首问) |
| messages | 消息表 | role(USER/ASSISTANT), content, references(TEXT) |

## API 接口概览

| 模块 | 端点 | 权限 |
|------|------|------|
| 认证 | `POST /api/auth/login` `POST /api/auth/register` `POST /api/auth/refresh` | 匿名 |
| 用户 | `GET /api/user/me` `PUT /api/user/password` | USER+ |
| 知识库 | `POST /api/knowledge/documents/upload` `GET/DELETE /api/knowledge/documents` | ADMIN |
| 问答 | `POST /api/chat/send` (SSE) `GET /api/chat/conversations` | USER+ |

## RAG 工作流程

```
文档入库：上传 → Tika 解析 → 切分(500字/块) → bge-m3 向量化 → pgvector
问答流程：提问 → bge-m3 向量化 → 余弦相似度 Top-5 → Prompt 拼接 → Qwen2.5 生成 → SSE 流式回答
```

## License

MIT

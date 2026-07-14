# RAG 企业级知识库问答系统 — 详细设计方案

## 项目概述

企业级产品：开发面向多领域企业知识的 RAG（检索增强生成）企业级知识库问答系统。用户通过浏览器进行知识库问答操作。

## 技术选型

| 层面 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 后端框架 | Spring Boot 3 + Spring Security | 3.4.1 | Java 生态标准，Security 搞定登录和权限 |
| AI 集成 | Spring AI + Ollama | 1.0.0-M6 | Spring 官方 AI 集成，调用本地 Ollama 模型 |
| 关系数据库 | PostgreSQL + pgvector | 17 + 0.8.4 | 业务数据 + 向量数据一个库搞定 |
| 本地 AI 模型 | Ollama 运行本地模型 | 0.30.4 | 完全离线，无需联网 |
| 对话模型 | Qwen2.5:7b（通义千问） | 7B 参数 | 中文能力强，适合商品问答 |
| 向量模型 | bge-m3（智源嵌入模型） | 1024 维向量 | 中文语义搜索精度高 |
| 前端框架 | Vue 3 + Element Plus | 3.x | 用户熟悉，Element Plus 提供现成 UI 组件库 |
| 文档解析 | Apache Tika | 2.9.2 | 支持 PDF/Word/Excel/Markdown/TXT 等格式 |
| 构建工具 | Maven + Vite | — | 后端 Maven，前端 Vite |
| 开发环境 | macOS 15.3 + Apple M1 + 16GB | — | 统一内存架构，模型可 GPU 加速 |

## 硬件适配（Apple M1 + 16GB 内存）

| 服务 | 内存占用 | 说明 |
|------|:--:|------|
| Ollama + Qwen2.5:7b | ~5GB | 对话生成 |
| Ollama + bge-m3 | ~1.5GB | Embedding 向量化 |
| PostgreSQL + pgvector | ~0.5GB | 数据存储 |
| Spring Boot 后端 | ~1GB | 业务服务 |
| 浏览器 | ~2GB | 前端页面 |
| **合计** | **~10GB** | ✅ 剩余约 6GB 余量 |

---

## 一、系统架构

```
浏览器 (Vue 3 + Element Plus)
    │  HTTP/REST (端口 8080) + JWT Token
    ▼
Spring Boot 3 后端
    ├── SecurityConfig (JWT Filter + 角色权限拦截)
    ├── Controller 层 (AuthController / UserController / KnowledgeController / ChatController)
    ├── Service 层 (Auth / User / Document / Embedding / RAG / Chat / Conversation)
    ├── Repository 层 (JPA + 原生 SQL 向量搜索)
    │
    ├── JDBC ──► PostgreSQL + pgvector (端口 5432)
    └── HTTP ──► Ollama (端口 11434)
                    ├── Qwen2.5:7b（回答生成）
                    └── bge-m3（Embedding 向量化）
```

### 核心数据流

1. **文档入库**：用户上传文件 → Tika 解析文本 → 文本切分（500字符/块，100字符重叠）→ Ollama bge-m3 向量化 → 向量存入 pgvector
2. **问答**：用户提问 → bge-m3 向量化问题 → pgvector 余弦相似度搜索 Top-5 → Prompt 拼接（知识库内容 + 历史对话 + 问题）→ Ollama Qwen2.5:7b 流式生成 → SSE 推送前端 → 展示回答 + 引用标签

---

## 二、数据库表设计（PostgreSQL + pgvector）

### users（用户表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| username | VARCHAR(50) UNIQUE NOT NULL | 用户名 |
| password_hash | VARCHAR(255) NOT NULL | BCrypt 加密密码 |
| email | VARCHAR(100) | 邮箱 |
| role | VARCHAR(20) NOT NULL DEFAULT 'USER' | ADMIN / USER |
| enabled | BOOLEAN NOT NULL DEFAULT TRUE | 是否启用 |
| created_at | TIMESTAMP NOT NULL | 创建时间 |
| updated_at | TIMESTAMP NOT NULL | 更新时间 |

### documents（知识库文档表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| title | VARCHAR(500) NOT NULL | 文档标题 |
| file_name | VARCHAR(500) NOT NULL | 原始文件名 |
| file_type | VARCHAR(50) NOT NULL | pdf/txt/md/docx/xlsx 等 |
| file_path | VARCHAR(1000) NOT NULL | 服务器存储路径 |
| file_size | BIGINT | 文件大小（字节） |
| status | VARCHAR(20) NOT NULL DEFAULT 'PENDING' | PENDING / PROCESSING / COMPLETED / FAILED |
| chunk_count | INTEGER DEFAULT 0 | 分块数量 |
| uploaded_by | BIGINT FK→users.id | 上传者 |
| created_at | TIMESTAMP NOT NULL | 创建时间 |
| updated_at | TIMESTAMP NOT NULL | 更新时间 |

### chunks（文档分块表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| document_id | BIGINT FK→documents.id NOT NULL | 所属文档 |
| chunk_index | INTEGER NOT NULL | 分块序号（0-based） |
| content | TEXT NOT NULL | 分块文本内容 |
| token_count | INTEGER | token 数量估算 |
| created_at | TIMESTAMP NOT NULL | 创建时间 |

### chunk_embeddings（向量表，pgvector 扩展）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| chunk_id | BIGINT FK→chunks.id UNIQUE NOT NULL | 关联分块 |
| embedding | vector(1024) NOT NULL | bge-m3 向量（1024维） |
| created_at | TIMESTAMP NOT NULL | 创建时间 |

> **性能索引**：在 embedding 列上创建 IVFFlat 索引用于近似最近邻搜索（建议数据量 > 1000 条后建立）

### conversations（会话表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| user_id | BIGINT FK→users.id NOT NULL | 所属用户 |
| title | VARCHAR(200) NOT NULL | 会话标题（自动截取首问前 30 字） |
| created_at | TIMESTAMP NOT NULL | 创建时间 |
| updated_at | TIMESTAMP NOT NULL | 最后活跃时间 |

> **索引**：user_id + updated_at DESC 复合索引，用于查询用户最近会话列表

### messages（消息表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| conversation_id | BIGINT FK→conversations.id NOT NULL | 所属会话 |
| role | VARCHAR(20) NOT NULL | USER / ASSISTANT |
| content | TEXT NOT NULL | 消息内容 |
| references_data | JSONB | 引用来源（仅 ASSISTANT 消息） |
| created_at | TIMESTAMP NOT NULL | 创建时间 |

**references_data JSONB 格式**：
```json
[
  {
    "documentId": 1,
    "documentTitle": "iPhone 15 产品手册.pdf",
    "chunkId": 42,
    "contentSnippet": "iPhone 15 采用 A16 仿生芯片...",
    "score": 0.923
  }
]
```

---

## 三、后端模块拆分

### 包结构
```
com.example.ragkb
├── RagKbApplication.java           # 启动类
├── config/
│   ├── SecurityConfig.java         # Spring Security 配置 + 权限拦截规则
│   ├── JwtTokenProvider.java       # JWT 生成/验证工具
│   ├── JwtAuthFilter.java          # JWT 认证过滤器
│   ├── WebConfig.java              # CORS 跨域配置
│   ├── AsyncConfig.java            # 异步任务线程池（文档处理）
│   └── DataInitializer.java        # 启动时自动创建 admin 账号
├── controller/
│   ├── AuthController.java         # 登录/注册/Token 刷新（POST /api/auth/**）
│   ├── UserController.java         # 用户信息/修改密码（GET/PUT /api/user/**）
│   ├── KnowledgeController.java    # 知识库 CRUD（仅 ADMIN）
│   └── ChatController.java         # 问答 SSE 流/会话管理
├── service/
│   ├── AuthService.java            # 认证逻辑（BCrypt 密码验证 + JWT 签发）
│   ├── UserService.java            # 用户管理
│   ├── DocumentService.java        # 文档上传、Tika 解析、异步处理编排
│   ├── EmbeddingService.java       # 调用 Ollama Embedding API 批量向量化
│   ├── RAGService.java             # RAG 核心编排（问题向量化→语义搜索→Prompt→LLM）
│   └── ConversationService.java    # 会话与消息的 CRUD
├── repository/
│   ├── UserRepository.java
│   ├── DocumentRepository.java
│   ├── ChunkRepository.java
│   ├── ChunkEmbeddingRepository.java  # 原生 JDBC 向量搜索（余弦相似度）
│   ├── ConversationRepository.java
│   └── MessageRepository.java
├── model/
│   ├── entity/                     # User, Document, Chunk, Conversation, Message
│   ├── dto/                        # LoginRequest, RegisterRequest, ChatRequest 等
│   └── enums/                      # UserRole, DocumentStatus
├── exception/
│   ├── GlobalExceptionHandler.java # 全局异常拦截（@RestControllerAdvice）
│   └── BusinessException.java      # 自定义业务异常
└── util/
    └── TextSplitter.java           # 中文友好文本切分（滑动窗口 + 段落边界）
```

### Spring Security 权限设计

- **认证方式**：JWT 无状态认证
  - `access_token`：30 分钟有效期
  - `refresh_token`：7 天有效期
  - Token 过期后前端自动用 refresh_token 换取新 access_token
- **USER 角色**：问答、会话管理、个人信息修改、修改密码
- **ADMIN 角色**：继承 USER 全部权限 + 知识库文档上传/删除/管理
- **初始账号**：`admin` / `123456`（BCrypt 加密存储，应用启动时自动创建）
- **权限拦截规则**：
  - `/api/auth/**` → 无需登录
  - `/api/knowledge/**` → 仅 ADMIN
  - `/api/chat/**` → 需登录（USER 或 ADMIN）
  - `/api/user/**` → 需登录（USER 或 ADMIN）

---

## 四、RAG 完整流程设计

### 4.1 文档入库流程（Ingestion Pipeline）

```
用户上传文件 (POST /api/knowledge/documents/upload)
    │
    ▼
Step 1: 文件校验（类型白名单 + 大小限制 20MB）
    │  存入 ./data/documents/，写入 documents 表（status=PENDING）
    ▼
Step 2: 文本解析（Apache Tika）
    │  自动检测格式 → 提取纯文本 → 更新 status=PROCESSING
    ▼
Step 3: 文本切分（TextSplitter）
    │  chunk_size=500字符 | chunk_overlap=100字符
    │  优先按段落/句号切分（中文友好） → 写入 chunks 表
    ▼
Step 4: 批量向量化（Ollama bge-m3 API）
    │  16条/批 → 获取 1024 维向量 → 写入 chunk_embeddings 表
    ▼
Step 5: 更新文档状态
       status=COMPLETED | chunk_count=N
```

> 整个处理过程通过 `@Async` 异步执行，文件上传后立即返回，不阻塞用户。

### 4.2 问答流程（Query Pipeline）

```
用户提问 (POST /api/chat/send, SSE 流式)
    │
    ▼
Step 1: 保存用户消息 → 创建/更新会话 → 自动生成标题（首问前30字）
    │
    ▼
Step 2: 问题向量化 → 调用 Ollama bge-m3 API → 获取 1024 维向量
    │
    ▼
Step 3: 语义搜索
    │  pgvector 余弦相似度搜索 Top-5 相关分块
    │  SQL: ORDER BY embedding <=> query_vector LIMIT 5
    │  过滤 similarity < 0.5 的低相关结果
    │  返回：chunk 内容 + 来源文档信息 + 相似度分数
    ▼
Step 4: Prompt 拼接
    │  System Prompt（知识库助手角色）
    │  + 【参考资料】（Top-5 检索结果，编号 [1]~[5]）
    │  + 【历史对话】（最近 3 轮）
    │  + 【用户问题】
    ▼
Step 5: Ollama Qwen2.5:7b 流式生成
    │  temperature=0.3, num_predict=1024
    │  SSE 逐字推送前端 → 打字机效果
    ▼
Step 6: 保存 AI 回答 + 引用来源
       返回完整 ChatResponse（回答文本 + references JSON）
```

### 4.3 关键 RAG 参数配置

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| chunk_size | 500 字符 | 每个文本块的字符数 |
| chunk_overlap | 100 字符 | 相邻块之间的重叠字符数 |
| top_k | 5 | 语义搜索返回的最相关结果数 |
| similarity_threshold | 0.5 | 最低余弦相似度阈值（低于此值的结果过滤掉） |
| max_history_rounds | 3 | 上下文携带的最近历史对话轮数 |
| temperature | 0.3 | LLM 回答随机性（越低越稳定） |
| num_predict | 1024 | 最大生成 token 数 |

### 4.4 System Prompt 模板

```
你是知识库助手，专门基于知识库内容回答用户的问题。

回答规则：
1. 请严格基于下方【参考资料】中的内容来回答问题，不要编造信息
2. 回答中引用资料时，使用 [编号] 标注来源，例如 [1]、[2]
3. 回答要准确、简洁、专业
4. 如果参考资料不足以回答用户问题，请明确告知"参考资料中未找到相关信息"
5. 如果用户问的是与商品、购物无关的问题，请礼貌引导其回到商品咨询
```

---

## 五、前端设计

### 5.1 路由设计

| 路径 | 页面 | 权限 | 说明 |
|------|------|------|------|
| `/` | — | — | 自动重定向到 /chat |
| `/login` | 登录页 | 匿名 | Element Plus 表单，渐变背景 |
| `/register` | 注册页 | 匿名 | 含密码确认校验 |
| `/chat` | 问答主页 | USER+ | 空状态提示"新建对话开始提问" |
| `/chat/:conversationId` | 指定会话 | USER+ | 加载历史消息 |
| `/profile` | 个人中心 | USER+ | 账号信息 + 修改密码 |
| `/admin/knowledge` | 知识库管理 | ADMIN | 文档上传表格 + 统计 |

### 5.2 核心页面与组件

```
App.vue (路由出口)
├── LoginView.vue            # 登录页（渐变背景 + Element Plus 表单）
├── RegisterView.vue         # 注册页（含确认密码校验）
├── ChatView.vue             # 主问答界面
│   ├── 左侧边栏
│   │   ├── 新建会话按钮
│   │   ├── ConversationList（会话列表、切换、删除、重命名）
│   │   └── 用户下拉菜单（个人中心/知识库管理/退出）
│   ├── 中间消息区
│   │   ├── MessageList（用户消息 + AI 回答 + 流式打字机效果）
│   │   ├── ReferenceBadge（引用来源标签，点击弹窗查看详情）
│   │   └── EmptyChat（空状态引导）
│   └── 底部输入区
│       ├── ChatInput（textarea + Enter 发送）
│       └── TypingIndicator（AI 回答中动画）
├── ProfileView.vue          # 个人中心（信息展示 + 修改密码）
├── AdminKnowledgeView.vue   # 知识库管理
│   ├── 统计卡片（文档数/分块数/向量数）
│   ├── 文档表格（分页、状态标签、操作按钮）
│   ├── UploadDialog（拖拽上传弹窗）
│   └── DocumentDetailDrawer（分块预览抽屉）
└── NotFoundView.vue         # 404 页面
```

### 5.3 前端技术细节

- **状态管理**：Pinia（auth、conversation、chat 三个 store）
- **HTTP 请求**：Axios 封装（JWT 自动携带 + Token 过期自动刷新 + 403 拦截）
- **SSE 流式消费**：Fetch API + ReadableStream reader + 逐字渲染
- **Markdown 渲染**：marked + highlight.js（代码高亮）
- **路由守卫**：未登录自动跳转 /login，非 ADMIN 无法访问 /admin/*
- **Element Plus 中文配置**：全局中文语言包

---

## 六、REST API 接口设计

### 6.1 认证模块 `/api/auth`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | 匿名 |
| POST | `/api/auth/login` | 用户登录，返回 JWT | 匿名 |
| POST | `/api/auth/refresh` | 刷新 access_token | 携带 refresh_token |

**登录请求体**：`{ "username": "admin", "password": "123456" }`

**登录响应**：
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "user": { "id": 1, "username": "admin", "role": "ADMIN" }
}
```

### 6.2 用户模块 `/api/user`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/user/me` | 获取当前用户信息 | USER+ |
| PUT | `/api/user/password` | 修改密码（需旧密码验证） | USER+ |

### 6.3 知识库管理模块 `/api/knowledge`（全部需要 ADMIN）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/documents/upload` | 上传文档（multipart/form-data） |
| GET | `/api/knowledge/documents` | 文档列表（分页查询） |
| GET | `/api/knowledge/documents/{id}` | 文档详情 + 分块列表 |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档及其所有分块和向量 |
| POST | `/api/knowledge/documents/{id}/reprocess` | 重新处理文档 |
| GET | `/api/knowledge/stats` | 知识库统计（文档数、分块数、向量数） |

### 6.4 问答与会话模块 `/api/chat`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/send` | 发送问题（**SSE 流式返回**） |
| GET | `/api/chat/conversations` | 当前用户的会话列表 |
| GET | `/api/chat/conversations/{id}` | 获取会话的所有消息 |
| PUT | `/api/chat/conversations/{id}` | 重命名会话标题 |
| DELETE | `/api/chat/conversations/{id}` | 删除会话及其消息 |

**SSE 事件格式**：
```
event: conversation    data: {"conversationId": 12}
event: status          data: "正在搜索知识库..."
event: references      data: [{"documentId":1, "documentTitle":"...", ...}]
event: content         data: "根据参考资料[1]，iPhone 15..."
event: done            data: {"conversationId":12, "references":[...]}
```

---

## 七、性能优化策略

| 层面 | 优化策略 | 实现方式 |
|------|----------|----------|
| **向量搜索** | IVFFlat 近似最近邻索引 | `CREATE INDEX ON chunk_embeddings USING ivfflat (embedding vector_cosine_ops)` |
| **向量搜索** | Top-K 限制 + 相似度阈值过滤 | 仅返回 top-5 且 score ≥ 0.5 的结果 |
| **LLM 调用** | SSE 流式返回 | 前端 0.5s 内看到首字，无需等完整生成 |
| **LLM 调用** | 上下文精简 | 历史对话仅带 3 轮，Prompt 总长控制在 2000 token 内 |
| **LLM 调用** | temperature 调低 | 知识问答场景设 temperature=0.3，减少幻觉 |
| **数据库** | HikariCP 连接池 | max-pool-size=20，多并发支持 |
| **数据库** | 关键字段索引 | conversations.user_id、messages.conversation_id 等 |
| **后端** | 异步文档处理 | `@Async` + ThreadPoolTaskExecutor，上传后立即返回 |
| **后端** | 批量 Embedding | 16 条/批调用 Ollama，减少网络往返 |
| **前端** | 路由懒加载 | `() => import('./views/ChatView.vue')`，首屏更小 |
| **前端** | KeepAlive 组件缓存 | 切换会话保留输入状态 |
| **前端** | Markdown 渲染缓存 | 仅对新内容调用 marked 解析 |
| **硬件** | JVM 堆内存限制 | `-Xmx4g`，为 Ollama 留足内存 |
| **硬件** | Ollama 并发限制 | `OLLAMA_NUM_PARALLEL=1`，16GB 内存下稳定运行 |

---

## 八、开发阶段划分

### 阶段 1：基础设施搭建 ✅
- 环境配置（PostgreSQL 17 + pgvector、Java 17、Ollama、Node.js）
- Spring Boot 3 项目初始化 + Maven 依赖
- Vue 3 + Element Plus 项目初始化
- 数据库表创建（Flyway 迁移脚本）
- Spring Security + JWT 认证骨架
- 用户注册/登录 API + 前端登录/注册页面

### 阶段 2：知识库管理 ✅
- 文档实体与 Repository
- 文档上传/列表/删除 API
- Apache Tika 文本解析
- TextSplitter 中文文本切分
- EmbeddingService + 向量存储/搜索
- 前端知识库管理页面（上传/表格/状态/详情）

### 阶段 3：RAG 问答核心 ✅
- RAGService 核心编排
- ConversationService 会话管理
- SSE 流式回答推送
- 前端 ChatView + 打字机效果 + 引用标签

### 阶段 4：多会话与交互 ✅
- 侧边栏会话列表 + CRUD
- 历史消息加载
- 修改密码 + 个人中心

### 阶段 5：企业级打磨（待进行）
- pgvector IVFFlat 索引建立
- Element Plus 主题定制
- 文档处理进度实时反馈
- 响应式布局优化
- 压力测试与性能调优

---

## 九、附加功能

- ✅ 多种文档格式支持（PDF、Word、Excel、Markdown、TXT）
- ✅ AI 回答打字机流式效果（SSE 推送）
- ✅ 知识库统计看板（文档数、分块数、向量总数）
- ✅ Markdown 渲染 + 代码语法高亮
- ✅ Token 自动续期（access_token 过期前用 refresh_token 自动刷新）
- ✅ 中文友好的文本切分策略（段落/句号边界 + 重叠窗口）

---

## 十、部署与运行

### 环境要求
- Java 17+
- Maven 3.8+
- Node.js 20+
- PostgreSQL 17 + pgvector 扩展
- Ollama（本地 AI 模型运行器）

### 模型准备
```bash
ollama pull qwen2.5:7b      # 对话模型（~4.7GB）
ollama pull bge-m3           # 向量模型（~1.2GB）
```

> Mac M1 16GB 暂用替代方案：`qwen2.5-coder:7b` + `nomic-embed-text`（已在本地）

### 启动步骤
```bash
# 1. 启动 PostgreSQL
brew services start postgresql@17

# 2. 启动 Ollama
ollama serve

# 3. 启动后端（端口 8080）
cd backend
mvn spring-boot:run

# 4. 启动前端（端口 5173）
cd frontend
npm run dev

# 5. 浏览器访问
open http://localhost:5173
# 默认管理员：admin / 123456
```

### 数据存储
- 数据库：`jdbc:postgresql://localhost:5432/ragkb`
- 上传文档：`./data/documents/`
- 数据库用户：`ragkb` / `ragkb123`

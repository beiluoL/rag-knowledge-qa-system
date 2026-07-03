-- ============================================
-- RAG 知识库系统 - 初始化建表脚本
-- ============================================

-- 启用 pgvector 扩展（需提前在数据库中执行 CREATE EXTENSION vector;）
-- 如果扩展不存在则创建
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- 1. 用户表
-- ============================================
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email       VARCHAR(100),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================
-- 2. 知识库文档表
-- ============================================
CREATE TABLE documents (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(500)  NOT NULL,
    file_name   VARCHAR(500)  NOT NULL,
    file_type   VARCHAR(50)   NOT NULL,
    file_path   VARCHAR(1000) NOT NULL,
    file_size   BIGINT,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    chunk_count INTEGER       DEFAULT 0,
    uploaded_by BIGINT        REFERENCES users(id),
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);

-- ============================================
-- 3. 文档分块表
-- ============================================
CREATE TABLE chunks (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER       NOT NULL,
    content     TEXT           NOT NULL,
    token_count INTEGER,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chunks_document_id ON chunks(document_id);

-- ============================================
-- 4. 向量表（pgvector）
-- ============================================
CREATE TABLE chunk_embeddings (
    id          BIGSERIAL PRIMARY KEY,
    chunk_id    BIGINT        NOT NULL UNIQUE REFERENCES chunks(id) ON DELETE CASCADE,
    embedding   vector(768)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ============================================
-- 5. 会话表
-- ============================================
CREATE TABLE conversations (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(200)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_user_id ON conversations(user_id);
CREATE INDEX idx_conversations_updated_at ON conversations(updated_at DESC);

-- ============================================
-- 6. 消息表
-- ============================================
CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20)   NOT NULL,
    content         TEXT           NOT NULL,
    references_data TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);

-- 初始 admin 用户由 DataInitializer 在应用启动时自动创建
-- 默认账号: admin / 123456

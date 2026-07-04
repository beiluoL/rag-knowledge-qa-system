-- 在线模式向量表（阿里云 DashScope text-embedding-v2: 1536 维）
CREATE TABLE chunk_embeddings_online (
    id          BIGSERIAL PRIMARY KEY,
    chunk_id    BIGINT           NOT NULL UNIQUE REFERENCES chunks(id) ON DELETE CASCADE,
    embedding   vector(1536)     NOT NULL,
    created_at  TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- 添加模式字段到 documents 表
ALTER TABLE documents ADD COLUMN IF NOT EXISTS ai_mode VARCHAR(10) DEFAULT 'offline';

-- V7: 知识库（knowledge_bases）与文档归属，泛化支持多领域（不再局限于电商）
CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(100),
    tags VARCHAR(500),
    owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kb_category ON knowledge_bases(category);
CREATE INDEX IF NOT EXISTS idx_kb_owner ON knowledge_bases(owner_id);

-- 文档归属知识库 + 分类标签
ALTER TABLE documents ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS category VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_documents_kb ON documents(knowledge_base_id);

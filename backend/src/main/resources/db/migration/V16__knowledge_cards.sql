-- V16: 知识卡片（用户可管理，支持 AI 一键生成）
CREATE TABLE IF NOT EXISTS knowledge_cards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    front TEXT,
    back TEXT NOT NULL,
    category VARCHAR(100),
    tags TEXT,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    knowledge_base_id BIGINT REFERENCES knowledge_bases(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kc_user ON knowledge_cards(user_id);
CREATE INDEX IF NOT EXISTS idx_kc_user_cat ON knowledge_cards(user_id, category);
CREATE INDEX IF NOT EXISTS idx_kc_user_updated ON knowledge_cards(user_id, updated_at DESC);

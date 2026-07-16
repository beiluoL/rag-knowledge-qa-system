-- V17: 对话长期记忆(user_memories) + 通知中心(notifications) + 文档多模态扩展
CREATE TABLE IF NOT EXISTS user_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    memory_type VARCHAR(20) NOT NULL,          -- preference / fact / summary
    content TEXT NOT NULL,
    source_conversation_id BIGINT,
    importance SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_um_user ON user_memories(user_id);
CREATE INDEX IF NOT EXISTS idx_um_user_type ON user_memories(user_id, memory_type);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,  -- NULL = 全员公告
    type VARCHAR(30) NOT NULL,                -- document_processed / study_reminder / system_announcement
    title VARCHAR(200),
    content TEXT,
    ref_type VARCHAR(30),
    ref_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_nt_user_read ON notifications(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_nt_created ON notifications(created_at DESC);

-- 多模态文档：原始媒体 URL（图片/音视频回显）+ 解析来源标记
ALTER TABLE documents ADD COLUMN IF NOT EXISTS media_url VARCHAR(1000);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS parse_source VARCHAR(20);  -- vision / ocr / asr

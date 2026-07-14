-- ============================================
-- V4: 用户资料扩展 + 消息反馈 + 对话统计 + 搜索索引
-- ============================================

-- users 表新增昵称和头像字段（支持用户资料编辑）
ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar VARCHAR(500);

-- messages 表新增反馈字段（like/dislike，用于评估 RAG 回答质量）
ALTER TABLE messages ADD COLUMN IF NOT EXISTS feedback VARCHAR(10);

-- conversations 表新增消息数统计字段（避免每次 COUNT 查询）
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS message_count INTEGER DEFAULT 0;

-- 消息内容全文搜索索引（支持对话搜索功能，使用 simple 配置兼容中文）
CREATE INDEX IF NOT EXISTS idx_messages_content ON messages USING gin(to_tsvector('simple', content));

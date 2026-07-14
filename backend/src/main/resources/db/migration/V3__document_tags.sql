-- ============================================
-- V3: 文档标签 + 搜索索引
-- ============================================

-- 添加标签字段
ALTER TABLE documents ADD COLUMN IF NOT EXISTS tags VARCHAR(500);

-- 文件名搜索索引（LIKE 查询加速）
CREATE INDEX IF NOT EXISTS idx_documents_title ON documents(title);

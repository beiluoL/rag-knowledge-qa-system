-- 启用 pg_trgm（PostgreSQL 自带扩展，无需外部安装），提供三元组相似度函数 similarity()
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- chunks.content 上建立三元组 GIN 索引，加速中文关键词相似度检索。
-- 替代失效的 to_tsvector('simple')：simple 词典不会切分中文，整段中文被当成单个词元，
-- 导致关键词分支在中文场景几乎召回不到结果。pg_trgm 的三元组对中文子串匹配有效。
CREATE INDEX IF NOT EXISTS idx_chunks_content_trgm
    ON chunks USING gin (content gin_trgm_ops);

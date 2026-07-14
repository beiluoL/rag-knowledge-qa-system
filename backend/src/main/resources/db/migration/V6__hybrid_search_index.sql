-- V6: 混合检索增强 - chunks 全文搜索索引
-- 为 LangChain4j 引擎的混合检索（向量 + 关键词）提供全文搜索能力

-- 为 chunks 表 content 列创建 GIN 全文搜索索引
CREATE INDEX IF NOT EXISTS idx_chunks_content_fts
    ON chunks USING gin(to_tsvector('simple', content));

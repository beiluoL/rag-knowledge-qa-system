-- V9: 知识库树状结构（parent_id 自关联）+ 动态分类（kb_categories）
-- 取消原有 category 字符串列，改为关联 kb_categories 表。

CREATE TABLE IF NOT EXISTS kb_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 预置分类
INSERT INTO kb_categories (name, description) VALUES
  ('技术文档', '技术文档、API/SDK、工程规范'),
  ('产品/电商手册', '商品、运营、客服话术'),
  ('法律法规', '合同、合规、条文'),
  ('教育培训', '课程、考试、培训材料'),
  ('医疗健康', '医学指南、健康科普'),
  ('编程与开发', '编程语言、框架、大模型等开发类知识')
ON CONFLICT (name) DO NOTHING;

-- knowledge_bases 增加树与分类字段
ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS parent_id BIGINT;
ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS category_id BIGINT;
ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

-- 旧 category 字符串回填到 category_id（按名称匹配已预置分类）
UPDATE knowledge_bases kb
SET category_id = c.id
FROM kb_categories c
WHERE kb.category IS NOT NULL AND kb.category = c.name AND kb.category_id IS NULL;

-- 外键
ALTER TABLE knowledge_bases
  ADD CONSTRAINT fk_kb_parent FOREIGN KEY (parent_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE;
ALTER TABLE knowledge_bases
  ADD CONSTRAINT fk_kb_category FOREIGN KEY (category_id) REFERENCES kb_categories(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_kb_parent ON knowledge_bases(parent_id);
CREATE INDEX IF NOT EXISTS idx_kb_category_id ON knowledge_bases(category_id);

-- 丢弃旧 category 列
ALTER TABLE knowledge_bases DROP COLUMN IF EXISTS category;

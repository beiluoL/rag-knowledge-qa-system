-- V11: 彻底去电商化（rebrand）
-- 将预置分类与系统知识库中带「电商」的命名改为通用的「产品」命名，
-- 并清理电商专属示例文档（级联删除其分块与向量），
-- 下次启动由 DataInitializer 以通用内容重新灌库（自动重新向量化）。
-- 注意：V7 / V9 已应用，不得修改其文件内容（Flyway 校验和会失败），此处仅更新已入库数据。

-- 1) 分类改名 + 描述去电商化
UPDATE kb_categories
SET name = '产品手册', description = '产品、运营、业务话术'
WHERE name = '产品/电商手册';

-- 2) 系统知识库改名 + 描述去电商化
UPDATE knowledge_bases
SET name = '产品手册', description = '产品、运营、业务话术类知识库'
WHERE name = '产品与电商手册';

-- 3) 删除电商专属示例文档（级联清理 chunks / chunk_embeddings 陈旧向量），
--    下次启动 DataInitializer 会以通用内容重新灌库
DELETE FROM documents
WHERE title IN ('商品上架标准流程', '优惠券规则与叠加逻辑');

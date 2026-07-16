-- V18: 修正 user_memories.importance 列类型为 INTEGER，与 JPA Integer 映射一致（避免 schema-validation 失败）
ALTER TABLE user_memories ALTER COLUMN importance TYPE INTEGER USING importance::integer;

-- V5: 文档管理增强 + 聊天体验增强 + 系统管理增强

-- conversations 表新增置顶标记
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS pinned BOOLEAN DEFAULT FALSE;

-- documents 表新增描述字段（便于管理员添加文档说明）
ALTER TABLE documents ADD COLUMN IF NOT EXISTS description VARCHAR(1000);

-- 用户操作日志表（系统管理增强）
CREATE TABLE IF NOT EXISTS operation_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    username    VARCHAR(50),
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id   BIGINT,
    detail      VARCHAR(500),
    ip_address  VARCHAR(50),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_op_logs_user ON operation_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_op_logs_created ON operation_logs(created_at DESC);

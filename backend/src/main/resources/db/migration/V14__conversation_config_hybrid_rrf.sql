-- 将混合检索开关与 RRF 融合常数从 application.yml 隐藏配置提升为可持久化、后台可配置的字段。
ALTER TABLE conversation_config
    ADD COLUMN IF NOT EXISTS hybrid_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS rrf_k INT NOT NULL DEFAULT 60;

-- V12: 新增「真 SSE 流式」开关配置列
-- 使后端生成模式可由管理员在「对话配置」中切换：
--   true  = 真 SSE 流式（模型边生成边按 chunk 推送 content 事件）
--   false = 模拟逐字（先取完整答案，再分块推送 content 事件，前端消费方式不变）
-- 注意：V7 / V9 / V10 / V11 已应用，不得修改其文件内容（Flyway 校验和会失败），此处仅新增列。

ALTER TABLE conversation_config
  ADD COLUMN true_sse_streaming_enabled BOOLEAN NOT NULL DEFAULT true;

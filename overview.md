# 本次交付概览（2026-07-15）

## 任务 99 — 真 SSE 流式改为「对话配置」菜单功能
把流式生成模式从硬编码改为后台可配置项，管理员在「系统管理 → 对话配置」实时切换。

**后端**
- `V12__add_true_sse_streaming_config.sql`：新增列 `true_sse_streaming_enabled BOOLEAN NOT NULL DEFAULT true`（Flyway 新增迁移，未改动已应用的 V7/V9/V10/V11）。
- `ConversationConfig` 实体 + `ConversationConfigService`（`getConfig` 透传 / `updateConfig` 落库 / `isTrueSseStreamingEnabled()` 实时读取）+ `ConversationConfigController`（解析新参数）。
- `ChatController.sendMessage`：读取配置分支——`true` 走真流式 `generateAnswerStream`（模型边生成边推送）；`false` 走 `generateAnswer` 取完整答案后分块（每 2 字 + 12ms）模拟逐字推送 `content` 事件，前端消费方式不变。

**前端**
- `api/conversation-config.ts` 类型加 `trueSseStreamingEnabled`。
- `AdminChatConfigView.vue` 新增「真 SSE 流式」开关卡片（Zap 图标 + el-switch）。

**验证**：API 实测 GET/PUT 切换并持久化成功；`clean` 重启后 Flyway 到 v12，回填日志含新字段。

## 任务 100 — 暗色/系统主题切换重设计为「系统设置」弹窗
原 App.vue 左下角浮动切换器位置不佳，改为「系统设置」弹出窗（参考用户截图）。

**实现**
- `App.vue` 移除浮动 `<ThemeToggle floating />`，改为全局挂载 `<SettingsModal />`。
- 新增 `composables/useSettings.ts`（模块级单例，全局共享开关）。
- 新增 `components/SettingsModal.vue`：`el-dialog` + 左侧三 tab（通用 / 外观 / 关于），外观页含浅色/深色/跟随系统三张主题卡片（纯 CSS 预览缩略图 + 选中打勾），点击即时生效并持久化；语言下拉占位（简中）。
- `ChatView.vue` 用户下拉菜单新增「系统设置」项，触发全局弹窗。

**验证**：`vite build` 通过（仅第三方 PURE 注释 / chunk 体积警告）；前端 :5173、后端 :8080 均已就绪，可本地测试。

## 当前可访问
- 前端（测试入口）：http://localhost:5173
- 后端 API：http://localhost:8080 （对话配置：`/api/admin/conversation-config`，需 admin 登录）

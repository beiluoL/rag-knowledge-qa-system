<template>
  <div class="chat-layout">
    <!-- 左侧会话列表 -->
    <aside class="sidebar" :style="{ width: sidebarWidth + 'px' }">
      <div class="sidebar-header">
        <h3>💬 会话列表</h3>
        <el-button type="primary" size="small" @click="newChat" :icon="Plus">新建</el-button>
      </div>
      <div class="mode-indicator" :class="aiMode" @click="handleToggleMode" title="点击切换在线/离线模式">
        <span v-if="aiMode === 'online'" class="mode-dot online"></span>
        <span v-else class="mode-dot offline"></span>
        <span class="mode-text">{{ aiMode === 'online' ? '在线 · 阿里云百炼' : '离线 · Ollama 本地' }}</span>
        <el-icon class="mode-switch-icon"><Switch /></el-icon>
      </div>
      <!-- AI 框架切换 -->
      <div class="mode-indicator framework-indicator" :class="aiFramework" @click="handleToggleFramework" title="点击切换 AI 框架（Spring AI / LangChain4j）">
        <span class="mode-dot" :class="aiFramework === 'langchain4j' ? 'lc4j' : 'sa'"></span>
        <span class="mode-text">{{ aiFramework === 'langchain4j' ? 'LangChain4j' : 'Spring AI' }}</span>
        <el-icon class="mode-switch-icon"><Switch /></el-icon>
      </div>
      <!-- RAG 可视化开关 -->
      <div class="rag-toggle" v-if="authStore.username">
        <span class="rag-toggle-label">RAG 过程可视化</span>
        <el-switch v-model="ragStepsVisible" size="small" />
      </div>
      <!-- 搜索框 -->
      <div class="search-box" v-if="authStore.username">
        <el-input v-model="searchKeyword" placeholder="搜索会话内容..." :prefix-icon="Search" clearable size="small" @clear="handleSearch" @keyup.enter="handleSearch" />
      </div>
      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="selectConversation(conv.id)"
        >
          <div class="conv-title">
            <span v-if="conv.pinned" class="pin-icon" title="已置顶">📌</span>
            {{ conv.title }}
          </div>
          <div class="conv-time">{{ formatDate(conv.updatedAt) }}</div>
          <el-dropdown trigger="click" @command="(cmd: string) => handleConvAction(cmd, conv)">
            <el-icon class="conv-more"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="pin">{{ conv.pinned ? '取消置顶' : '置顶' }}</el-dropdown-item>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
                <el-dropdown-item command="export">导出 Markdown</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <el-empty v-if="conversations.length === 0" description="暂无会话" :image-size="60" />
      </div>
      <div class="sidebar-footer">
        <el-dropdown trigger="click" @command="handleUserAction">
          <div class="user-info">
            <el-avatar :size="32" icon="UserFilled" />
            <span class="username">{{ authStore.username }}</span>
            <span v-if="authStore.isAdmin" class="admin-badge">管理员</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item v-if="authStore.isAdmin" command="admin">知识库管理</el-dropdown-item>
              <el-dropdown-item v-if="authStore.isAdmin" command="dashboard">系统管理</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <!-- 中间聊天区 -->
    <main class="chat-main">

      <div v-if="!currentConversationId && messages.length === 0" class="empty-chat">
        <el-empty description="新建一个对话开始提问吧！" :image-size="120" />
      </div>
      <div v-else class="message-list" ref="messageListRef">
        <div v-for="msg in messages" :key="msg.id" class="message-item" :class="'message-' + msg.role.toLowerCase()">
          <div class="message-avatar">
            <el-avatar v-if="msg.role === 'USER'" :size="36" icon="UserFilled" />
            <el-avatar v-else :size="36" style="background:#409eff">AI</el-avatar>
          </div>
          <div class="message-body">
            <div class="message-content markdown-body" v-html="renderMarkdown(msg.content)" />
            <!-- 引用来源 -->
            <div v-if="msg.references && msg.references.length > 0" class="references-section">
              <el-divider content-position="left">📚 参考来源</el-divider>
              <div v-for="(ref, idx) in msg.references" :key="ref.chunkId" class="reference-item">
                <el-popover placement="bottom" width="400" trigger="click">
                  <template #reference>
                    <el-tag type="success" class="reference-tag" size="small">
                      [{{ idx + 1 }}] {{ ref.documentTitle }}
                    </el-tag>
                  </template>
                  <div class="reference-detail">
                    <p><strong>来源文档：</strong>{{ ref.documentTitle }}</p>
                    <p><strong>相似度：</strong>{{ (ref.score * 100).toFixed(1) }}%</p>
                    <el-divider />
                    <p class="ref-content">{{ ref.contentSnippet }}</p>
                  </div>
                </el-popover>
              </div>
            </div>
            <!-- 消息反馈按钮（仅 AI 回答） -->
            <div v-if="msg.role === 'ASSISTANT'" class="feedback-bar">
              <el-button text size="small" :type="msg.feedback === 'like' ? 'success' : ''" @click="handleFeedback(msg, 'like')">
                👍 {{ msg.feedback === 'like' ? '已赞' : '有用' }}
              </el-button>
              <el-button text size="small" :type="msg.feedback === 'dislike' ? 'danger' : ''" @click="handleFeedback(msg, 'dislike')">
                👎 {{ msg.feedback === 'dislike' ? '已踩' : '无用' }}
              </el-button>
              <el-button text size="small" @click="handleCopyMessage(msg)">
                📋 复制
              </el-button>
            </div>
          </div>
        </div>

        <!-- RAG 思考过程块（在对话流中，用户消息之后、AI 回答之前） -->
        <Transition name="thinking-block">
          <div v-if="ragStepsVisible && ragSteps.length > 0" class="rag-thinking-block" :class="{ 'thinking-collapsed': !ragStepsExpanded }">
            <!-- 折叠头部 -->
            <div class="thinking-header" @click="ragStepsExpanded = !ragStepsExpanded">
              <div class="thinking-header-left">
                <span class="thinking-icon">🧠</span>
                <span class="thinking-title" v-if="ragStepsExpanded || !isStreaming">RAG 检索过程</span>
                <span class="thinking-title" v-else>RAG 检索 · {{ doneStepCount }}/{{ ragSteps.length }} 步骤</span>
                <span class="thinking-total" v-if="ragTotalTime && !isStreaming">· {{ ragTotalTime }}</span>
                <span v-if="isStreaming" class="thinking-spinner"></span>
              </div>
              <div class="thinking-header-right">
                <span class="thinking-step-dots">
                  <span v-for="s in ragSteps" :key="s.step" class="thinking-dot"
                    :class="{ 'dot-done': s.status === 'done', 'dot-running': s.status === 'running', 'dot-pending': s.status !== 'done' && s.status !== 'running' }"></span>
                </span>
                <el-icon class="thinking-chevron" :class="{ 'chevron-expanded': ragStepsExpanded }"><ArrowDown /></el-icon>
              </div>
            </div>

            <!-- 展开内容 -->
            <div class="thinking-body" v-show="ragStepsExpanded">
              <div class="thinking-steps">
                <div
                  v-for="s in ragSteps"
                  :key="s.step"
                  class="thinking-step"
                  :class="{ 'step-active': s.status === 'running', 'step-finished': s.status === 'done' }"
                >
                  <div class="thinking-step-icon">
                    <span v-if="s.status === 'running'" class="t-spinner"></span>
                    <span v-else-if="s.status === 'done'" class="t-check">✓</span>
                    <span v-else class="t-dot"></span>
                  </div>
                  <div class="thinking-step-text">
                    <span class="t-label">{{ s.label }}</span>
                    <span class="t-detail" v-if="s.status === 'done' || s.status === 'running'">{{ s.detail }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 打字中状态 -->
        <div v-if="isStreaming" class="message-item message-assistant">
          <div class="message-avatar">
            <el-avatar :size="36" style="background:#409eff">AI</el-avatar>
          </div>
          <div class="message-body">
            <div class="message-content markdown-body">
              <span v-if="streamingContent" v-html="renderMarkdown(streamingContent)" />
              <span class="typing-dots">
                <span class="dot" />
                <span class="dot" />
                <span class="dot" />
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部输入区 -->
      <div class="chat-input-area">
        <el-input
          v-model="inputQuestion"
          type="textarea"
          :rows="3"
          placeholder="输入商品相关问题，例如：这款手机的电池容量是多少？"
          :disabled="isStreaming"
          @keyup.enter.exact="handleSend"
          resize="none"
        />
        <div class="input-actions">
          <span class="input-hint">按 Enter 发送，Shift+Enter 换行</span>
          <el-button type="primary" :loading="isStreaming" @click="handleSend" :icon="Promotion">
            {{ isStreaming ? '回答中...' : '发送' }}
          </el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Promotion, ArrowDown, Search, Download } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getConversations, getConversationMessages, sendMessage as sendChatMessage,
  renameConversation, deleteConversation, feedbackMessage, searchConversations, exportConversation,
  togglePinConversation,
  type Conversation, type Message, type Reference } from '@/api/chat'
import { getAiMode, switchAiMode, getAiFramework, switchAiFramework } from '@/api/aimode'
import { marked } from 'marked'
import hljs from 'highlight.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sidebarWidth = 280
const messageListRef = ref<HTMLElement>()
const conversations = ref<Conversation[]>([])
const messages = ref<Message[]>([])
const currentConversationId = ref<number | null>(null)
const inputQuestion = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const aiMode = ref('offline')
const aiFramework = ref('spring-ai')
const searchKeyword = ref('')

// RAG 过程可视化
const ragStepsVisible = ref(true) // 开关
const ragStepsExpanded = ref(true) // 折叠/展开
const ragSteps = ref<Array<{ step: string; status: string; label: string; detail: string }>>([])
const ragTotalTime = ref('')
const doneStepCount = computed(() => ragSteps.value.filter(s => s.status === 'done').length)

// Markdown 代码块高亮渲染器
const renderer = new marked.Renderer()
renderer.code = function({ text, lang }: { text: string; lang?: string }) {
  if (lang && hljs.getLanguage(lang)) {
    const highlighted = hljs.highlight(text, { language: lang }).value
    return `<pre><code class="hljs language-${lang}">${highlighted}</code></pre>`
  }
  const highlighted = hljs.highlightAuto(text).value
  return `<pre><code class="hljs">${highlighted}</code></pre>`
}

marked.setOptions({ renderer })

function renderMarkdown(text: string) {
  if (!text) return ''
  return marked.parse(text) as string
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN')
}

// 加载会话列表
async function loadConversations() {
  try {
    const { data } = await getConversations()
    conversations.value = data
  } catch (e) {
    console.error('加载会话列表失败', e)
  }
}

// 选择会话
async function selectConversation(id: number) {
  currentConversationId.value = id
  router.replace(`/chat/${id}`)
  try {
    const { data } = await getConversationMessages(id)
    messages.value = data
    await nextTick()
    scrollToBottom()
  } catch (e) {
    console.error('加载消息失败', e)
  }
}

// 切换 AI 模式
async function handleToggleMode() {
  const targetMode = aiMode.value === 'online' ? 'offline' : 'online'
  try {
    const { data } = await switchAiMode(targetMode)
    aiMode.value = data.mode
    ElMessage.success(data.message || `已切换为 ${data.mode} 模式`)
  } catch (e: any) {
    ElMessage.error('切换失败: ' + (e.response?.data?.message || e.message))
  }
}

// 切换 AI 框架（Spring AI / LangChain4j）
async function handleToggleFramework() {
  const target = aiFramework.value === 'langchain4j' ? 'spring-ai' : 'langchain4j'
  try {
    const { data } = await switchAiFramework(target)
    aiFramework.value = data.framework
    ElMessage.success(data.message || `已切换为 ${data.framework} 框架`)
  } catch (e: any) {
    ElMessage.error('切换失败: ' + (e.response?.data?.message || e.message))
  }
}

// 新建会话
function newChat() {
  currentConversationId.value = null
  messages.value = []
  router.replace('/chat')
}

// 发送消息
async function handleSend() {
  const question = inputQuestion.value.trim()
  if (!question || isStreaming.value) return
  inputQuestion.value = ''

  // 添加用户消息到界面
  const userMsg: Message = {
    id: Date.now(),
    role: 'USER',
    content: question,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMsg)

  isStreaming.value = true
  streamingContent.value = ''
  ragSteps.value = []       // 清空上轮步骤
  ragTotalTime.value = ''
  ragStepsExpanded.value = true  // 新消息默认展开

  try {
    const response = await sendChatMessage(currentConversationId.value, question)
    const reader = response.body?.getReader()
    if (!reader) throw new Error('无响应流')

    let assistantContent = ''
    let references: Reference[] = []
    let fullBuffer = ''
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const text = decoder.decode(value, { stream: true })
      // 拼接上一次残留的半行数据
      fullBuffer += text
      const lines = fullBuffer.split('\n')
      // 最后一个元素可能是半行，保留到下次
      fullBuffer = lines.pop() || ''

      let currentEvent = ''
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim()
        }
        if (line.startsWith('data:')) {
          const dataStr = line.substring(5).trim()
          if (!dataStr) continue

          // content 事件：纯文本，直接追加
          if (currentEvent === 'content' || currentEvent === 'status') {
            if (currentEvent === 'content') {
              assistantContent += dataStr
              streamingContent.value = assistantContent
            }
            continue
          }

          // step 事件：RAG 过程可视化
          if (currentEvent === 'step') {
            try {
              const stepData = JSON.parse(dataStr)
              // 更新同一步骤的状态（避免重复添加）
              const existingIdx = ragSteps.value.findIndex(s => s.step === stepData.step)
              if (existingIdx >= 0) {
                ragSteps.value[existingIdx] = stepData
              } else {
                ragSteps.value.push(stepData)
              }
            } catch { /* ignore */ }
            continue
          }

          // 其他事件：JSON 格式
          try {
            const data = JSON.parse(dataStr)
            if (currentEvent === 'conversation' && data.conversationId) {
              currentConversationId.value = data.conversationId
              // 静默更新 URL，不触发路由导航，避免中断 SSE 流
              history.replaceState(null, '', `/chat/${data.conversationId}`)
              loadConversations()
            }
            if (currentEvent === 'references') {
              references = Array.isArray(data) ? data : []
            }
            if (currentEvent === 'done') {
              if (data.references) references = data.references
              if (data.totalTime) ragTotalTime.value = '总耗时: ' + data.totalTime
            }
          } catch {
            // 纯文本兜底
            assistantContent += dataStr
            streamingContent.value = assistantContent
          }
        }
      }
      await nextTick()
      scrollToBottom()
    }

    // 添加 AI 消息
    const assistantMsg: Message = {
      id: Date.now() + 1,
      role: 'ASSISTANT',
      content: assistantContent,
      references: references,
      createdAt: new Date().toISOString()
    }
    messages.value.push(assistantMsg)
    streamingContent.value = ''

    await loadConversations()
    await nextTick()
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error('发送失败: ' + (e.message || '未知错误'))
  } finally {
    isStreaming.value = false
  }
}

// 滚动到底部
function scrollToBottom() {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

// 搜索会话
async function handleSearch() {
  const keyword = searchKeyword.value.trim()
  try {
    const { data } = await searchConversations(keyword || undefined)
    conversations.value = data
  } catch (e) {
    console.error('搜索失败', e)
  }
}

// 消息反馈（点赞/踩）
async function handleFeedback(msg: Message, feedback: string) {
  // 点击相同反馈值则取消
  const newFeedback = msg.feedback === feedback ? '' : feedback
  try {
    await feedbackMessage(msg.id, newFeedback)
    msg.feedback = newFeedback || undefined
  } catch (e) {
    ElMessage.error('反馈失败')
  }
}

/**
 * 复制消息内容到剪贴板
 * @param msg 要复制的消息
 */
function handleCopyMessage(msg: Message) {
  navigator.clipboard.writeText(msg.content).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

// 导出会话为 Markdown
async function handleExport(conv: Conversation) {
  try {
    const { data } = await exportConversation(conv.id)
    // 创建 Blob 下载
    const blob = new Blob([data as any], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${conv.title || '会话导出'}.md`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

// 会话操作
async function handleConvAction(cmd: string, conv: Conversation) {
  if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm('确定删除该会话？', '确认', { type: 'warning' })
      await deleteConversation(conv.id)
      if (currentConversationId.value === conv.id) newChat()
      await loadConversations()
      ElMessage.success('已删除')
    } catch { /* 取消 */ }
  } else if (cmd === 'rename') {
    try {
      const { value } = await ElMessageBox.prompt('请输入新标题', '重命名', {
        inputValue: conv.title
      })
      if (value) {
        await renameConversation(conv.id, value)
        await loadConversations()
        ElMessage.success('已重命名')
      }
    } catch { /* 取消 */ }
  } else if (cmd === 'export') {
    await handleExport(conv)
  } else if (cmd === 'pin') {
    try {
      await togglePinConversation(conv.id)
      conv.pinned = !conv.pinned
      await loadConversations()
      ElMessage.success(conv.pinned ? '已置顶' : '已取消置顶')
    } catch (e) {
      ElMessage.error('操作失败')
    }
  }
}

// 用户操作
function handleUserAction(cmd: string) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'admin') router.push('/admin/knowledge')
  else if (cmd === 'dashboard') router.push('/admin/dashboard')
  else if (cmd === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}

onMounted(async () => {
  // 获取 AI 模式
  try {
    const { data } = await getAiMode()
    aiMode.value = data.mode
  } catch { /* ignore */ }
  try {
    const { data } = await getAiFramework()
    aiFramework.value = data.framework
  } catch { /* ignore */ }
  await loadConversations()
  const convId = route.params.conversationId
  if (convId) {
    await selectConversation(Number(convId))
  }
})
</script>

<style scoped>
.chat-layout {
  height: 100vh;
  display: flex;
}
.sidebar {
  width: 280px;
  min-width: 240px;
  height: 100%;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
}
.sidebar-header h3 { font-size: 16px; font-weight: 600; }
.mode-indicator {
  padding: 8px 16px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  gap: 6px;
}
.mode-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.mode-dot.online { background: #67c23a; box-shadow: 0 0 4px #67c23a; }
.mode-dot.offline { background: #409eff; box-shadow: 0 0 4px #409eff; }
.mode-indicator { cursor: pointer; user-select: none; transition: background 0.2s; }
.mode-indicator:hover { background: #e8eaed; }
.mode-text { flex: 1; }
.mode-switch-icon { color: #909399; font-size: 14px; }
.mode-dot.lc4j { background: #e6a23c; box-shadow: 0 0 4px #e6a23c; }
.mode-dot.sa { background: #409eff; box-shadow: 0 0 4px #409eff; }
.framework-indicator { margin-top: 4px; }
.conversation-list { flex: 1; overflow-y: auto; padding: 8px; }
.conv-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  position: relative;
  transition: background 0.2s;
}
.conv-item:hover { background: #f5f7fa; }
.conv-item.active { background: var(--el-color-primary-light-9); }
.conv-title { font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-time { font-size: 12px; color: #909399; margin-top: 4px; }
.conv-more { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); color: #c0c4cc; }
.sidebar-footer { padding: 12px 16px; border-top: 1px solid #ebeef5; }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.username { font-size: 14px; }
.admin-badge { font-size: 10px; background: #e6a23c; color: white; padding: 1px 6px; border-radius: 10px; }
.chat-main { flex: 1; display: flex; flex-direction: column; height: 100%; background: #f5f7fa; }
.empty-chat { flex: 1; display: flex; align-items: center; justify-content: center; }
.message-list { flex: 1; overflow-y: auto; padding: 24px; }
.message-item { display: flex; gap: 12px; margin-bottom: 24px; }
.message-user { flex-direction: row-reverse; }
.message-body { max-width: 70%; }
.message-content { padding: 12px 16px; border-radius: 12px; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.message-user .message-content { background: var(--el-color-primary-light-9); }
.references-section { margin-top: 12px; }
.reference-item { margin: 4px 0; }
.ref-content { color: #606266; font-size: 13px; line-height: 1.6; }
.chat-input-area {
  padding: 16px 24px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}
.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.input-hint { font-size: 12px; color: #c0c4cc; }

/* ═══════════════════════════════════════════
   RAG 思考过程块 — 对话流内 + 可折叠
   参考 ChatGPT/Claude 思考过程设计
   ═══════════════════════════════════════════ */

.rag-toggle {
  padding: 8px 16px;
  font-size: 12px;
  color: #606266;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rag-toggle-label { font-size: 12px; font-weight: 500; }

/* 思考块入场动画 */
.thinking-block-enter-active {
  animation: thinkIn 0.3s ease-out;
}
.thinking-block-leave-active {
  animation: thinkOut 0.2s ease-in;
}
@keyframes thinkIn {
  from { opacity: 0; transform: translateY(-6px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes thinkOut {
  from { opacity: 1; transform: translateY(0); }
  to   { opacity: 0; transform: translateY(-4px); }
}

/* ── 思考块容器 ── */
.rag-thinking-block {
  margin: 0 0 16px 48px;   /* 与 AI 消息对齐（avatar 宽度 36 + gap 12） */
  max-width: 75%;
  background: #fafbfd;
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.3s ease;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
}
.rag-thinking-block.thinking-collapsed {
  background: #f8f9fb;
}

/* ── 折叠头部 ── */
.thinking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.thinking-header:hover { background: rgba(0,0,0,0.02); }
.thinking-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.thinking-icon { font-size: 15px; flex-shrink: 0; }
.thinking-title {
  font-size: 13px;
  font-weight: 600;
  color: #4a5568;
  white-space: nowrap;
}
.thinking-total {
  font-size: 12px;
  color: #a0aec0;
  white-space: nowrap;
}
.thinking-spinner {
  width: 14px; height: 14px;
  border: 2px solid #e2e8f0;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

/* 步骤圆点指示器 */
.thinking-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.thinking-step-dots {
  display: flex;
  gap: 4px;
}
.thinking-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  transition: all 0.3s;
}
.thinking-dot.dot-done    { background: #68d391; }
.thinking-dot.dot-running { background: #667eea; animation: dotPulse 0.8s ease-in-out infinite; }
.thinking-dot.dot-pending { background: #e2e8f0; }
@keyframes dotPulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50%      { transform: scale(1.5); opacity: 0.5; }
}

.thinking-chevron {
  font-size: 14px;
  color: #a0aec0;
  transition: transform 0.25s ease;
}
.thinking-chevron.chevron-expanded {
  transform: rotate(180deg);
}

/* ── 展开内容 ── */
.thinking-body {
  border-top: 1px solid #edf2f7;
  background: #fff;
  animation: bodyIn 0.25s ease-out;
}
@keyframes bodyIn {
  from { opacity: 0; }
  to   { opacity: 1; }
}
.thinking-steps {
  padding: 8px 14px 12px;
}

.thinking-step {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 6px 0;
  transition: all 0.2s;
}
.thinking-step + .thinking-step {
  border-top: 1px solid #f7fafc;
}

/* 步骤图标 */
.thinking-step-icon {
  width: 20px; height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}
.t-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #e2e8f0;
}
.t-check {
  color: #68d391;
  font-size: 11px;
  font-weight: bold;
}
.t-spinner {
  width: 13px; height: 13px;
  border: 2px solid #e2e8f0;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 步骤文字 */
.thinking-step-text {
  flex: 1; min-width: 0;
}
.t-label {
  font-size: 12px;
  font-weight: 500;
  color: #4a5568;
  display: block;
}
.step-active .t-label { color: #667eea; }
.t-detail {
  font-size: 11px;
  color: #a0aec0;
  margin-top: 2px;
  display: block;
  line-height: 1.4;
}
.step-active .t-detail { color: #667eea; }
.step-finished .t-detail { color: #68d391; }

/* ── 搜索框 ── */
.search-box {
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
}

/* ── 消息反馈按钮 ── */
.feedback-bar {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  opacity: 0.6;
  transition: opacity 0.2s;
}
.feedback-bar:hover {
  opacity: 1;
}

/* ── 置顶图标 ── */
.pin-icon {
  font-size: 11px;
  margin-right: 2px;
}
</style>

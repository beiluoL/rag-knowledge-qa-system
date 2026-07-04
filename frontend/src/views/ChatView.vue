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
      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="selectConversation(conv.id)"
        >
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-time">{{ formatDate(conv.updatedAt) }}</div>
          <el-dropdown trigger="click" @command="(cmd: string) => handleConvAction(cmd, conv)">
            <el-icon class="conv-more"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">重命名</el-dropdown-item>
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
          </div>
        </div>

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
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Promotion } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getConversations, getConversationMessages, sendMessage as sendChatMessage,
  renameConversation, deleteConversation, type Conversation, type Message, type Reference } from '@/api/chat'
import { getAiMode, switchAiMode } from '@/api/aimode'
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
  }
}

// 用户操作
function handleUserAction(cmd: string) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'admin') router.push('/admin/knowledge')
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
</style>

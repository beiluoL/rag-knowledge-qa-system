<template>
  <div class="chat-layout">
    <!-- 移动端抽屉遮罩 -->
    <transition name="fade">
      <div
        v-if="sidebarOpen"
        class="sidebar-backdrop"
        @click="closeSidebar"
        aria-hidden="true"
      />
    </transition>

    <!-- 左侧会话栏：桌面固定 / 平板·手机抽屉 -->
    <aside class="sidebar" :class="{ 'sidebar-open': sidebarOpen }">
      <div class="sidebar-header">
        <div class="brand">
          <BookOpen class="brand-logo" />
          <span class="brand-name">智能知识库</span>
        </div>
        <el-button
          class="collapse-btn"
          text
          :icon="PanelLeftClose"
          aria-label="收起会话列表"
          @click="closeSidebar"
        />
      </div>

      <div class="sidebar-actions">
        <el-button
          type="primary"
          :icon="Plus"
          class="new-chat-btn"
          @click="newChat"
          aria-label="新建会话"
        >
          新建会话
        </el-button>
      </div>

      <!-- AI 模式指示（在线/离线） -->
      <div
        class="mode-indicator"
        :class="aiMode"
        role="button"
        tabindex="0"
        :aria-label="aiMode === 'online' ? '当前在线模式，点击切换' : '当前离线模式，点击切换'"
        @click="handleToggleMode"
        @keyup.enter="handleToggleMode"
      >
        <span class="mode-dot" :class="aiMode" />
        <span class="mode-text">{{ aiMode === 'online' ? '在线 · 阿里云百炼' : '离线 · Ollama 本地' }}</span>
        <el-icon class="mode-switch-icon"><Repeat /></el-icon>
      </div>
      <!-- AI 框架指示（Spring AI / LangChain4j） -->
      <div
        class="mode-indicator framework-indicator"
        :class="aiFramework"
        role="button"
        tabindex="0"
        :aria-label="aiFramework === 'langchain4j' ? '当前 LangChain4j 框架，点击切换' : '当前 Spring AI 框架，点击切换'"
        @click="handleToggleFramework"
        @keyup.enter="handleToggleFramework"
      >
        <span class="mode-dot" :class="aiFramework" />
        <span class="mode-text">{{ aiFramework === 'langchain4j' ? 'LangChain4j' : 'Spring AI' }}</span>
        <el-icon class="mode-switch-icon"><Repeat /></el-icon>
      </div>

      <!-- 知识库选择（树状，决定 RAG 检索范围；选父库=检索整棵子树） -->
      <div class="kb-selector" v-if="authStore.username">
        <span class="kb-selector-label"><el-icon><Library /></el-icon> 知识库范围</span>
        <el-tree-select
          v-model="selectedKbId"
          :data="kbTree"
          :props="{ label: 'name', children: 'children' }"
          value-key="id"
          node-key="id"
          placeholder="全部知识库"
          size="small"
          clearable
          default-expand-all
          style="width: 100%"
        />
      </div>

      <!-- RAG 可视化开关 -->
      <div class="rag-toggle" v-if="authStore.username">
        <span class="rag-toggle-label">RAG 过程可视化</span>
        <el-switch v-model="ragStepsVisible" size="small" aria-label="RAG 过程可视化开关" />
      </div>

      <!-- 会话列表区域 -->
      <div class="conversation-section">
        <div class="conversation-section-head">
          <span class="section-title"><el-icon><MessageCircle /></el-icon> 会话列表</span>
          <span v-if="conversations.length" class="section-count ui-badge ui-badge-info">
            {{ conversations.length }}
          </span>
        </div>

        <!-- 搜索框 -->
        <div class="search-box" v-if="authStore.username">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索会话内容..."
            :prefix-icon="Search"
            clearable
            size="small"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="conversation-list">
          <!-- 加载骨架 -->
          <div v-if="conversationsLoading" class="conv-skeleton">
            <el-skeleton :rows="7" animated />
          </div>
          <!-- 空状态 -->
          <div v-else-if="conversations.length === 0" class="ui-empty conv-empty">
            <el-icon class="empty-icon"><MessageCircle /></el-icon>
            <p class="empty-title">还没有会话</p>
            <p class="empty-desc">点击「新建会话」，开始你的第一次提问</p>
            <el-button type="primary" :icon="Plus" @click="newChat">新建会话</el-button>
          </div>
          <!-- 列表 -->
          <template v-else>
            <div
              v-for="conv in conversations"
              :key="conv.id"
              class="conv-item"
              :class="{ active: conv.id === currentConversationId }"
              @click="selectConversation(conv.id)"
            >
              <div class="conv-title">
                <span v-if="conv.pinned" class="pin-icon" title="已置顶" aria-label="已置顶">
                  <el-icon><Pin /></el-icon>
                </span>
                <span class="conv-title-text">{{ conv.title }}</span>
              </div>
              <div class="conv-time">{{ formatDate(conv.updatedAt) }}</div>
              <el-dropdown
                trigger="click"
                @command="(cmd: string) => handleConvAction(cmd, conv)"
                @click.stop
              >
                <el-icon class="conv-more" aria-label="更多操作"><MoreHorizontal /></el-icon>
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
          </template>
        </div>
      </div>

      <!-- 用户信息 -->
      <div class="sidebar-footer">
        <el-dropdown trigger="click" @command="handleUserAction">
          <div class="user-info" role="button" tabindex="0" aria-label="用户菜单">
            <el-avatar :size="32"><User /></el-avatar>
            <span class="username">{{ authStore.username }}</span>
            <span v-if="authStore.isAdmin" class="admin-badge">管理员</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile"><el-icon><User /></el-icon>个人中心</el-dropdown-item>
              <el-dropdown-item command="learn"><el-icon><BookOpenCheck /></el-icon>学习中心</el-dropdown-item>
              <el-dropdown-item command="quiz"><el-icon><PenLine /></el-icon>智能出题</el-dropdown-item>
              <el-dropdown-item command="writing"><el-icon><Pencil /></el-icon>智能写作</el-dropdown-item>
              <el-dropdown-item command="review"><el-icon><Calendar /></el-icon>复习计划</el-dropdown-item>
              <el-dropdown-item v-if="authStore.isAdmin" command="admin"><el-icon><Wrench /></el-icon>知识库管理</el-dropdown-item>
              <el-dropdown-item v-if="authStore.isAdmin" command="dashboard"><el-icon><BarChart3 /></el-icon>系统管理</el-dropdown-item>
              <el-dropdown-item command="logout" divided><el-icon><LogOut /></el-icon>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <!-- 中间聊天区 -->
    <main class="chat-main">
      <!-- 顶部标题栏 -->
      <header class="chat-header">
        <el-button
          class="menu-toggle"
          text
          :icon="Menu"
          aria-label="打开会话列表"
          @click="toggleSidebar"
        />
        <div class="chat-header-title">
          <span class="chat-title">{{ currentConversationId ? currentTitle || 'AI 智能问答' : 'AI 智能问答助手' }}</span>
          <span class="chat-subtitle">
            {{ aiMode === 'online' ? '在线模式' : '离线模式' }} · {{ aiFramework === 'langchain4j' ? 'LangChain4j' : 'Spring AI' }}
          </span>
        </div>
        <div class="chat-header-actions">
          <el-button
            class="header-new-chat"
            type="primary"
            :icon="Plus"
            @click="newChat"
            aria-label="新建会话"
          >
            <span class="new-chat-text">新建</span>
          </el-button>
        </div>
      </header>

      <div v-if="!currentConversationId && messages.length === 0" class="empty-chat">
        <el-empty description="新建一个对话开始提问吧！" :image-size="120" />
      </div>
      <div v-else class="message-list" ref="messageListRef">
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="message-item"
          :class="'message-' + msg.role.toLowerCase()"
        >
          <div class="message-avatar">
            <el-avatar v-if="msg.role === 'USER'" :size="36"><User /></el-avatar>
            <el-avatar v-else :size="36" class="ai-avatar">AI</el-avatar>
          </div>
          <div class="message-body">
            <div class="message-content markdown-body" v-html="renderMarkdown(msg.content)" />
            <!-- 引用来源 -->
            <div v-if="msg.references && msg.references.length > 0" class="references-section">
              <el-divider content-position="left">
                <span class="ref-divider-label"><el-icon><Library /></el-icon> 参考来源</span>
              </el-divider>
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
            <!-- 拒答提示横幅（检索未命中相关资料时） -->
            <div v-if="msg.role === 'ASSISTANT' && msg.refused" class="refuse-banner" role="alert">
              <el-icon class="refuse-icon"><AlertTriangle /></el-icon>
              <span>未检索到相关资料，以下回答可能超出知识库范围，请谨慎参考，或补充相关文档后重试。</span>
            </div>
            <!-- 消息反馈按钮（仅 AI 回答） -->
            <div v-if="msg.role === 'ASSISTANT'" class="feedback-bar">
              <el-button
                text
                size="small"
                :type="msg.feedback === 'like' ? 'primary' : ''"
                @click="handleFeedback(msg, 'like')"
                aria-label="有用"
              >
                <el-icon><ThumbsUp /></el-icon> {{ msg.feedback === 'like' ? '已赞' : '有用' }}
              </el-button>
              <el-button
                text
                size="small"
                :type="msg.feedback === 'dislike' ? 'danger' : ''"
                @click="handleFeedback(msg, 'dislike')"
                aria-label="无用"
              >
                <el-icon><ThumbsDown /></el-icon> {{ msg.feedback === 'dislike' ? '已踩' : '无用' }}
              </el-button>
              <el-button text size="small" @click="handleCopyMessage(msg)" aria-label="复制消息">
                <el-icon><Copy /></el-icon> 复制
              </el-button>
            </div>
          </div>
        </div>

        <!-- RAG 推理过程块（Codex 风格推理轨迹，位于用户消息之后、AI 回答之前） -->
        <Transition name="thinking-block">
          <div v-if="ragStepsVisible && ragSteps.length > 0" class="codex-reasoning" :class="{ 'reasoning-collapsed': !ragStepsExpanded }">
            <!-- 折叠头部 -->
            <div class="reasoning-header" @click="ragStepsExpanded = !ragStepsExpanded" role="button" tabindex="0" :aria-expanded="ragStepsExpanded" @keyup.enter="ragStepsExpanded = !ragStepsExpanded">
              <div class="reasoning-header-left">
                <span class="reasoning-spark">✦</span>
                <span class="reasoning-title" v-if="ragStepsExpanded || !isStreaming">推理过程</span>
                <span class="reasoning-title" v-else>推理中 · {{ doneStepCount }}/{{ ragSteps.length }} 步</span>
                <span class="reasoning-total" v-if="ragTotalTime && !isStreaming">{{ ragTotalTime }}</span>
                <span v-if="isStreaming" class="reasoning-spinner"></span>
              </div>
              <div class="reasoning-header-right">
                <span class="reasoning-count" v-if="!isStreaming">{{ ragSteps.length }} 步</span>
                <el-icon class="reasoning-chevron" :class="{ 'chevron-expanded': ragStepsExpanded }"><ArrowDown /></el-icon>
              </div>
            </div>

            <!-- 展开内容：左侧主轴 + 步骤节点 -->
            <div class="reasoning-body" v-show="ragStepsExpanded">
              <div class="reasoning-track">
                <div
                  v-for="s in ragSteps"
                  :key="s.step"
                  class="reasoning-step"
                  :class="{ 'step-active': s.status === 'running', 'step-finished': s.status === 'done' }"
                >
                  <div class="reasoning-node">
                    <span v-if="s.status === 'running'" class="r-spinner"></span>
                    <span v-else-if="s.status === 'done'" class="r-check">✓</span>
                    <span v-else class="r-dot"></span>
                  </div>
                  <div class="reasoning-content">
                    <span class="r-label">{{ s.label }}</span>
                    <span class="r-detail" v-if="s.status === 'done' || s.status === 'running'">{{ s.detail }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 检索片段预览（可展开） -->
            <div v-if="retrievedChunks.length" class="retrieved-panel" :class="{ 'retrieved-collapsed': !chunksExpanded }">
              <div class="retrieved-header" @click="chunksExpanded = !chunksExpanded" role="button" tabindex="0" :aria-expanded="chunksExpanded" @keyup.enter="chunksExpanded = !chunksExpanded">
                <el-icon class="retrieved-icon"><FileText /></el-icon>
                <span class="retrieved-title">检索片段预览</span>
                <span class="retrieved-count">{{ retrievedChunks.length }} 条</span>
                <span v-if="isStreaming" class="retrieved-live">· 实时</span>
                <el-icon class="retrieved-chevron" :class="{ 'chevron-expanded': chunksExpanded }"><ArrowDown /></el-icon>
              </div>
              <div v-show="chunksExpanded" class="retrieved-body">
                <div v-for="(c, i) in retrievedChunks" :key="c.chunkId" class="retrieved-card">
                  <div class="retrieved-card-head">
                    <span class="retrieved-idx">{{ i + 1 }}</span>
                    <span class="retrieved-doc" :title="c.documentTitle">{{ c.documentTitle }}</span>
                    <span class="retrieved-score">{{ (c.score * 100).toFixed(1) }}%</span>
                  </div>
                  <div class="retrieved-score-bar"><span :style="{ width: Math.min(100, c.score * 100) + '%' }"></span></div>
                  <p class="retrieved-snippet">{{ c.contentSnippet }}</p>
                </div>
              </div>
            </div>
          </div>
        </Transition>

        <!-- 打字中状态 -->
        <div v-if="isStreaming" class="message-item message-assistant">
          <div class="message-avatar">
            <el-avatar :size="36" class="ai-avatar">AI</el-avatar>
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
          placeholder="输入你的问题，例如：退货政策是怎样的？如何配置数据库连接？"
          :disabled="isStreaming"
          aria-label="输入你的问题"
          @keyup.enter.exact="handleSend"
          resize="none"
        />
        <div class="input-actions">
          <span class="input-hint">按 Enter 发送，Shift+Enter 换行</span>
          <el-button
            type="primary"
            :loading="isStreaming"
            @click="handleSend"
            :icon="Send"
            aria-label="发送消息"
          >
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
import {
  BookOpen, Plus, Send, ArrowDown, Search, FileText, AlertTriangle, MoreHorizontal,
  Repeat, Library, MessageCircle, Pin, ThumbsUp, ThumbsDown, Copy, Menu, PanelLeftClose,
  User, BookOpenCheck, PenLine, Pencil, Calendar, Wrench, BarChart3, LogOut
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { getConversations, getConversationMessages, sendMessage as sendChatMessage,
  renameConversation, deleteConversation, feedbackMessage, searchConversations, exportConversation,
  togglePinConversation,
  type Conversation, type Message, type Reference } from '@/api/chat'
import { getAiMode, switchAiMode, getAiFramework, switchAiFramework } from '@/api/aimode'
import { getKbTree, type KbTreeNode } from '@/api/knowledgeBase'
import { marked } from 'marked'
import hljs from 'highlight.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

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

// 会话列表加载状态（用于骨架屏）
const conversationsLoading = ref(false)
// 移动端抽屉开合
const sidebarOpen = ref(false)

const currentTitle = computed(
  () => conversations.value.find(c => c.id === currentConversationId.value)?.title || ''
)

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}
function closeSidebar() {
  sidebarOpen.value = false
}

// RAG 过程可视化
const ragStepsVisible = ref(true) // 开关
const ragStepsExpanded = ref(true) // 折叠/展开
const ragSteps = ref<Array<{ step: string; status: string; label: string; detail: string }>>([])
const ragTotalTime = ref('')
// 检索到的片段（来自 references 事件），用于推理面板内可展开预览
const retrievedChunks = ref<Reference[]>([])
const chunksExpanded = ref(true)
const doneStepCount = computed(() => ragSteps.value.filter(s => s.status === 'done').length)

// 知识库范围（树；决定 RAG 检索的知识库；null = 全部）
const selectedKbId = ref<number | null>(null)
const kbTree = ref<KbTreeNode[]>([])

async function loadKbTree() {
  try {
    const { data } = await getKbTree()
    kbTree.value = data || []
  } catch (e) {
    console.error('加载知识库树失败', e)
  }
}

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
  conversationsLoading.value = true
  try {
    const { data } = await getConversations()
    conversations.value = data
  } catch (e) {
    console.error('加载会话列表失败', e)
  } finally {
    conversationsLoading.value = false
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
  } finally {
    closeSidebar()
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
  closeSidebar()
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
  retrievedChunks.value = []  // 清空上轮检索片段
  chunksExpanded.value = true

  try {
    const response = await sendChatMessage(currentConversationId.value, question, selectedKbId.value ?? undefined)
    const reader = response.body?.getReader()
    if (!reader) throw new Error('无响应流')

    let assistantContent = ''
    let references: Reference[] = []
    let refused = false
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
              retrievedChunks.value = references
            }
            if (currentEvent === 'done') {
              if (data.references) references = data.references
              if (data.refused) refused = true
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
      refused: refused,
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
  conversationsLoading.value = true
  try {
    const { data } = await searchConversations(keyword || undefined)
    conversations.value = data
  } catch (e) {
    console.error('搜索失败', e)
  } finally {
    conversationsLoading.value = false
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
  else if (cmd === 'learn') router.push('/learn')
  else if (cmd === 'quiz') router.push('/quiz')
  else if (cmd === 'writing') router.push('/writing')
  else if (cmd === 'review') router.push('/review-plan')
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
  await loadKbTree()
  const qKb = route.query.kb
  if (qKb) selectedKbId.value = Number(qKb)
  const convId = route.params.conversationId
  if (convId) {
    await selectConversation(Number(convId))
  }
})
</script>

<style scoped>
.chat-layout {
  height: 100vh;
  height: 100dvh;
  display: flex;
  background: var(--bg);
  overflow: hidden;
}

/* ═══════════════════════════════════════════
   左侧会话栏
   ═══════════════════════════════════════════ */
.sidebar {
  width: 300px;
  flex-shrink: 0;
  height: 100%;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base);
  z-index: var(--z-sticky);
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-lg) var(--space-xl);
  border-bottom: 1px solid var(--divider);
}
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}
.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  color: var(--primary-600);
  flex-shrink: 0;
}
.brand-name {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.collapse-btn {
  display: none; /* 仅移动端显示 */
}

.sidebar-actions {
  padding: var(--space-md) var(--space-xl) 0;
}
.new-chat-btn {
  width: 100%;
}

/* ── 模式指示 ── */
.mode-indicator {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin: var(--space-xs) var(--space-xl);
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--surface-2);
  border: 1px solid var(--border);
  font-size: 12.5px;
  cursor: pointer;
  user-select: none;
  transition: background var(--duration-fast), border-color var(--duration-fast), box-shadow var(--duration-fast);
}
.mode-indicator:hover {
  background: var(--surface-3);
  border-color: var(--primary-200);
  box-shadow: var(--shadow-sm);
}
.mode-indicator:focus-visible {
  outline: 2px solid var(--primary-400);
  outline-offset: 2px;
}
.mode-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.mode-dot.online { background: var(--success); box-shadow: 0 0 0 3px var(--success-light); }
.mode-dot.offline { background: var(--primary-600); box-shadow: 0 0 0 3px var(--primary-100); }
.mode-dot.lc4j { background: var(--warning); box-shadow: 0 0 0 3px var(--warning-light); }
.mode-dot.sa { background: var(--primary-600); box-shadow: 0 0 0 3px var(--primary-100); }
.mode-text {
  flex: 1;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mode-switch-icon {
  color: var(--text-muted);
  font-size: 16px;
  flex-shrink: 0;
}
.framework-indicator {
  margin-top: 0;
}

/* ── 知识库选择器 ── */
.kb-selector {
  padding: var(--space-sm) var(--space-xl);
  font-size: var(--text-xs);
}
.kb-selector-label {
  font-size: var(--text-xs);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: var(--text-secondary);
}
.kb-selector-label .el-icon { color: var(--primary-600); }

/* ── RAG 开关 ── */
.rag-toggle {
  padding: var(--space-sm) var(--space-xl);
  font-size: var(--text-xs);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rag-toggle-label { font-weight: 600; color: var(--text-secondary); }

/* ═══════════════════════════════════════════
   会话列表
   ═══════════════════════════════════════════ */
.conversation-section {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  margin-top: var(--space-xs);
}
.conversation-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-xl);
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--text-secondary);
}
.section-title .el-icon { font-size: 15px; color: var(--primary-600); }
.section-count {
  font-size: 11px;
}
.search-box {
  display: flex;
  align-items: center;
  padding: 0 var(--space-xl) var(--space-sm);
}
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--space-md) var(--space-md);
}
.conv-skeleton {
  padding: var(--space-md);
}
.conv-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
}
.empty-icon {
  font-size: 44px;
  color: var(--primary-200);
}
.empty-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}
.empty-desc {
  margin: 0;
  font-size: 12.5px;
  color: var(--text-muted);
  max-width: 220px;
  line-height: 1.5;
}

.conv-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  position: relative;
  border: 1px solid transparent;
  transition: background var(--duration-fast), border-color var(--duration-fast), box-shadow var(--duration-fast);
  margin-bottom: var(--space-xs);
}
.conv-item:hover { background: var(--surface-2); }
.conv-item.active {
  background: var(--primary-50);
  border-color: var(--primary-200);
  box-shadow: var(--shadow-sm);
}
.conv-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  padding-right: 24px;
}
.conv-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-time { font-size: var(--text-xs); color: var(--text-muted); }
.conv-more {
  position: absolute;
  right: 6px;
  top: 10px;
  color: var(--text-muted);
  padding: 4px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.conv-more:hover { background: var(--surface-3); color: var(--text-secondary); }
.pin-icon {
  color: var(--warning);
  display: inline-flex;
  flex-shrink: 0;
}

/* ── 用户信息 ── */
.sidebar-footer {
  padding: var(--space-md) var(--space-xl);
  border-top: 1px solid var(--divider);
}
.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  padding: var(--space-sm);
  border-radius: var(--radius-md);
  transition: background var(--duration-fast);
}
.user-info:hover { background: var(--surface-2); }
.user-info:focus-visible { outline: 2px solid var(--primary-400); outline-offset: 2px; }
.username { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.admin-badge {
  font-size: 10px;
  background: var(--primary-600);
  color: var(--text-inverse);
  padding: 1px 6px;
  border-radius: var(--radius-full);
}

/* ═══════════════════════════════════════════
   中间聊天区
   ═══════════════════════════════════════════ */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: var(--bg);
}
.chat-header {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-xl);
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  min-height: 60px;
}
.menu-toggle {
  display: none; /* 仅移动端显示 */
}
.chat-header-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.chat-title {
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chat-subtitle {
  font-size: var(--text-xs);
  color: var(--text-muted);
}
.chat-header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.empty-chat {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xl) var(--space-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}
.message-item {
  display: flex;
  gap: var(--space-md);
  max-width: 100%;
}
.message-user { flex-direction: row-reverse; }
.message-user .message-body { align-items: flex-end; }
.message-avatar { flex-shrink: 0; }
.ai-avatar {
  background: var(--brand-gradient);
  color: var(--text-inverse);
  font-weight: 700;
  font-size: var(--text-sm);
}
.message-body {
  min-width: 0;
  max-width: 72%;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.message-content {
  padding: var(--space-lg) var(--space-xl);
  border-radius: var(--radius-lg);
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
  transition: box-shadow var(--duration-fast);
  word-break: break-word;
}
.message-content:hover { box-shadow: var(--shadow-md); }
.message-user .message-content {
  background: var(--primary-50);
  border-color: var(--primary-200);
}

/* 引用来源 */
.references-section { margin-top: var(--space-xs); }
.ref-divider-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
}
.ref-divider-label .el-icon { color: var(--primary-600); }
.reference-item { margin: 4px 0; display: inline-block; }
.ref-content { color: var(--text-secondary); font-size: var(--text-sm); line-height: 1.6; }

/* 拒答提示横幅 */
.refuse-banner {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  margin-top: var(--space-xs);
  padding: var(--space-md) var(--space-lg);
  background: var(--warning-light);
  border: 1px solid var(--warning);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--text-primary);
}
.refuse-icon {
  color: var(--warning);
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 1px;
}

/* 消息反馈按钮 */
.feedback-bar {
  display: flex;
  gap: var(--space-xs);
  margin-top: var(--space-xs);
  opacity: 0.75;
  transition: opacity var(--duration-fast);
  flex-wrap: wrap;
}
.feedback-bar:hover { opacity: 1; }
.feedback-bar .el-button { width: auto; }
.feedback-bar .el-button .el-icon { margin-right: 2px; }

/* ═══════════════════════════════════════════
   底部输入区
   ═══════════════════════════════════════════ */
.chat-input-area {
  padding: var(--space-lg) var(--space-2xl);
  background: var(--surface);
  border-top: 1px solid var(--border);
}
.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-sm);
  gap: var(--space-md);
}
.input-hint { font-size: var(--text-xs); color: var(--text-muted); }

/* ═══════════════════════════════════════════
   RAG 推理过程块（Codex 风格推理轨迹）
   ═══════════════════════════════════════════ */
.thinking-block-enter-active { animation: thinkIn 0.3s var(--ease-out); }
.thinking-block-leave-active { animation: thinkOut 0.2s ease-in; }
@keyframes thinkIn {
  from { opacity: 0; transform: translateY(-6px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes thinkOut {
  from { opacity: 1; transform: translateY(0); }
  to   { opacity: 0; transform: translateY(-4px); }
}

.codex-reasoning {
  margin: 0 0 var(--space-lg) 48px; /* 与 AI 消息对齐（avatar 36 + gap 12） */
  max-width: 75%;
  background: linear-gradient(180deg, var(--surface-2), var(--surface-3));
  border: 1px solid var(--border);
  border-left: 3px solid var(--primary-600);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition: all var(--duration-base) var(--ease-out);
  box-shadow: var(--shadow-sm);
}
.codex-reasoning.reasoning-collapsed {
  background: var(--surface-2);
}

.reasoning-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: background var(--duration-fast);
}
.reasoning-header:hover { background: var(--primary-50); }
.reasoning-header:focus-visible { outline: 2px solid var(--primary-400); outline-offset: -2px; }
.reasoning-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.reasoning-spark {
  font-size: 14px;
  color: var(--primary-600);
  flex-shrink: 0;
}
.reasoning-title {
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
}
.reasoning-total {
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  white-space: nowrap;
}
.reasoning-spinner {
  width: 13px; height: 13px;
  border: 2px solid var(--border);
  border-top-color: var(--primary-600);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}
.reasoning-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.reasoning-count {
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}
.reasoning-chevron {
  font-size: 14px;
  color: var(--text-muted);
  transition: transform var(--duration-base) var(--ease-out);
}
.reasoning-chevron.chevron-expanded { transform: rotate(180deg); }

.reasoning-body {
  border-top: 1px solid var(--divider);
  background: var(--surface);
  padding: 4px 14px 10px;
  animation: bodyIn 0.25s ease-out;
}
@keyframes bodyIn {
  from { opacity: 0; }
  to   { opacity: 1; }
}
.reasoning-track {
  position: relative;
  padding-left: 6px;
}
.reasoning-track::before {
  content: '';
  position: absolute;
  left: 14px;
  top: 8px;
  bottom: 14px;
  width: 2px;
  background: linear-gradient(180deg, var(--primary-500), var(--primary-700));
  opacity: 0.35;
  border-radius: 2px;
}

.reasoning-step {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 7px 0;
}
.reasoning-node {
  position: relative;
  z-index: 1;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  margin-top: 1px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.r-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: var(--border);
  border: 2px solid var(--surface);
  box-shadow: 0 0 0 1px var(--border);
}
.r-check {
  width: 18px; height: 18px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 11px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
}
.r-spinner {
  width: 16px; height: 16px;
  border: 2px solid var(--border);
  border-top-color: var(--primary-600);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.reasoning-content { flex: 1; min-width: 0; }

/* ── 检索片段预览面板 ── */
.retrieved-panel {
  margin: 0 18px var(--space-lg);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface-2);
  overflow: hidden;
}
.retrieved-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: background var(--duration-fast);
}
.retrieved-header:hover { background: var(--primary-50); }
.retrieved-header:focus-visible { outline: 2px solid var(--primary-400); outline-offset: -2px; }
.retrieved-icon { color: var(--primary-600); font-size: 16px; flex-shrink: 0; }
.retrieved-title { font-size: var(--text-sm); font-weight: 600; color: var(--text-primary); }
.retrieved-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--primary-700);
  background: var(--primary-50);
  padding: 1px 8px;
  border-radius: var(--radius-full);
}
.retrieved-live {
  font-size: 11px;
  color: var(--success);
  display: flex;
  align-items: center;
}
.retrieved-live::before {
  content: '';
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--success);
  margin-right: 4px;
  animation: dotPulse 1s ease-in-out infinite;
}
.retrieved-chevron {
  margin-left: auto;
  color: var(--text-muted);
  transition: transform var(--duration-base) var(--ease-out);
}
.retrieved-chevron.chevron-expanded { transform: rotate(180deg); }
.retrieved-body {
  border-top: 1px solid var(--divider);
  padding: 4px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
}
.retrieved-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  background: var(--surface);
  transition: box-shadow var(--duration-fast), border-color var(--duration-fast);
}
.retrieved-card:hover {
  border-color: var(--primary-200);
  box-shadow: var(--shadow-sm);
}
.retrieved-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.retrieved-idx {
  flex-shrink: 0;
  width: 18px; height: 18px;
  border-radius: var(--radius-sm);
  background: var(--primary-600);
  color: var(--text-inverse);
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.retrieved-doc {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.retrieved-score {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--success);
  font-variant-numeric: tabular-nums;
}
.retrieved-score-bar {
  height: 4px;
  border-radius: var(--radius-full);
  background: var(--surface-3);
  overflow: hidden;
  margin-bottom: 6px;
}
.retrieved-score-bar > span {
  display: block;
  height: 100%;
  border-radius: var(--radius-full);
  background: var(--brand-gradient);
  transition: width var(--duration-slow) var(--ease-out);
}
.retrieved-snippet {
  margin: 0;
  font-size: var(--text-xs);
  line-height: 1.6;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.r-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  display: block;
}
.step-active .r-label { color: var(--primary-600); }
.r-detail {
  font-size: 11.5px;
  color: var(--text-muted);
  margin-top: 2px;
  display: block;
  line-height: 1.45;
  font-family: var(--font-mono);
  white-space: pre-wrap;
  word-break: break-word;
}
.step-active .r-detail { color: var(--primary-600); }
.step-finished .r-detail { color: var(--text-secondary); }

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes dotPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.7); }
}

/* ═══════════════════════════════════════════
   移动端抽屉遮罩
   ═══════════════════════════════════════════ */
.sidebar-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  z-index: var(--z-dropdown);
  backdrop-filter: blur(1px);
}
.fade-enter-active, .fade-leave-active { transition: opacity var(--duration-base) var(--ease-out); }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ═══════════════════════════════════════════
   响应式
   ═══════════════════════════════════════════ */
/* 平板 ≤768px：左侧栏转为可呼出抽屉 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: 300px;
    max-width: 86vw;
    transform: translateX(-100%);
    box-shadow: var(--shadow-lg);
  }
  .sidebar.sidebar-open { transform: translateX(0); }
  .collapse-btn { display: inline-flex; }
  .menu-toggle { display: inline-flex; }
  .chat-header-actions .new-chat-text { display: none; }
  .chat-header-actions .header-new-chat { padding: 8px; }
  .message-body { max-width: 85%; }
  .message-list { padding: var(--space-lg); }
  .chat-input-area { padding: var(--space-md); }
  .codex-reasoning { margin-left: 0; max-width: 100%; }
}

/* 手机 ≤480px：简化布局，触摸目标 ≥44px */
@media (max-width: 480px) {
  .sidebar { width: 86vw; }
  .message-body { max-width: 100%; }
  .message-list { padding: var(--space-md); gap: var(--space-md); }
  .chat-input-area { padding: var(--space-sm) var(--space-md); }
  .chat-header { padding: var(--space-sm) var(--space-md); min-height: 56px; }
  .input-actions { flex-wrap: wrap; }
  .input-hint { width: 100%; order: -1; }

  /* 触摸目标 ≥44px */
  .menu-toggle,
  .collapse-btn,
  .conv-more,
  .feedback-bar .el-button,
  .chat-input-area .el-button {
    min-height: 44px;
    min-width: 44px;
  }
  .chat-input-area .el-button { width: auto; }
}
</style>

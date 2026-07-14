<template>
  <div class="chat-layout" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
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
        <div class="header-actions">
          <button
            class="header-icon-btn"
            type="button"
            :class="{ active: showSearchInput }"
            aria-label="搜索会话"
            @click="toggleSearch"
          >
            <Search class="icon-md" />
          </button>
          <button
            class="header-icon-btn"
            type="button"
            aria-label="收起会话列表"
            @click="collapseSidebar"
          >
            <PanelLeftClose class="icon-md" />
          </button>
        </div>
      </div>

      <!-- 搜索会话（点击搜索图标展开） -->
      <transition name="search-slide">
        <div v-if="showSearchInput && authStore.username" class="search-panel">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索会话..."
            :prefix-icon="Search"
            clearable
            size="small"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </div>
      </transition>

      <div class="sidebar-actions">
        <button class="new-chat-btn" type="button" @click="newChat" aria-label="新建会话">
          <Plus class="icon-md" />
          <span>开启新对话</span>
        </button>
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

      <!-- 会话列表区域 -->
      <div class="conversation-section">
        <div class="conversation-list">
          <!-- 加载骨架 -->
          <div v-if="conversationsLoading" class="conv-skeleton">
            <el-skeleton :rows="7" animated />
          </div>
          <!-- 空状态 -->
          <div v-else-if="conversations.length === 0" class="ui-empty conv-empty">
            <el-icon class="empty-icon"><MessageCircle /></el-icon>
            <p class="empty-title">还没有会话</p>
            <p class="empty-desc">点击「开启新对话」，开始你的第一次提问</p>
            <el-button type="primary" :icon="Plus" @click="newChat">开启新对话</el-button>
          </div>
          <!-- 列表（按 DeepSeek 风格分组） -->
          <template v-else>
            <div
              v-for="group in groupedConversations"
              :key="group.label"
              class="conv-group"
            >
              <div class="conv-group-label">{{ group.label }}</div>
              <div
                v-for="conv in group.items"
                :key="conv.id"
                class="conv-item"
                :class="{ active: conv.id === currentConversationId }"
                @click="selectConversation(conv.id)"
              >
                <div class="conv-title">
                  <span class="conv-title-text">{{ conv.title }}</span>
                </div>
                <div class="conv-more-wrap" @click.stop>
                  <button
                    class="conv-more-btn"
                    type="button"
                    aria-label="更多操作"
                    @click.stop="openPopover(conv, $event)"
                  >
                    <MoreHorizontal class="icon-sm" />
                  </button>
                </div>
              </div>
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

    <!-- 折叠后浮动「打开」按钮 -->
    <transition name="fade">
      <button
        v-if="sidebarCollapsed"
        class="sidebar-reopen"
        type="button"
        aria-label="打开会话列表"
        @click="toggleSidebar"
      >
        <PanelLeftOpen class="icon-md" />
      </button>
    </transition>

    <!-- 会话操作悬浮菜单（DeepSeek 风格） -->
    <Teleport to="body">
      <div
        v-if="popoverVisible"
        ref="popoverRef"
        class="conv-popover"
        :style="popoverStyle"
        role="menu"
        aria-label="会话操作"
      >
        <div class="conv-popover-item" role="menuitem" @click="handlePopoverAction('rename')">
          <Pencil class="icon-md" />
          <span>重命名</span>
        </div>
        <div class="conv-popover-item" role="menuitem" @click="handlePopoverAction('pin')">
          <Pin class="icon-md" />
          <span>{{ popoverConv?.pinned ? '取消置顶' : '置顶' }}</span>
        </div>
        <div class="conv-popover-item" role="menuitem" @click="handlePopoverAction('share')">
          <Share2 class="icon-md" />
          <span>分享</span>
        </div>
        <div class="conv-popover-item danger" role="menuitem" @click="handlePopoverAction('delete')">
          <Trash2 class="icon-md" />
          <span>删除</span>
        </div>
      </div>
    </Teleport>

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

        <!-- RAG 推理过程块（DeepSeek 风格：已思考 N 秒，可展开/收起） -->
        <Transition name="thinking-block">
          <div v-if="ragStepsVisible && ragSteps.length > 0" class="deepseek-thinking" :class="{ 'thinking-collapsed': !ragStepsExpanded }">
            <!-- 折叠头部 -->
            <button
              class="thinking-header"
              type="button"
              @click="ragStepsExpanded = !ragStepsExpanded"
              :aria-expanded="ragStepsExpanded"
            >
              <div class="thinking-header-main">
                <Sparkles class="thinking-icon icon-md" />
                <span class="thinking-title">{{ thinkingHeaderText }}</span>
                <span v-if="ragTotalTime" class="thinking-time">（用时 {{ reasoningDurationSeconds }} 秒）</span>
                <span v-else-if="isStreaming" class="thinking-time thinking-live">思考中</span>
                <ChevronDown class="thinking-chevron icon-sm" :class="{ 'chevron-expanded': ragStepsExpanded }" />
              </div>
            </button>

            <!-- 展开内容：DeepSeek 风格分点推理 -->
            <div v-show="ragStepsExpanded" class="thinking-body">
              <div class="thinking-content">
                <div
                  v-for="s in ragSteps"
                  :key="s.step"
                  class="thinking-step"
                  :class="{ 'step-running': s.status === 'running', 'step-done': s.status === 'done' }"
                >
                  <span class="thinking-bullet" aria-hidden="true">•</span>
                  <div class="thinking-step-main">
                    <span class="thinking-step-label">{{ s.label }}</span>
                    <span v-if="s.detail" class="thinking-step-detail">{{ s.detail }}</span>
                    <span v-if="s.status === 'running'" class="thinking-step-spinner" aria-hidden="true"></span>
                  </div>
                </div>

                <!-- 参考来源（内嵌在推理过程中） -->
                <div v-if="retrievedChunks.length" class="thinking-sources">
                  <div class="thinking-sources-title">参考来源</div>
                  <div
                    v-for="(c, i) in retrievedChunks"
                    :key="c.chunkId"
                    class="thinking-source-item"
                  >
                    <span class="thinking-source-idx">{{ i + 1 }}</span>
                    <span class="thinking-source-doc" :title="c.documentTitle">{{ c.documentTitle }}</span>
                    <span class="thinking-source-score">{{ (c.score * 100).toFixed(1) }}%</span>
                  </div>
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
  BookOpen, Plus, Send, ArrowDown, Search, FileText, AlertTriangle,
  Library, MessageCircle, Pin, ThumbsUp, ThumbsDown, Copy, Menu, PanelLeftClose, PanelLeftOpen,
  User, BookOpenCheck, PenLine, Pencil, Calendar, Wrench, BarChart3, LogOut, Trash2,
  MoreHorizontal, Share2, Sparkles, ChevronDown
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { getConversations, getConversationMessages, sendMessage as sendChatMessage,
  renameConversation, deleteConversation, feedbackMessage, searchConversations, exportConversation,
  togglePinConversation,
  type Conversation, type Message, type Reference } from '@/api/chat'
import { getAiMode, getAiFramework } from '@/api/aimode'
import { getKbTree, type KbTreeNode } from '@/api/knowledgeBase'
import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/atom-one-dark.css'

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
const showSearchInput = ref(false)

// 会话列表加载状态（用于骨架屏）
const conversationsLoading = ref(false)
// 移动端抽屉开合
const sidebarOpen = ref(false)
// 桌面端侧边栏折叠
const sidebarCollapsed = ref(false)
const popoverVisible = ref(false)
const popoverConv = ref<Conversation | null>(null)
const popoverRef = ref<HTMLElement>()
const popoverStyle = ref({ top: '0px', left: '0px' })

const currentTitle = computed(
  () => conversations.value.find(c => c.id === currentConversationId.value)?.title || ''
)

// 按 DeepSeek 风格分组：置顶、今天、昨天、7 天内、更早
const groupedConversations = computed(() => {
  const groups: { label: string; items: Conversation[] }[] = []
  const pinned = conversations.value.filter(c => c.pinned)
  if (pinned.length) groups.push({ label: '置顶', items: pinned })

  const others = conversations.value.filter(c => !c.pinned)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
  const weekAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000)

  const isToday = (d: Date) => d.getTime() >= today.getTime()
  const isYesterday = (d: Date) => d.getTime() >= yesterday.getTime() && d.getTime() < today.getTime()
  const isWithinWeek = (d: Date) => d.getTime() >= weekAgo.getTime() && d.getTime() < yesterday.getTime()

  const bucket = (label: string, filter: (d: Date) => boolean) => {
    const items = others.filter(c => filter(new Date(c.updatedAt)))
    if (items.length) groups.push({ label, items })
  }
  bucket('今天', isToday)
  bucket('昨天', isYesterday)
  bucket('7 天内', isWithinWeek)
  bucket('更早', (d: Date) => d.getTime() < weekAgo.getTime())

  return groups
})

function toggleSearch() {
  showSearchInput.value = !showSearchInput.value
  if (!showSearchInput.value) {
    searchKeyword.value = ''
    handleSearch()
  }
}

function toggleSidebar() {
  if (window.innerWidth <= 768) {
    sidebarOpen.value = !sidebarOpen.value
  } else {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
}
function collapseSidebar() {
  if (window.innerWidth <= 768) {
    sidebarOpen.value = false
  } else {
    sidebarCollapsed.value = true
  }
}
function closeSidebar() {
  sidebarOpen.value = false
}

// 打开会话操作悬浮菜单
function openPopover(conv: Conversation, event: MouseEvent) {
  popoverConv.value = conv
  const btn = event.currentTarget as HTMLElement
  const rect = btn.getBoundingClientRect()
  const menuWidth = 152
  const menuHeight = 168
  let left = rect.left + rect.width / 2 - menuWidth / 2
  let top = rect.top + rect.height + 6
  // 视口边界保护
  if (left + menuWidth > window.innerWidth - 8) left = window.innerWidth - menuWidth - 8
  if (left < 8) left = 8
  if (top + menuHeight > window.innerHeight - 8) top = rect.top - menuHeight - 6
  popoverStyle.value = { top: `${top}px`, left: `${left}px` }
  popoverVisible.value = true
}
function closePopover() {
  popoverVisible.value = false
  popoverConv.value = null
}
function handlePopoverAction(cmd: string) {
  if (!popoverConv.value) return
  handleConvAction(cmd, popoverConv.value)
  closePopover()
}

// 点击外部关闭菜单
onMounted(() => {
  document.addEventListener('mousedown', (e) => {
    if (popoverVisible.value && popoverRef.value && !popoverRef.value.contains(e.target as Node)) {
      closePopover()
    }
  })
})

// RAG 过程可视化
const ragStepsVisible = ref(true) // 开关
const ragStepsExpanded = ref(true) // 折叠/展开
const ragSteps = ref<Array<{ step: string; status: string; label: string; detail: string }>>([])
const ragTotalTime = ref('')
// 检索到的片段（来自 references 事件），用于推理面板内参考来源
const retrievedChunks = ref<Reference[]>([])
const doneStepCount = computed(() => ragSteps.value.filter(s => s.status === 'done').length)
const reasoningDurationSeconds = computed(() => {
  const match = ragTotalTime.value.match(/(\d+)\s*ms/)
  return match ? (parseInt(match[1], 10) / 1000).toFixed(1) : '0.0'
})
const thinkingHeaderText = computed(() => (isStreaming.value && !ragTotalTime.value ? '思考中' : '已思考'))

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
  const langClass = lang ? ` language-${lang}` : ''
  const highlighted = lang && hljs.getLanguage(lang)
    ? hljs.highlight(text, { language: lang }).value
    : hljs.highlightAuto(text).value
  return `<pre><code class="hljs${langClass}">${highlighted}</code></pre>`
}

marked.setOptions({ breaks: true, gfm: true, renderer })

function renderMarkdown(text: string) {
  if (!text) return ''
  const rawHtml = marked.parse(text) as string
  // 企业级 XSS 防护：AI 生成内容经 v-html 渲染前消毒
  return DOMPurify.sanitize(rawHtml, { USE_PROFILES: { html: true } })
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

// 分享会话链接
function shareConversation(conv: Conversation) {
  const url = `${window.location.origin}/chat/${conv.id}`
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('分享链接已复制')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
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
  } else if (cmd === 'share') {
    shareConversation(conv)
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
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base), width var(--duration-base) var(--ease-out);
  z-index: var(--z-sticky);
}
/* 桌面端折叠：侧边栏宽度归零，聊天区铺满 */
.chat-layout.sidebar-collapsed .sidebar {
  width: 0;
  min-width: 0;
  border-right: none;
  overflow: hidden;
}
/* 折叠后浮动「打开」按钮 */
.sidebar-reopen {
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: var(--z-sticky);
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  background: var(--surface);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: background var(--duration-fast), color var(--duration-fast);
}
.sidebar-reopen:hover {
  background: var(--surface-2);
  color: var(--primary-600);
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px var(--space-lg);
  border-bottom: 1px solid var(--divider);
  gap: var(--space-sm);
}
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}
.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  color: var(--primary-600);
  flex-shrink: 0;
}
.brand-name {
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.header-icon-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.header-icon-btn:hover {
  background: var(--surface-2);
  color: var(--text-secondary);
}
.header-icon-btn.active {
  color: var(--primary-600);
  background: var(--primary-50);
}
.header-icon-btn:focus-visible {
  outline: 2px solid var(--primary-400);
  outline-offset: 2px;
}
.collapse-btn { display: none; /* 已合并到 header-actions */ }

.sidebar-actions {
  padding: var(--space-md) var(--space-lg) 0;
}
.new-chat-btn {
  width: 100%;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  border: 1px solid var(--border);
  border-radius: var(--radius-full);
  background: var(--surface);
  color: var(--text-primary);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--duration-fast), border-color var(--duration-fast), color var(--duration-fast), box-shadow var(--duration-fast);
  box-shadow: var(--shadow-xs);
}
.new-chat-btn:hover {
  background: var(--surface-2);
  border-color: var(--primary-200);
  color: var(--primary-700);
  box-shadow: var(--shadow-sm);
}
.new-chat-btn:focus-visible {
  outline: 2px solid var(--primary-400);
  outline-offset: 2px;
}

.search-panel {
  padding: 0 var(--space-lg) var(--space-sm);
  overflow: hidden;
  transform-origin: top;
}
.search-slide-enter-active,
.search-slide-leave-active {
  transition: all var(--duration-base) var(--ease-out);
}
.search-slide-enter-from,
.search-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  margin-top: 0;
  margin-bottom: 0;
}
.search-slide-enter-to,
.search-slide-leave-from {
  opacity: 1;
  transform: translateY(0);
  max-height: 64px;
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
.conversation-section-head { display: none; }
.section-title { display: none; }
.section-count { display: none; }
.search-box { display: none; }
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-sm) var(--space-sm) var(--space-md);
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

.conv-group { margin-bottom: var(--space-sm); }
.conv-group-label {
  padding: var(--space-sm) var(--space-md) 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.5px;
  user-select: none;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  position: relative;
  border: 1px solid transparent;
  transition: background var(--duration-fast), border-color var(--duration-fast), box-shadow var(--duration-fast);
  margin: 0 var(--space-xs) 2px;
}
.conv-item:hover { background: var(--surface-2); }
.conv-item.active {
  background: var(--primary-50);
  border-color: var(--primary-200);
  box-shadow: var(--shadow-sm);
}
.conv-title {
  flex: 1;
  min-width: 0;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.conv-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-title-text::before {
  content: '';
  display: inline-block;
  width: 0;
}
.conv-more-wrap {
  flex-shrink: 0;
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--duration-fast);
}
.conv-item:hover .conv-more-wrap,
.conv-item.active .conv-more-wrap {
  opacity: 1;
  pointer-events: auto;
}
.conv-more-btn {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.conv-more-btn:hover {
  background: var(--surface-3);
  color: var(--text-secondary);
}
.conv-more-btn:focus-visible {
  outline: 2px solid var(--primary-400);
  outline-offset: 2px;
}

/* 会话操作悬浮菜单 */
.conv-popover {
  position: fixed;
  z-index: 9999;
  min-width: 140px;
  padding: 6px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  animation: popoverIn 0.15s var(--ease-out);
}
@keyframes popoverIn {
  from { opacity: 0; transform: translateY(-4px) scale(0.98); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}
.conv-popover-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--text-primary);
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.conv-popover-item:hover {
  background: var(--surface-2);
}
.conv-popover-item.danger {
  color: #dc2626;
}
.conv-popover-item.danger:hover {
  background: #fee2e2;
}
.conv-popover-item svg {
  width: 16px;
  height: 16px;
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
  display: inline-flex; /* 桌面端也可收起/展开侧边栏 */
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
   RAG 推理过程块（DeepSeek 风格：已思考 N 秒）
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

.deepseek-thinking {
  margin: 0 0 var(--space-lg) 48px; /* 与 AI 消息对齐（avatar 36 + gap 12） */
  max-width: 75%;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--duration-base) var(--ease-out);
  box-shadow: var(--shadow-sm);
}
.deepseek-thinking.thinking-collapsed {
  background: var(--surface-2);
}

.thinking-header {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 10px 14px;
  border: none;
  background: transparent;
  cursor: pointer;
  user-select: none;
  text-align: left;
  transition: background var(--duration-fast);
  font-family: inherit;
}
.thinking-header:hover { background: var(--primary-50); }
.thinking-header:focus-visible { outline: 2px solid var(--primary-400); outline-offset: -2px; }
.thinking-header-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.thinking-icon {
  color: var(--primary-600);
  flex-shrink: 0;
}
.thinking-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}
.thinking-time {
  font-size: var(--text-xs);
  color: var(--text-muted);
  white-space: nowrap;
  font-family: var(--font-mono);
}
.thinking-time.thinking-live {
  color: var(--primary-600);
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.thinking-time.thinking-live::after {
  content: '';
  width: 5px; height: 5px;
  border-radius: 50%;
  background: currentColor;
  animation: dotPulse 1.2s ease-in-out infinite;
}
.thinking-chevron {
  color: var(--text-muted);
  flex-shrink: 0;
  margin-left: auto;
  transition: transform var(--duration-base) var(--ease-out);
}
.thinking-chevron.chevron-expanded { transform: rotate(180deg); }

.thinking-body {
  padding: 2px 14px 14px 20px;
  animation: bodyIn 0.25s ease-out;
}
@keyframes bodyIn {
  from { opacity: 0; }
  to   { opacity: 1; }
}
.thinking-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;
  border-top: 1px solid var(--divider);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.7;
}

.thinking-step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  position: relative;
}
.thinking-step.step-running .thinking-step-label { color: var(--primary-700); }
.thinking-step.step-done .thinking-step-label { color: var(--text-primary); }
.thinking-bullet {
  color: var(--primary-400);
  font-size: 16px;
  line-height: 1.5;
  flex-shrink: 0;
  margin-top: -1px;
}
.thinking-step-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  flex: 1;
  min-width: 0;
}
.thinking-step-label {
  font-weight: 500;
  color: var(--text-secondary);
}
.thinking-step-detail {
  color: var(--text-muted);
  font-weight: 400;
}
.thinking-step-spinner {
  width: 12px; height: 12px;
  border: 1.5px solid var(--border);
  border-top-color: var(--primary-600);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
  margin-left: 2px;
}

/* 参考来源（内嵌） */
.thinking-sources {
  margin-top: var(--space-xs);
  padding: var(--space-sm) var(--space-md);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
}
.thinking-sources-title {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 2px;
}
.thinking-source-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--text-xs);
  color: var(--text-secondary);
}
.thinking-source-idx {
  width: 16px; height: 16px;
  border-radius: var(--radius-sm);
  background: var(--primary-100);
  color: var(--primary-700);
  font-weight: 700;
  font-size: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.thinking-source-doc {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.thinking-source-score {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  flex-shrink: 0;
}



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
  /* 触摸设备无 hover，常驻显示更多按钮 */
  .conv-more-wrap { opacity: 1; pointer-events: auto; }
  .message-body { max-width: 85%; }
  .message-list { padding: var(--space-lg); }
  .chat-input-area { padding: var(--space-md); }
  .deepseek-thinking { margin-left: 0; max-width: 100%; }
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
  .conv-more-btn,
  .header-icon-btn,
  .new-chat-btn,
  .feedback-bar .el-button,
  .chat-input-area .el-button {
    min-height: 44px;
    min-width: 44px;
  }
  .new-chat-btn,
  .chat-input-area .el-button { width: auto; }
}
</style>

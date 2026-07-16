<template>
  <div class="notif-bell" ref="rootEl">
    <button
      class="notif-btn"
      type="button"
      aria-label="通知中心"
      @click="toggle"
    >
      <Bell class="icon-md" />
      <span v-if="unread > 0" class="notif-badge">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <transition name="pop">
      <div v-if="open" class="notif-panel" role="menu" aria-label="通知列表">
        <div class="notif-panel-head">
          <span class="notif-panel-title">通知中心</span>
          <button
            v-if="unread > 0"
            class="notif-markall"
            type="button"
            @click="handleMarkAll"
          >全部已读</button>
        </div>

        <div class="notif-list">
          <div v-if="list.length === 0" class="notif-empty">
            <Inbox class="icon-xl" />
            <p>暂无通知</p>
          </div>

          <div
            v-for="n in list"
            :key="n.id"
            class="notif-item"
            :class="{ unread: !n.isRead }"
            @click="handleClick(n)"
          >
            <div class="notif-icon" :class="'type-' + n.type">
              <component :is="iconOf(n.type)" class="icon-md" />
            </div>
            <div class="notif-body">
              <div class="notif-row">
                <span class="notif-item-title">{{ n.title }}</span>
                <span class="notif-time">{{ fromNow(n.createdAt) }}</span>
              </div>
              <p class="notif-content">{{ n.content }}</p>
            </div>
            <span v-if="!n.isRead" class="notif-dot" />
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { Bell, Inbox, FileCheck, BellRing, Megaphone, type LucideIcon } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import {
  getNotifications,
  getUnreadCount,
  markRead,
  markAllRead,
  type AppNotification
} from '@/api/notifications'

const list = ref<AppNotification[]>([])
const unread = ref(0)
const open = ref(false)
const rootEl = ref<HTMLElement | null>(null)

let reader: ReadableStreamDefaultReader<Uint8Array> | null = null
let abort: AbortController | null = null
let reconnectTimer: number | null = null
let closedByUser = false

const iconMap: Record<string, LucideIcon> = {
  document_processed: FileCheck,
  study_reminder: BellRing,
  system_announcement: Megaphone
}
function iconOf(type: string): LucideIcon {
  return iconMap[type] || BellRing
}

function toggle() {
  open.value = !open.value
  if (open.value) load()
}

async function load() {
  try {
    const [r1, r2] = await Promise.all([getNotifications(), getUnreadCount()])
    const items: AppNotification[] = r1.data?.data ?? r1.data ?? []
    list.value = items
    unread.value = (r2.data?.data?.count ?? r2.data?.count ?? 0) as number
  } catch {
    /* ignore */
  }
}

async function handleClick(n: AppNotification) {
  if (!n.isRead) {
    try {
      await markRead(n.id)
      n.isRead = true
      unread.value = Math.max(0, unread.value - 1)
    } catch {
      /* ignore */
    }
  }
}

async function handleMarkAll() {
  try {
    await markAllRead()
    list.value.forEach(n => (n.isRead = true))
    unread.value = 0
  } catch {
    /* ignore */
  }
}

function handleStreamEvent(event: string, data: string) {
  if (!data) return
  try {
    const payload = JSON.parse(data)
    if (event === 'init') {
      unread.value = payload.unread ?? unread.value
    } else if (event === 'notification') {
      const n = payload as AppNotification
      list.value = [n, ...list.value].slice(0, 50)
      unread.value += 1
      ElMessage.info(`🔔 ${n.title}`)
    }
  } catch {
    /* ignore parse */
  }
}

async function openStream() {
  if (closedByUser) return
  const token = localStorage.getItem('accessToken')
  if (!token) return
  const base = (request.defaults.baseURL as string) || 'http://localhost:9090/api'
  abort = new AbortController()
  try {
    const resp = await fetch(`${base}/notifications/stream`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: abort.signal
    })
    if (!resp.body) return
    reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      let idx
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const chunk = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        const ev = /event:(\S+)/.exec(chunk)
        const da = /data:(.*)/.exec(chunk)
        if (ev && da) handleStreamEvent(ev[1], da[1].trim())
      }
    }
  } catch {
    /* connection lost */
  } finally {
    reader = null
    // 自动重连（5s 后），用户未主动关闭且仍登录
    if (!closedByUser && localStorage.getItem('accessToken')) {
      reconnectTimer = window.setTimeout(openStream, 5000)
    }
  }
}

function onClickOutside(e: MouseEvent) {
  if (rootEl.value && !rootEl.value.contains(e.target as Node)) open.value = false
}

function fromNow(iso: string): string {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  const diff = Date.now() - t
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  return `${d} 天前`
}

const _ = computed(() => unread.value) // keep reactive

onMounted(() => {
  load()
  openStream()
  document.addEventListener('click', onClickOutside, true)
})

onBeforeUnmount(() => {
  closedByUser = true
  document.removeEventListener('click', onClickOutside, true)
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (abort) abort.abort()
  if (reader) reader.cancel().catch(() => {})
})
</script>

<style scoped>
.notif-bell { position: relative; display: inline-flex; }

.notif-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-secondary);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.notif-btn:hover { background: var(--surface-3); color: var(--text-primary); }

.notif-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--danger-500, #ef4444);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
  border: 2px solid var(--surface);
}

.notif-panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 340px;
  max-height: 460px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: var(--z-dropdown);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.notif-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--border);
}
.notif-panel-title { font-weight: 700; color: var(--text-primary); font-size: 14px; }
.notif-markall {
  border: none; background: transparent; color: var(--primary-600);
  font-size: 12px; cursor: pointer; padding: 4px 6px; border-radius: var(--radius-sm);
}
.notif-markall:hover { background: var(--surface-3); }

.notif-list { overflow-y: auto; padding: var(--space-sm); }

.notif-empty {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: var(--space-3xl) 0; color: var(--text-muted);
}
.notif-empty p { font-size: 13px; margin: 0; }

.notif-item {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  position: relative;
  transition: background var(--duration-fast);
}
.notif-item:hover { background: var(--surface-3); }
.notif-item.unread { background: color-mix(in srgb, var(--primary-500, #2563eb) 7%, transparent); }

.notif-icon {
  flex-shrink: 0;
  width: 34px; height: 34px;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-md);
  color: #fff;
}
.notif-icon.type-document_processed { background: var(--primary-600, #2563eb); }
.notif-icon.type-study_reminder { background: #f59e0b; }
.notif-icon.type-system_announcement { background: #8b5cf6; }

.notif-body { flex: 1; min-width: 0; }
.notif-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.notif-item-title { font-weight: 600; font-size: 13px; color: var(--text-primary); }
.notif-time { font-size: 11px; color: var(--text-muted); flex-shrink: 0; }
.notif-content {
  margin: 4px 0 0; font-size: 12px; color: var(--text-secondary);
  line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden;
}
.notif-dot {
  position: absolute; top: 14px; right: 10px;
  width: 8px; height: 8px; border-radius: 50%; background: var(--primary-600, #2563eb);
}

.pop-enter-active, .pop-leave-active { transition: opacity var(--duration-fast), transform var(--duration-fast); }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateY(-6px); }
</style>

<template>
  <div class="admin-shell">
    <!-- 移动端遮罩 -->
    <transition name="fade">
      <div v-if="sidebarOpen" class="sidebar-backdrop" @click="sidebarOpen = false" />
    </transition>

    <aside class="admin-sidebar" :class="{ 'is-open': sidebarOpen }">
      <div class="admin-brand">
        <BookOpen class="logo" />
        <span class="name">知识库后台</span>
      </div>
      <el-menu
        :default-active="route.path"
        router
        class="admin-menu"
        @select="sidebarOpen = false"
      >
        <el-menu-item index="/admin/knowledge">
          <el-icon><FileText /></el-icon><span>文档管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/kb">
          <el-icon><Library /></el-icon><span>知识库与标签</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon><span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/dashboard">
          <el-icon><LineChart /></el-icon><span>系统概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/conversation-config">
          <el-icon><MessageSquare /></el-icon><span>对话配置</span>
        </el-menu-item>
        <el-menu-item index="/admin/evaluation">
          <el-icon><BarChart3 /></el-icon><span>效果评估</span>
        </el-menu-item>
        <el-divider />
        <el-menu-item index="/chat">
          <el-icon><MessageCircle /></el-icon><span>返回对话</span>
        </el-menu-item>
        <el-menu-item index="/learn">
          <el-icon><BookOpenCheck /></el-icon><span>学习中心</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div class="crumb">
          <button
            class="hamburger"
            type="button"
            aria-label="切换导航菜单"
            @click="sidebarOpen = !sidebarOpen"
          >
            <el-icon><Menu /></el-icon>
          </button>
          <span class="crumb-title">{{ currentTitle }}</span>
        </div>
        <div class="topbar-right">
          <NotificationBell />
          <el-input
            v-model="globalSearch"
            placeholder="全局搜索文档..."
            :prefix-icon="Search"
            clearable
            size="small"
            class="topbar-search"
            @keyup.enter="doGlobalSearch"
            @clear="doGlobalSearch"
          />
          <el-dropdown trigger="click" @command="handleUserAction">
          <div class="user-chip" role="button" tabindex="0" aria-label="用户菜单">
            <el-avatar :size="30" :src="sidebarAvatar"><User /></el-avatar>
            <span class="user-name">{{ authStore.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="learn">学习中心</el-dropdown-item>
              <el-dropdown-item command="chat">对话问答</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        </div>
      </header>
      <main class="admin-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { resolveFileUrl } from '@/api/user'
import { BookOpen, FileText, Library, User, LineChart, MessageCircle, BookOpenCheck, Menu, ArrowDown, MessageSquare, Search, BarChart3 } from 'lucide-vue-next'
import NotificationBell from '@/components/NotificationBell.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const sidebarOpen = ref(false)

/** 侧边栏用户头像（有上传头像则显示，否则回退到图标） */
const sidebarAvatar = computed(() => resolveFileUrl(authStore.user?.avatar))
const globalSearch = ref('')

function doGlobalSearch() {
  const q = globalSearch.value.trim()
  if (q) router.push(`/admin/knowledge?search=${encodeURIComponent(q)}`)
  else router.push('/admin/knowledge')
}

const currentTitle = computed(() => (route.meta.title as string) || '管理后台')

function handleUserAction(cmd: string) {
  if (cmd === 'logout') {
    authStore.logout()
    router.push('/login')
  } else if (cmd === 'learn') {
    router.push('/learn')
  } else if (cmd === 'chat') {
    router.push('/chat')
  }
}
</script>

<style scoped>
.admin-shell { height: 100vh; display: flex; background: var(--bg); }

.admin-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  padding: var(--space-md) 0;
  z-index: var(--z-modal);
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--space-lg) var(--space-xl) var(--space-lg);
  font-weight: 800;
  font-size: 17px;
  color: var(--text-primary);
}
.admin-brand .logo { width: 26px; height: 26px; color: var(--primary-600); }

.admin-menu { border-right: none; flex: 1; }

.admin-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

.admin-topbar {
  height: 56px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-2xl);
  position: sticky;
  top: 0;
  gap: var(--space-md);
  z-index: var(--z-sticky);
}
.topbar-right { display: flex; align-items: center; gap: var(--space-md); }
.topbar-search { width: 240px; }

.crumb { display: flex; align-items: center; gap: var(--space-sm); }
.crumb-title { font-weight: 600; color: var(--text-primary); font-size: var(--text-base); }

.hamburger {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  transition: background var(--duration-fast);
}
.hamburger:hover { background: var(--surface-3); }

.user-chip {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-primary);
  padding: var(--space-xs) var(--space-sm);
  border-radius: var(--radius-md);
  transition: background var(--duration-fast);
}
.user-chip:hover { background: var(--surface-3); }
.user-name { font-weight: 500; }

.sidebar-backdrop {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: var(--z-modal-backdrop);
}

.admin-content { flex: 1; overflow-y: auto; padding: var(--space-3xl) var(--space-2xl); }

.fade-enter-active, .fade-leave-active { transition: opacity var(--duration-base); }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ── 平板：侧边栏折叠为抽屉 ── */
@media (max-width: 768px) {
  .hamburger { display: inline-flex; }
  .sidebar-backdrop { display: block; }

  .admin-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    box-shadow: var(--shadow-lg);
    transform: translateX(-100%);
    transition: transform var(--duration-base) var(--ease-out);
  }
  .admin-sidebar.is-open { transform: translateX(0); }

  .admin-content { padding: var(--space-2xl) var(--space-lg); }
  .admin-topbar { padding: 0 var(--space-lg); }
}

/* ── 手机：紧凑间距 ── */
@media (max-width: 480px) {
  .admin-content { padding: var(--space-lg) var(--space-md); }
  .user-name { display: none; }
  /* 触摸目标 ≥44px */
  .hamburger { width: 44px; height: 44px; }
}
</style>

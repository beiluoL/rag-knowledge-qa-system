import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guest: true }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/chat/:conversationId',
    name: 'ChatConversation',
    component: () => import('@/views/ChatView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/quiz',
    name: 'Quiz',
    component: () => import('@/views/QuizView.vue'),
    meta: { requiresAuth: true, title: '智能出题' }
  },
  {
    path: '/writing',
    name: 'Writing',
    component: () => import('@/views/WritingView.vue'),
    meta: { requiresAuth: true, title: '智能写作' }
  },
  {
    path: '/review-plan',
    name: 'ReviewPlan',
    component: () => import('@/views/ReviewPlanView.vue'),
    meta: { requiresAuth: true, title: '复习计划' }
  },
  {
    path: '/learn',
    name: 'Learn',
    component: () => import('@/views/LearningDashboardView.vue'),
    meta: { requiresAuth: true, title: '学习中心' }
  },
  {
    path: '/learn/cards',
    name: 'StudyCards',
    component: () => import('@/views/StudyCardsBrowseView.vue'),
    meta: { requiresAuth: true, title: '学习卡片库' }
  },
  {
    path: '/learn/card/:kbId/:index',
    name: 'StudyCardDetail',
    component: () => import('@/views/StudyCardDetailView.vue'),
    meta: { requiresAuth: true, title: '卡片详情' }
  },
  {
    path: '/learn/code',
    name: 'CodePractice',
    component: () => import('@/views/CodePracticeView.vue'),
    meta: { requiresAuth: true, title: '代码练习' }
  },
  {
    path: '/learn/paths',
    name: 'LearningPaths',
    component: () => import('@/views/LearningPathView.vue'),
    meta: { requiresAuth: true, title: '学习路径' }
  },
  {
    path: '/learn/paths/:id',
    name: 'LearningPathDetail',
    component: () => import('@/views/LearningPathDetailView.vue'),
    meta: { requiresAuth: true, title: '学习路径详情' }
  },
  {
    path: '/learn/:mode',
    name: 'LearnMode',
    component: () => import('@/views/LearningModeView.vue'),
    meta: { requiresAuth: true, title: '学习模式' }
  },
  {
    path: '/admin',
    component: () => import('@/views/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    redirect: '/admin/knowledge',
    children: [
      {
        path: 'knowledge',
        name: 'AdminKnowledge',
        component: () => import('@/views/AdminKnowledgeView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '文档管理' }
      },
      {
        path: 'kb',
        name: 'KnowledgeBaseManage',
        component: () => import('@/views/KnowledgeBaseManageView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '知识库与标签' }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/AdminUsersView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '用户管理' }
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/AdminDashboardView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '系统概览' }
      },
      {
        path: 'conversation-config',
        name: 'AdminConversationConfig',
        component: () => import('@/views/AdminChatConfigView.vue'),
        meta: { requiresAuth: true, requiresAdmin: true, title: '对话配置' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  // 刷新后 token 存在但用户信息丢失时，先恢复会话（确保管理员路由判定正确）
  if (authStore.token && !authStore.user) {
    try {
      await authStore.fetchProfile()
    } catch {
      authStore.clearSession()
    }
  }
  const isAuthenticated = authStore.isLoggedIn
  const isAdmin = authStore.isAdmin

  if (to.meta.requiresAuth && !isAuthenticated) return '/login'
  if (to.meta.guest && isAuthenticated) return '/chat'
  if (to.meta.requiresAdmin && !isAdmin) return '/chat'
  return true
})

export default router

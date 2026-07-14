<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">系统概览</h1>
        <p class="page-subtitle">平台运行关键指标与近期管理员操作记录。</p>
      </div>
      <el-button :icon="MessageCircle" @click="router.push('/chat')">返回对话</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div v-for="item in statCards" :key="item.key" class="ui-stat-card">
        <template v-if="loadingStats">
          <el-skeleton animated style="width: 100%">
            <template #template>
              <div class="skeleton-stat">
                <el-skeleton-item variant="circle" style="width: 48px; height: 48px" />
                <div class="skeleton-stat-text">
                  <el-skeleton-item variant="text" style="width: 60%" />
                  <el-skeleton-item variant="text" style="width: 40%" />
                </div>
              </div>
            </template>
          </el-skeleton>
        </template>
        <template v-else>
          <div class="ui-stat-icon" :class="`tone-${item.tone}`">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div>
            <div class="ui-stat-value">{{ stats[item.key as keyof typeof stats] ?? 0 }}</div>
            <div class="ui-stat-label">{{ item.label }}</div>
          </div>
        </template>
      </div>
    </div>

    <!-- 操作日志 -->
    <el-card class="log-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">操作日志</span>
          <el-button @click="loadLogs" :icon="RefreshCw" size="small" :loading="loadingLogs">刷新</el-button>
        </div>
      </template>
      <el-table :data="logs" stripe v-loading="loadingLogs">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="action" label="操作" width="120">
          <template #default="{ row }">
            <el-tag :type="actionType(row.action)" size="small">{{ actionText(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="对象类型" width="100" />
        <el-table-column prop="targetId" label="对象ID" width="80" />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="130" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无操作日志" />
        </template>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="totalLogs"
          layout="total, prev, pager, next"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  RefreshCw, MessageCircle, User, FileText, LayoutGrid, MessageSquare
} from 'lucide-vue-next'
import { getSystemStats, getOperationLogs, type SystemStats, type OperationLog } from '@/api/admin'

const router = useRouter()

const stats = reactive<SystemStats>({
  totalUsers: 0, totalDocuments: 0, totalChunks: 0,
  totalConversations: 0, totalMessages: 0
})

const loadingStats = ref(true)

const statCards = [
  { key: 'totalUsers', label: '用户总数', icon: User, tone: 'primary' },
  { key: 'totalDocuments', label: '文档总数', icon: FileText, tone: 'success' },
  { key: 'totalChunks', label: '分块总数', icon: LayoutGrid, tone: 'warning' },
  { key: 'totalConversations', label: '会话总数', icon: MessageCircle, tone: 'danger' },
  { key: 'totalMessages', label: '消息总数', icon: MessageSquare, tone: 'info' },
] as const

const logs = ref<OperationLog[]>([])
const loadingLogs = ref(false)
const currentPage = ref(1)
const pageSize = 20
const totalLogs = ref(0)

/** 操作类型标签颜色 */
function actionType(action: string) {
  const map: Record<string, string> = {
    UPLOAD_DOC: 'success', DELETE_DOC: 'danger', TOGGLE_USER: 'warning',
    UPDATE_ROLE: 'warning', CLEAR_KB: 'danger', LOGIN: 'info'
  }
  return map[action] || 'info'
}

/** 操作类型中文文本 */
function actionText(action: string) {
  const map: Record<string, string> = {
    UPLOAD_DOC: '上传文档', DELETE_DOC: '删除文档', TOGGLE_USER: '启用/禁用',
    UPDATE_ROLE: '修改角色', CLEAR_KB: '清空知识库', LOGIN: '登录',
    LOGOUT: '退出', UPDATE_PROFILE: '编辑资料'
  }
  return map[action] || action
}

/** 格式化时间 */
function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

/** 加载系统统计数据 */
async function loadStats() {
  loadingStats.value = true
  try {
    const { data } = await getSystemStats()
    Object.assign(stats, data)
  } catch (e) {
    console.error('加载统计数据失败', e)
  } finally {
    loadingStats.value = false
  }
}

/** 加载操作日志（分页） */
async function loadLogs() {
  loadingLogs.value = true
  try {
    const { data } = await getOperationLogs(currentPage.value - 1, pageSize)
    logs.value = data.content
    totalLogs.value = data.totalElements
  } catch (e) {
    console.error('加载操作日志失败', e)
  } finally {
    loadingLogs.value = false
  }
}

/** 分页切换 */
function onPageChange(page: number) {
  currentPage.value = page
  loadLogs()
}

onMounted(() => {
  loadStats()
  loadLogs()
})
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--space-lg);
  margin-bottom: var(--space-2xl);
}

.ui-stat-icon :deep(.el-icon) { font-size: 1.4rem; }
.tone-primary { background: var(--primary-50); color: var(--primary-600); }
.tone-success { background: var(--success-light); color: var(--success); }
.tone-warning { background: var(--warning-light); color: var(--warning); }
.tone-danger { background: var(--danger-light); color: var(--danger); }
.tone-info { background: var(--info-light); color: var(--info); }

.skeleton-stat { display: flex; align-items: center; gap: var(--space-md); }
.skeleton-stat-text { flex: 1; display: flex; flex-direction: column; gap: var(--space-sm); }

.log-card { background: var(--surface); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-weight: 600; font-size: 1rem; color: var(--text-primary); }
.pagination-wrap { margin-top: var(--space-lg); display: flex; justify-content: flex-end; }

@media (max-width: 1024px) {
  .stat-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 768px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 480px) {
  .stat-grid { grid-template-columns: 1fr; }
  /* 触摸目标 ≥44px */
  .el-button { min-height: 44px; }
}
</style>

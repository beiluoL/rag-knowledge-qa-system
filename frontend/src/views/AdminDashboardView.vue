<template>
  <div class="chat-layout">
    <aside class="sidebar" :style="{ width: '280px' }">
      <div class="sidebar-header"><h3>📊 系统管理</h3></div>
      <nav class="admin-nav">
        <el-button @click="router.push('/admin/knowledge')" text style="width:100%;justify-content:flex-start">📚 知识库管理</el-button>
        <el-button @click="router.push('/admin/users')" text style="width:100%;justify-content:flex-start">👥 用户管理</el-button>
        <el-button type="primary" text style="width:100%;justify-content:flex-start">📊 系统仪表板</el-button>
      </nav>
      <div class="sidebar-footer">
        <el-button @click="router.push('/chat')" style="width:100%">← 返回对话</el-button>
      </div>
    </aside>
    <main class="admin-main">
      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :xs="12" :sm="8" :md="4" v-for="item in statCards" :key="item.key">
          <el-card shadow="hover" class="stat-card" :style="{ borderTop: `3px solid ${item.color}` }">
            <div class="stat-icon">{{ item.icon }}</div>
            <div class="stat-value">{{ stats[item.key as keyof typeof stats] ?? 0 }}</div>
            <div class="stat-label">{{ item.label }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 操作日志 -->
      <el-card style="margin-top:20px">
        <template #header>
          <div class="card-header">
            <span>📋 操作日志</span>
            <el-button @click="loadLogs" :icon="Refresh" size="small">刷新</el-button>
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
        </el-table>
        <div class="pagination-wrap">
          <el-pagination v-model:current-page="currentPage" :page-size="pageSize"
            :total="totalLogs" layout="total, prev, pager, next" @current-change="onPageChange" />
        </div>
      </el-card>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getSystemStats, getOperationLogs, type SystemStats, type OperationLog } from '@/api/admin'

const router = useRouter()

const stats = reactive<SystemStats>({
  totalUsers: 0, totalDocuments: 0, totalChunks: 0,
  totalConversations: 0, totalMessages: 0
})

const statCards = [
  { key: 'totalUsers', label: '用户总数', icon: '👥', color: '#409eff' },
  { key: 'totalDocuments', label: '文档总数', icon: '📄', color: '#67c23a' },
  { key: 'totalChunks', label: '分块总数', icon: '🧩', color: '#e6a23c' },
  { key: 'totalConversations', label: '会话总数', icon: '💬', color: '#f56c6c' },
  { key: 'totalMessages', label: '消息总数', icon: '📨', color: '#909399' },
]

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
  try {
    const { data } = await getSystemStats()
    Object.assign(stats, data)
  } catch (e) {
    console.error('加载统计数据失败', e)
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
.chat-layout { height: 100vh; display: flex; }
.sidebar { background: #fff; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column; }
.sidebar-header { padding: 16px; border-bottom: 1px solid #ebeef5; }
.sidebar-footer { padding: 16px; border-top: 1px solid #ebeef5; margin-top: auto; }
.admin-nav { padding: 8px; display: flex; flex-direction: column; gap: 4px; }
.admin-main { flex: 1; padding: 24px; overflow-y: auto; background: #f5f7fa; }

.stats-row { margin-bottom: 8px; }
.stat-card { text-align: center; padding: 8px 0; }
.stat-icon { font-size: 32px; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }

.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>

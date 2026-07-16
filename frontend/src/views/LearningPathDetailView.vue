<template>
  <div class="page-container">
    <div v-if="loading" class="ui-loading" role="status" aria-live="polite">
      <el-icon class="is-loading loading-spin icon-md"><Loader2 /></el-icon>
      <span>正在加载学习路径…</span>
    </div>

    <template v-else-if="detail">
      <!-- 头部 -->
      <div class="path-head ui-card">
        <button class="back-btn" type="button" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回
        </button>
        <div class="path-head-main">
          <h1 class="page-title">{{ detail.title }}</h1>
          <p class="page-subtitle">{{ detail.description || '学习路线' }}</p>
        </div>
        <el-button :icon="Trash2" plain type="danger" @click="confirmDelete">删除路径</el-button>
      </div>

      <!-- 完成度 -->
      <section class="ui-card progress-card">
        <div class="progress-top">
          <span class="progress-label">整体进度</span>
          <span class="progress-num">{{ detail.progress.completed }} / {{ detail.progress.total }} 完成</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: (detail.progress.percent * 100) + '%' }"></div>
        </div>
        <div class="progress-tags">
          <el-tag v-if="detail.progress.status === 'COMPLETED'" type="success" effect="light">已通关</el-tag>
          <el-tag v-else-if="detail.progress.status === 'IN_PROGRESS'" type="warning" effect="light">进行中</el-tag>
          <el-tag v-else-if="detail.progress.status === 'EMPTY'" type="info" effect="light">暂无节点</el-tag>
          <el-tag v-else type="info" effect="light">未开始</el-tag>
          <span class="progress-pct">{{ Math.round(detail.progress.percent * 100) }}%</span>
        </div>
      </section>

      <!-- 节点列表 -->
      <div v-if="detail.nodes.length === 0" class="ui-empty">
        <el-icon class="empty-icon"><Route /></el-icon>
        <p>该路径还没有节点。回到学习路径列表，用「从知识库生成」自动填充有序节点。</p>
      </div>

      <ol v-else class="node-list">
        <li
          v-for="(node, i) in detail.nodes"
          :key="node.id"
          class="ui-card node-item"
          :class="{ done: node.status === 'COMPLETED', active: node.status === 'IN_PROGRESS' }"
          draggable="true"
          @dragstart="onDragStart($event, i)"
          @dragover.prevent
          @drop="onDrop($event, i)"
        >
          <div class="node-index drag-handle" :class="{ dragging: dragIdx === i }">{{ i + 1 }}</div>
          <div class="node-body">
            <div class="node-title-row">
              <h3 class="node-title">{{ node.title }}</h3>
              <el-tag :type="statusType(node.status)" effect="light" size="small" class="node-status">
                {{ statusText(node.status) }}
              </el-tag>
            </div>
            <p v-if="node.description" class="node-desc">{{ node.description }}</p>
            <div v-if="node.status === 'COMPLETED' && node.completedAt" class="node-done-at">
              完成于 {{ formatDate(node.completedAt) }}
            </div>
          </div>
          <div class="node-actions">
            <el-button
              v-if="node.status !== 'COMPLETED'"
              type="primary" size="small"
              @click="goLearn(detail!.id, node.id)"
            >进入学习</el-button>
            <el-button
              v-if="node.status !== 'COMPLETED'"
              size="small" type="success" plain
              @click="setStatus(node, 'COMPLETED')"
            >标记完成</el-button>
            <el-button
              v-if="node.status === 'COMPLETED'"
              size="small" plain
              @click="setStatus(node, 'NOT_STARTED')"
            >重新学习</el-button>
            <el-button
              v-if="node.status === 'IN_PROGRESS'"
              size="small" plain
              @click="setStatus(node, 'NOT_STARTED')"
            >重置</el-button>
          </div>
        </li>
      </ol>
    </template>

    <el-alert v-else type="error" :closable="false" show-icon :title="error || '路径不存在'">
      <template #default>
        <div class="error-box">
          <span>{{ error || '路径不存在或已被删除' }}</span>
          <el-button size="small" type="primary" plain @click="load">重试</el-button>
        </div>
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Loader2, Trash2, ArrowLeft, Route, BookOpen
} from 'lucide-vue-next'
import {
  getLearningPathDetail, updateNodeProgress, deleteLearningPath, reorderNodes,
  type LearningPathDetail as Detail, type NodeStatus
} from '@/api/learningPath'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const detail = ref<Detail | null>(null)
const dragIdx = ref(-1)

function onDragStart(e: DragEvent, idx: number) {
  dragIdx.value = idx
  e.dataTransfer!.effectAllowed = 'move'
}

async function onDrop(e: DragEvent, targetIdx: number) {
  const fromIdx = dragIdx.value
  if (fromIdx < 0 || fromIdx === targetIdx || !detail.value) return
  const nodes = [...detail.value.nodes]
  const [moved] = nodes.splice(fromIdx, 1)
  nodes.splice(targetIdx, 0, moved)
  detail.value.nodes = nodes
  dragIdx.value = -1
  // 持久化
  const orders = nodes.map((n, i) => ({ id: n.id as number, orderIndex: i }))
  try { await reorderNodes(detail.value.id, orders) } catch { /* silent */ }
}

function statusType(s: NodeStatus): 'info' | 'warning' | 'success' {
  return s === 'COMPLETED' ? 'success' : s === 'IN_PROGRESS' ? 'warning' : 'info'
}
function statusText(s: NodeStatus) {
  return s === 'COMPLETED' ? '已完成' : s === 'IN_PROGRESS' ? '进行中' : '未开始'
}
function formatDate(s?: string | null) {
  if (!s) return ''
  return s.slice(0, 10)
}

async function load() {
  loading.value = true
  error.value = ''
  const id = Number(route.params.id)
  // 防御：route.params.id 在某些 HMR/直链异常下可能是 undefined/非数字，
  // 直接转成 NaN/undefined 拼进 URL 会打到 /learning/paths/NaN 触发后端 500。
  if (!Number.isInteger(id) || id <= 0) {
    error.value = '学习路径 ID 无效，请返回列表重新进入'
    loading.value = false
    return
  }
  try {
    const { data } = await getLearningPathDetail(id)
    detail.value = data
  } catch (e: any) {
    // 网络层错误（后端未启动 / 端口不通）没有 response，给出可操作的提示
    if (!e.response) {
      error.value = '无法连接后端服务，请确认服务已启动后重试'
    } else {
      error.value = e.response?.data?.message || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

async function setStatus(node: Detail['nodes'][number], status: NodeStatus) {
  try {
    const { data } = await updateNodeProgress(detail.value!.id, node.id, status)
    detail.value = data
    ElMessage.success(status === 'COMPLETED' ? '已标记完成' : '进度已更新')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '更新失败')
  }
}

async function confirmDelete() {
  try {
    await ElMessageBox.confirm(`确定删除学习路径「${detail.value?.title}」吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteLearningPath(detail.value!.id)
    ElMessage.success('已删除')
    router.push('/learn/paths')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

async function goLearn(pathId: number, nodeId: number) {
  await setStatus(detail.value!.nodes.find(n => n.id === nodeId)!, 'IN_PROGRESS')
  router.push(`/learn/paths/${pathId}/nodes/${nodeId}`)
}
</script>

<style scoped>
.path-head { display: flex; align-items: center; gap: var(--space-lg); margin-bottom: var(--space-lg); }
.back-btn {
  border: 1px solid var(--border); background: var(--surface); color: var(--text-secondary);
  border-radius: var(--radius-md); padding: 8px 12px; cursor: pointer; font: inherit;
  display: inline-flex; align-items: center; gap: 4px; flex-shrink: 0;
  transition: border-color var(--duration-fast), color var(--duration-fast);
}
.back-btn:hover { border-color: var(--primary-200); color: var(--primary-600); }
.path-head-main { flex: 1; min-width: 0; }

.progress-card { padding: var(--space-xl); margin-bottom: var(--space-xl); }
.progress-top { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--space-md); }
.progress-label { font-weight: 600; color: var(--text-primary); }
.progress-num { font-size: 0.8125rem; color: var(--text-secondary); }
.progress-bar { height: 10px; background: var(--surface-3); border-radius: var(--radius-full); overflow: hidden; }
.progress-fill {
  height: 100%; background: var(--brand-gradient); border-radius: var(--radius-full);
  transition: width 0.6s var(--ease-out);
}
.progress-tags { display: flex; align-items: center; gap: var(--space-md); margin-top: var(--space-sm); }
.progress-pct { margin-left: auto; font-weight: 700; color: var(--primary-600); font-variant-numeric: tabular-nums; }

.node-list { list-style: none; display: flex; flex-direction: column; gap: var(--space-md); }
.node-item {
  display: flex; align-items: center; gap: var(--space-lg); padding: var(--space-lg) var(--space-xl);
  border-left: 4px solid var(--border);
  transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
}
.node-item.active { border-left-color: var(--warning); }
.node-item.done { border-left-color: var(--success, #16a34a); }
.node-index {
  width: 36px; height: 36px; flex-shrink: 0; border-radius: 50%;
  background: var(--surface-3); color: var(--text-secondary);
  display: inline-flex; align-items: center; justify-content: center; font-weight: 700;
  cursor: grab; user-select: none;
}
.node-index.dragging { opacity: 0.5; }
.drag-handle:active { cursor: grabbing; }
.node-item.done .node-index { background: var(--success-light); color: var(--success); }
.node-item.active .node-index { background: var(--warning-light); color: var(--warning); }
.node-body { flex: 1; min-width: 0; }
.node-title-row { display: flex; align-items: center; gap: var(--space-sm); flex-wrap: wrap; }
.node-title { margin: 0; font-size: 0.95rem; font-weight: 700; color: var(--text-primary); }
.node-desc { margin: 4px 0 0; font-size: 0.8125rem; color: var(--text-secondary); }
.node-done-at { margin-top: 4px; font-size: 0.75rem; color: var(--text-muted); }
.node-actions { display: flex; flex-wrap: wrap; gap: 6px; flex-shrink: 0; }

.loading-spin { color: var(--text-muted); }

.error-box { display: flex; align-items: center; gap: var(--space-md); flex-wrap: wrap; }
.error-box .el-button { margin: 0; }

@media (max-width: 560px) {
  .node-item { flex-direction: column; align-items: stretch; }
  .node-actions { flex-direction: row; }
}
</style>

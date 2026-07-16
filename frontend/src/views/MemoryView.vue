<template>
  <div class="page-container">
    <header class="page-head">
      <div>
        <h1 class="page-title">记忆中心</h1>
        <p class="page-subtitle">AI 会跨会话记住你的偏好与关键事实，并在回答时参考。你可随时查看、编辑或删除。</p>
      </div>
      <el-button :icon="RefreshCw" :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="stat-row">
      <div class="stat-card">
        <span class="stat-num">{{ stats.total }}</span>
        <span class="stat-label">记忆总数</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">{{ stats.preference }}</span>
        <span class="stat-label">偏好 / 事实</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">{{ stats.summary }}</span>
        <span class="stat-label">对话摘要</span>
      </div>
    </div>

    <div v-if="loading" class="ui-loading">加载中…</div>

    <div v-else-if="list.length === 0" class="ui-empty">
      <BrainCircuit class="icon-xl" />
      <p>还没有任何记忆</p>
      <span class="empty-hint">在对话中表达你的偏好（例如「我关注手机品类」），AI 会自动记住。</span>
    </div>

    <div v-else class="mem-list">
      <div
        v-for="m in list"
        :key="m.id"
        class="mem-card"
      >
        <div class="mem-top">
          <span class="mem-type" :class="'t-' + m.memoryType">
            <component :is="typeIcon(m.memoryType)" class="icon-sm" />
            {{ typeLabel(m.memoryType) }}
          </span>
          <div class="mem-actions">
            <button class="mem-btn" type="button" :title="'编辑'" @click="startEdit(m)">
              <Pencil class="icon-sm" />
            </button>
            <button class="mem-btn danger" type="button" :title="'删除'" @click="remove(m)">
              <Trash2 class="icon-sm" />
            </button>
          </div>
        </div>

        <p v-if="editId !== m.id" class="mem-content">{{ m.content }}</p>
        <div v-else class="mem-edit">
          <el-input
            v-model="editContent"
            type="textarea"
            :rows="3"
            resize="none"
          />
          <div class="mem-edit-foot">
            <el-rate v-model="editImportance" :max="5" />
            <div class="mem-edit-btns">
              <el-button size="small" @click="cancelEdit">取消</el-button>
              <el-button size="small" type="primary" :loading="saving" @click="saveEdit(m)">保存</el-button>
            </div>
          </div>
        </div>

        <div class="mem-meta">
          <span>重要性 {{ m.importance }}/5</span>
          <span>·</span>
          <span>{{ fromNow(m.createdAt) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { BrainCircuit, Pencil, Trash2, RefreshCw, Star, ScrollText, type LucideIcon } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMemories, updateMemory, deleteMemory, type UserMemory } from '@/api/memories'

const list = ref<UserMemory[]>([])
const loading = ref(false)
const editId = ref<number | null>(null)
const editContent = ref('')
const editImportance = ref(3)
const saving = ref(false)

const stats = computed(() => ({
  total: list.value.length,
  preference: list.value.filter(m => m.memoryType === 'preference' || m.memoryType === 'fact').length,
  summary: list.value.filter(m => m.memoryType === 'summary').length
}))

const typeIconMap: Record<string, LucideIcon> = {
  preference: Star,
  fact: Star,
  summary: ScrollText
}
function typeIcon(t: string): LucideIcon {
  return typeIconMap[t] || Star
}
function typeLabel(t: string): string {
  if (t === 'summary') return '对话摘要'
  if (t === 'fact') return '事实'
  return '偏好'
}

async function load() {
  loading.value = true
  try {
    const r = await getMemories()
    list.value = (r.data?.data ?? r.data ?? []) as UserMemory[]
  } catch {
    ElMessage.error('加载记忆失败')
  } finally {
    loading.value = false
  }
}

function startEdit(m: UserMemory) {
  editId.value = m.id
  editContent.value = m.content
  editImportance.value = m.importance
}
function cancelEdit() {
  editId.value = null
}
async function saveEdit(m: UserMemory) {
  saving.value = true
  try {
    await updateMemory(m.id, { content: editContent.value.trim(), importance: editImportance.value })
    m.content = editContent.value.trim()
    m.importance = editImportance.value
    editId.value = null
    ElMessage.success('已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
async function remove(m: UserMemory) {
  try {
    await ElMessageBox.confirm('确定删除这条记忆吗？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteMemory(m.id)
    list.value = list.value.filter(x => x.id !== m.id)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

function fromNow(iso: string): string {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const m = Math.floor(diff / 60000)
  if (m < 1) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  return `${Math.floor(h / 24)} 天前`
}

onMounted(load)
</script>

<style scoped>
.page-container { max-width: 860px; margin: 0 auto; padding: var(--space-3xl) var(--space-lg); }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-2xl); }
.page-title { font-size: 24px; font-weight: 800; color: var(--text-primary); margin: 0; }
.page-subtitle { color: var(--text-secondary); margin: 6px 0 0; font-size: 14px; max-width: 560px; }

.stat-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-md); margin-bottom: var(--space-2xl); }
.stat-card {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: var(--space-lg); display: flex; flex-direction: column; gap: 4px;
}
.stat-num { font-size: 28px; font-weight: 800; color: var(--primary-600); }
.stat-label { font-size: 13px; color: var(--text-secondary); }

.mem-list { display: flex; flex-direction: column; gap: var(--space-md); }
.mem-card {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: var(--space-lg); transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
}
.mem-card:hover { border-color: var(--primary-400); box-shadow: var(--shadow-sm); }
.mem-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-sm); }
.mem-type {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12px; font-weight: 600; padding: 3px 10px; border-radius: 999px;
}
.mem-type.t-preference { background: color-mix(in srgb, var(--primary-500) 14%, transparent); color: var(--primary-600); }
.mem-type.t-fact { background: color-mix(in srgb, #8b5cf6 14%, transparent); color: #7c3aed; }
.mem-type.t-summary { background: color-mix(in srgb, #f59e0b 14%, transparent); color: #b45309; }

.mem-actions { display: flex; gap: 4px; }
.mem-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; border: none; background: transparent; color: var(--text-muted);
  border-radius: var(--radius-sm); cursor: pointer; transition: background var(--duration-fast), color var(--duration-fast);
}
.mem-btn:hover { background: var(--surface-3); color: var(--text-primary); }
.mem-btn.danger:hover { color: #ef4444; }

.mem-content { margin: 0; color: var(--text-primary); line-height: 1.65; font-size: 14px; white-space: pre-wrap; }
.mem-edit { display: flex; flex-direction: column; gap: var(--space-sm); }
.mem-edit-foot { display: flex; align-items: center; justify-content: space-between; }
.mem-edit-btns { display: flex; gap: var(--space-sm); }

.mem-meta { display: flex; gap: 8px; margin-top: var(--space-sm); font-size: 12px; color: var(--text-muted); }

.ui-empty { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: var(--space-3xl) 0; color: var(--text-muted); text-align: center; }
.ui-empty p { margin: 0; font-size: 15px; color: var(--text-secondary); }
.empty-hint { font-size: 13px; max-width: 360px; }
.ui-loading { text-align: center; padding: var(--space-2xl); color: var(--text-muted); }

@media (max-width: 480px) {
  .stat-row { grid-template-columns: 1fr; }
}
</style>

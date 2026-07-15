<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">学习路径</h1>
        <p class="page-subtitle">
          把知识库整理成有序的课程路线，按节点逐章推进并追踪完成进度。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Sparkles" type="primary" @click="openGenerate">从知识库生成</el-button>
        <el-button :icon="Plus" @click="openCreate">新建路径</el-button>
      </div>
    </div>

    <div v-if="loading" class="ui-loading" role="status" aria-live="polite">
      <el-icon class="is-loading loading-spin icon-md"><Loader2 /></el-icon>
      <span>正在加载学习路径…</span>
    </div>

    <div v-else-if="error" class="cfg-error">
      <el-alert type="error" :closable="false" show-icon :title="error" />
    </div>

    <div v-else-if="paths.length === 0" class="ui-empty">
      <el-icon class="empty-icon"><Route /></el-icon>
      <p>还没有学习路径。点击右上角「从知识库生成」一键把某知识库整理成有序路线，或「新建路径」手动规划。</p>
    </div>

    <div v-else class="ui-grid cols-3">
      <article
        v-for="p in paths"
        :key="p.id"
        class="ui-card path-card"
        role="button"
        tabindex="0"
        @click="goDetail(p.id)"
        @keydown.enter="goDetail(p.id)"
      >
        <div class="path-card-head">
          <span class="path-icon"><el-icon><Route /></el-icon></span>
          <button
            class="path-del"
            type="button"
            :aria-label="`删除路径 ${p.title}`"
            @click.stop="confirmDelete(p)"
          ><el-icon><Trash2 /></el-icon></button>
        </div>
        <h2 class="path-title">{{ p.title }}</h2>
        <p class="path-desc">{{ p.description || '自定义学习路线' }}</p>
        <div class="path-meta">
          <span v-if="p.knowledgeBaseId" class="path-kb">
            <el-icon><Library /></el-icon>{{ kbName(p.knowledgeBaseId) }}
          </span>
          <span class="path-date">更新于 {{ formatDate(p.updatedAt) }}</span>
        </div>
        <div class="path-foot">
          <span class="path-go">进入 <el-icon><ArrowRight /></el-icon></span>
        </div>
      </article>
    </div>

    <!-- 新建路径 -->
    <el-dialog v-model="createVisible" title="新建学习路径" width="480px">
      <el-form label-position="top">
        <el-form-item label="路径标题" required>
          <el-input v-model="createForm.title" placeholder="例如：Java 并发编程路线" maxlength="120" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-select v-model="createForm.knowledgeBaseId" placeholder="可选" clearable filterable style="width:100%">
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 从知识库生成 -->
    <el-dialog v-model="generateVisible" title="从知识库生成学习路线" width="480px">
      <el-form label-position="top">
        <el-form-item label="选择知识库" required>
          <el-select v-model="generateForm.knowledgeBaseId" placeholder="选择知识库" filterable style="width:100%">
            <el-option v-for="kb in kbOptions" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径标题">
          <el-input v-model="generateForm.title" placeholder="留空则自动命名为「学习路线 · 知识库名」" maxlength="120" />
        </el-form-item>
      </el-form>
      <p class="gen-tip">
        <el-icon><Sparkles /></el-icon>
        将把该知识库（含子库）中已成功解析的每篇文档生成为一个有序节点。
      </p>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Sparkles, Trash2, ArrowRight, Route, Library, Loader2
} from 'lucide-vue-next'
import {
  listLearningPaths, createLearningPath, generateLearningPath, deleteLearningPath,
  type LearningPathSummary
} from '@/api/learningPath'
import { listKnowledgeBases, type KnowledgeBase } from '@/api/knowledgeBase'

const router = useRouter()

const loading = ref(true)
const error = ref('')
const paths = ref<LearningPathSummary[]>([])
const kbOptions = ref<KnowledgeBase[]>([])

const createVisible = ref(false)
const generateVisible = ref(false)
const saving = ref(false)

const createForm = reactive<{ title: string; description: string; knowledgeBaseId: number | null }>({
  title: '', description: '', knowledgeBaseId: null
})
const generateForm = reactive<{ knowledgeBaseId: number | null; title: string }>({
  knowledgeBaseId: null, title: ''
})

function kbName(id?: number | null) {
  if (!id) return ''
  return kbOptions.value.find(k => k.id === id)?.name || '知识库'
}

function formatDate(s?: string) {
  if (!s) return '—'
  return s.slice(0, 10)
}

function goDetail(id: number) {
  router.push(`/learn/paths/${id}`)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [p, k] = await Promise.all([listLearningPaths(), listKnowledgeBases()])
    paths.value = p
    kbOptions.value = k
  } catch (e: any) {
    error.value = e.response?.data?.message || '加载学习路径失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createForm.title = ''
  createForm.description = ''
  createForm.knowledgeBaseId = null
  createVisible.value = true
}

function openGenerate() {
  generateForm.knowledgeBaseId = null
  generateForm.title = ''
  generateVisible.value = true
}

async function submitCreate() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请填写路径标题')
    return
  }
  saving.value = true
  try {
    await createLearningPath({
      title: createForm.title.trim(),
      description: createForm.description || undefined,
      knowledgeBaseId: createForm.knowledgeBaseId
    })
    ElMessage.success('已创建学习路径')
    createVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  } finally {
    saving.value = false
  }
}

async function submitGenerate() {
  if (!generateForm.knowledgeBaseId) {
    ElMessage.warning('请选择知识库')
    return
  }
  saving.value = true
  try {
    const { data } = await generateLearningPath({
      knowledgeBaseId: generateForm.knowledgeBaseId,
      title: generateForm.title.trim() || undefined
    })
    ElMessage.success('已生成学习路线')
    generateVisible.value = false
    router.push(`/learn/paths/${data.id}`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '生成失败')
  } finally {
    saving.value = false
  }
}

async function confirmDelete(p: LearningPathSummary) {
  try {
    await ElMessageBox.confirm(`确定删除学习路径「${p.title}」吗？节点与进度将一并清除。`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteLearningPath(p.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.header-actions { display: flex; gap: var(--space-md); flex-wrap: wrap; }
.path-card { padding: var(--space-xl); cursor: pointer; transition: box-shadow var(--duration-fast), transform var(--duration-fast), border-color var(--duration-fast); }
.path-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); border-color: var(--primary-200); }
.path-card:focus-visible { outline: 2px solid var(--primary-500); outline-offset: 2px; }
.path-card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-md); }
.path-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; border-radius: var(--radius-md);
  background: var(--primary-50); color: var(--primary-600); font-size: 22px;
}
.path-del {
  border: none; background: transparent; cursor: pointer; color: var(--text-muted);
  width: 32px; height: 32px; border-radius: var(--radius-sm); display: inline-flex; align-items: center; justify-content: center;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.path-del:hover { background: var(--danger-light, #fee2e2); color: var(--danger, #dc2626); }
.path-title { margin: 0 0 6px; font-size: 1rem; font-weight: 700; color: var(--text-primary); }
.path-desc {
  margin: 0 0 var(--space-md); font-size: 0.8125rem; color: var(--text-secondary);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 36px;
}
.path-meta { display: flex; flex-wrap: wrap; gap: var(--space-md); font-size: 0.75rem; color: var(--text-muted); margin-bottom: var(--space-md); }
.path-kb { display: inline-flex; align-items: center; gap: 4px; }
.path-foot { display: flex; justify-content: flex-end; }
.path-go { font-size: 0.8125rem; font-weight: 600; color: var(--primary-600); display: inline-flex; align-items: center; gap: 4px; }
.gen-tip { display: flex; align-items: center; gap: 6px; font-size: 0.8125rem; color: var(--text-secondary); margin: 0 0 var(--space-md); }
.loading-spin { color: var(--text-muted); }
.cfg-error { margin-bottom: var(--space-lg); }

@media (max-width: 640px) {
  .ui-grid.cols-3 { grid-template-columns: 1fr; }
}
</style>

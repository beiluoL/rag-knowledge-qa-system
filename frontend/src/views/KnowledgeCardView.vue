<template>
  <div class="cards-page">
    <!-- 页头 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">知识卡片</h1>
        <p class="page-sub">管理你的知识卡片：手动整理、AI 主题生成，或从知识库文档中抽取。</p>
      </div>
      <div class="head-actions">
        <el-tree-select
          v-model="selectedKbId"
          :data="kbTree"
          :props="{ label: 'name', children: 'children', value: 'id' }"
          placeholder="筛选知识库..."
          clearable
          check-strictly
          filterable
          @change="onKbChange"
          class="head-kb-select"
        />
        <el-button :icon="Sparkles" @click="openGenerate">AI 主题生成</el-button>
        <el-button :icon="Sparkles" type="warning" plain @click="openExtractFromKb" :disabled="!selectedKbId">从知识库抽取</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建卡片</el-button>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-button size="small" :type="batchMode ? 'warning' : 'default'" :icon="CheckSquare" @click="toggleBatchMode">
        {{ batchMode ? '取消' : '多选' }}
      </el-button>
      <div v-if="batchMode && selectedCardIds.length" class="batch-actions">
        <span class="batch-count">已选 {{ selectedCardIds.length }} 张</span>
        <el-button size="small" type="primary" :icon="Folder" @click="batchMoveVisible = true">移动到知识库</el-button>
        <el-button size="small" type="danger" :icon="Trash2" @click="handleBatchDelete">批量删除</el-button>
      </div>
      <el-segmented v-model="activeCategory" :options="categoryOptions" class="cat-seg" />
      <el-input
        v-model="keyword"
        placeholder="搜索标题 / 内容 / 标签"
        clearable
        :prefix-icon="Search"
        class="search-input"
        @input="onSearchInput"
        @clear="load"
      />
    </div>

    <!-- 列表 -->
    <div v-if="loading" class="state-hint">正在加载知识卡片…</div>

    <div v-else-if="cards.length === 0" class="empty-state">
      <el-icon class="empty-icon"><Layers /></el-icon>
      <p class="empty-title">还没有知识卡片</p>
      <p class="empty-desc">点击「新建卡片」手动添加，或用「AI 生成」根据主题批量创建。</p>
      <div class="empty-actions">
        <el-button :icon="Sparkles" @click="openGenerate">AI 生成</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建卡片</el-button>
      </div>
    </div>

    <div v-else class="card-grid">
      <article v-for="c in cards" :key="c.id" class="ui-card kc-card" :class="{ selected: selectedCardIds.includes(c.id) }" tabindex="0" @click="batchMode && toggleSelect(c.id)">
        <header class="kc-head">
          <el-checkbox v-if="batchMode" :model-value="selectedCardIds.includes(c.id)" @change="toggleSelect(c.id)" class="kc-check" @click.stop />
          <h3 class="kc-title" :title="c.title">{{ c.title }}</h3>
          <div class="kc-badges">
            <span v-if="c.source === 'AI'" class="src-badge src-ai">AI</span>
            <span v-else class="src-badge src-manual">手动</span>
            <span v-if="c.category" class="cat-badge">{{ c.category }}</span>
          </div>
        </header>

        <div v-if="c.front" class="kc-front">
          <span class="kc-label">正面</span>
          <p class="kc-text">{{ c.front }}</p>
        </div>
        <div class="kc-back">
          <span class="kc-label">背面</span>
          <p class="kc-text">{{ c.back }}</p>
        </div>

        <div v-if="c.tags" class="kc-tags">
          <span v-for="t in c.tags.split(',')" :key="t" class="tag-chip">{{ t.trim() }}</span>
        </div>

        <footer class="kc-foot">
          <span class="kc-time">{{ formatTime(c.updatedAt) }}</span>
          <div class="kc-ops">
            <el-button size="small" text :icon="Pencil" @click="openEdit(c)" aria-label="编辑" />
            <el-button size="small" text type="danger" :icon="Trash2" @click="remove(c)" aria-label="删除" />
          </div>
        </footer>
      </article>
    </div>

    <!-- 新建 / 编辑 弹窗 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑知识卡片' : '新建知识卡片'" width="520px">
      <el-form :model="form" label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="300" placeholder="卡片标题" />
        </el-form-item>
        <el-form-item label="正面（问题 / 术语，可选）">
          <el-input v-model="form.front" type="textarea" :rows="2" resize="none"
            placeholder="如：什么是 RAG？" />
        </el-form-item>
        <el-form-item label="背面（答案 / 解析）" required>
          <el-input v-model="form.back" type="textarea" :rows="4" resize="none"
            placeholder="详细解答…" />
        </el-form-item>
        <el-form-item label="分类（可选）">
          <el-input v-model="form.category" maxlength="100" placeholder="如：前端 / 数据库" />
        </el-form-item>
        <el-form-item label="标签（逗号分隔，可选）">
          <el-input v-model="form.tags" placeholder="如：React, 性能" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- AI 生成 弹窗 -->
    <el-dialog v-model="genVisible" title="AI 一键生成知识卡片" width="480px">
      <el-form :model="genForm" label-position="top">
        <el-form-item label="主题" required>
          <el-input v-model="genForm.topic" placeholder="如：Spring Boot 事务管理" @keyup.enter="generate" />
        </el-form-item>
        <el-form-item label="生成数量">
          <el-input-number v-model="genForm.count" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="分类（可选，统一打标）">
          <el-input v-model="genForm.category" maxlength="100" placeholder="如：后端" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :icon="Sparkles" :loading="generating" @click="generate">生成</el-button>
      </template>
    </el-dialog>

    <!-- 批量移动弹窗 -->
    <el-dialog v-model="batchMoveVisible" title="移动卡片到知识库" width="400px">
      <el-tree-select
        v-model="moveTargetKbId"
        :data="kbTree"
        :props="{ label: 'name', children: 'children', value: 'id' }"
        placeholder="选择目标知识库（留空=移出知识库）"
        clearable
        check-strictly
        filterable
      />
      <template #footer>
        <el-button @click="batchMoveVisible = false">取消</el-button>
        <el-button type="primary" @click="handleBatchMove">确认移动</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="extractVisible" title="从知识库文档抽取知识卡片" width="480px">
      <p class="dialog-desc">AI 将分析「{{ selectedKbName }}」下的文档内容，自动抽取关键知识点生成卡片。</p>
      <el-form :model="extractForm" label-position="top">
        <el-form-item label="卡片数量上限">
          <el-input-number v-model="extractForm.count" :min="3" :max="50" />
        </el-form-item>
        <el-form-item label="分类（可选）">
          <el-input v-model="extractForm.category" maxlength="100" placeholder="如：前端 / 后端" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="extractVisible = false">取消</el-button>
        <el-button type="primary" :icon="Sparkles" :loading="extracting" @click="doExtract">开始抽取</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Sparkles, Search, Pencil, Trash2, Layers, CheckSquare, Folder } from 'lucide-vue-next'
import {
  listCards, createCard, updateCard, deleteCard, generateCards, extractCardsFromKb,
  batchDeleteCards, batchMoveCards,
  type KnowledgeCard, type CardForm
} from '@/api/knowledgeCard'
import { getKbTree, type KbTreeNode } from '@/api/knowledgeBase'

const router = useRouter()
const loading = ref(false)
const cards = ref<KnowledgeCard[]>([])
const categories = ref<string[]>([])
const activeCategory = ref<string>('')
const keyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

// KB 关联
const kbTree = ref<KbTreeNode[]>([])
const selectedKbId = ref<number | null>(null)
const selectedKbName = computed(() => {
  if (!selectedKbId.value) return ''
  const findName = (nodes: KbTreeNode[]): string => {
    for (const n of nodes) {
      if (n.id === selectedKbId.value) return n.name
      if (n.children) { const r = findName(n.children); if (r) return r }
    }
    return ''
  }
  return findName(kbTree.value)
})

const categoryOptions = computed(() => [
  { label: '全部', value: '' },
  ...categories.value.map(c => ({ label: c, value: c }))
])

// ── 新建 / 编辑 ──
const formVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<CardForm & { tags: string }>({ title: '', front: '', back: '', category: '', tags: '' })

function resetForm() {
  form.title = ''
  form.front = ''
  form.back = ''
  form.category = ''
  form.tags = ''
}

function openCreate() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(c: KnowledgeCard) {
  editingId.value = c.id
  form.title = c.title
  form.front = c.front || ''
  form.back = c.back
  form.category = c.category || ''
  form.tags = c.tags || ''
  formVisible.value = true
}

async function save() {
  if (!form.title.trim()) return ElMessage.warning('请填写标题')
  if (!form.back.trim()) return ElMessage.warning('请填写背面内容')
  saving.value = true
  const payload: CardForm = {
    title: form.title.trim(),
    front: form.front?.trim() || undefined,
    back: form.back.trim(),
    category: form.category?.trim() || undefined,
    tags: form.tags?.trim() || undefined
  }
  try {
    if (editingId.value) await updateCard(editingId.value, payload)
    else await createCard(payload)
    ElMessage.success(editingId.value ? '已保存' : '已创建')
    formVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ── AI 从知识库抽取 ──
const extractVisible = ref(false)
const extracting = ref(false)
const extractForm = reactive({ count: 10, category: '' })

function openExtractFromKb() {
  if (!selectedKbId.value) { ElMessage.warning('请先在上方选择知识库'); return }
  extractForm.count = 10
  extractForm.category = ''
  extractVisible.value = true
}

async function doExtract() {
  if (!selectedKbId.value) return
  extracting.value = true
  try {
    const { data } = await extractCardsFromKb(selectedKbId.value, extractForm.count, extractForm.category || undefined)
    ElMessage.success(`AI 已从知识库抽取 ${data.length} 张卡片`)
    extractVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '抽取失败')
  } finally { extracting.value = false }
}
const genVisible = ref(false)
const generating = ref(false)
const genForm = reactive({ topic: '', count: 5, category: '' })

function openGenerate() {
  genForm.topic = ''
  genForm.count = 5
  genForm.category = ''
  genVisible.value = true
}

async function generate() {
  if (!genForm.topic.trim()) return ElMessage.warning('请填写生成主题')
  generating.value = true
  try {
    const { data } = await generateCards({
      topic: genForm.topic.trim(),
      count: genForm.count,
      category: genForm.category.trim() || undefined
    })
    ElMessage.success(`AI 已生成 ${data.length} 张卡片`)
    genVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

// ── 批量操作 ──
const batchMode = ref(false)
const selectedCardIds = ref<number[]>([])
const batchMoveVisible = ref(false)
let moveTargetKbId: number | null = null

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) selectedCardIds.value = []
}

function toggleSelect(id: number) {
  const i = selectedCardIds.value.indexOf(id)
  if (i >= 0) selectedCardIds.value.splice(i, 1)
  else selectedCardIds.value.push(id)
}

async function handleBatchDelete() {
  if (!selectedCardIds.value.length) return
  try { await ElMessageBox.confirm(`确定删除 ${selectedCardIds.value.length} 张卡片？`, '确认', { type: 'warning' }) } catch { return }
  try {
    await batchDeleteCards(selectedCardIds.value)
    ElMessage.success('已删除')
    selectedCardIds.value = []
    batchMode.value = false
    await load()
  } catch (e: any) { ElMessage.error(e.response?.data?.message || '删除失败') }
}

async function handleBatchMove() {
  if (!selectedCardIds.value.length) return
  try {
    await batchMoveCards(selectedCardIds.value, moveTargetKbId)
    ElMessage.success(`已移动 ${selectedCardIds.value.length} 张卡片`)
    selectedCardIds.value = []
    batchMode.value = false
    batchMoveVisible.value = false
    await load()
  } catch (e: any) { ElMessage.error(e.response?.data?.message || '移动失败') }
}
async function remove(c: KnowledgeCard) {
  try {
    await ElMessageBox.confirm(`确定删除卡片「${c.title}」吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch { return }
  try {
    await deleteCard(c.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

// ── 搜索 ──
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(load, 350)
}

function formatTime(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ── 加载 ──
async function load() {
  loading.value = true
  try {
    const params: { category?: string; keyword?: string; knowledgeBaseId?: number } = {}
    if (activeCategory.value) params.category = activeCategory.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (selectedKbId.value) params.knowledgeBaseId = selectedKbId.value
    const { data } = await listCards(params)
    cards.value = data
    if (!activeCategory.value && !keyword.value.trim() && !selectedKbId.value) {
      categories.value = [...new Set(data.map(c => c.category).filter(Boolean) as string[])]
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '加载失败')
  } finally { loading.value = false }
}

async function loadKbTree() {
  try { const { data } = await getKbTree(); kbTree.value = data } catch { /* ignore */ }
}

function onKbChange() { load() }

onMounted(() => { loadKbTree(); load() })
</script>

<style scoped>
.cards-page {
  min-height: 100vh;
  background: var(--bg);
  padding: var(--space-3xl) var(--space-2xl);
  max-width: 1200px;
  margin: 0 auto;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  flex-wrap: wrap;
  margin-bottom: var(--space-2xl);
}
.page-title { font-size: 1.5rem; font-weight: 800; color: var(--text-primary); margin: 0; }
.page-sub { color: var(--text-secondary); margin: 6px 0 0; font-size: 0.9rem; }
.head-actions { display: flex; gap: var(--space-sm); }

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-xl);
  flex-wrap: wrap;
}
.cat-seg { flex-wrap: wrap; }
.search-input { max-width: 320px; }

.state-hint { text-align: center; color: var(--text-muted); padding: var(--space-3xl); }

/* 空状态 */
.empty-state {
  text-align: center;
  padding: var(--space-4xl) var(--space-xl);
  background: var(--surface);
  border: 1px dashed var(--border);
  border-radius: var(--radius-lg);
}
.empty-icon { font-size: 3rem; color: var(--primary-400); }
.empty-title { font-size: 1.125rem; font-weight: 700; color: var(--text-primary); margin: var(--space-md) 0 var(--space-xs); }
.empty-desc { color: var(--text-secondary); margin: 0 0 var(--space-lg); }
.empty-actions { display: flex; gap: var(--space-sm); justify-content: center; }

/* 卡片网格 */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-lg);
}
.kc-card {
  cursor: default;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  transition: box-shadow var(--duration-fast), transform var(--duration-fast), border-color var(--duration-fast);
}
.kc-card.selected { border-color: var(--primary-500); background: var(--primary-50); }
.kc-check { flex-shrink: 0; margin-right: var(--space-sm); }
.batch-actions { display: flex; align-items: center; gap: var(--space-sm); }
.batch-count { font-size: var(--text-sm); color: var(--text-secondary); font-weight: 600; }
.kc-card:hover, .kc-card:focus-visible {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
  border-color: var(--primary-500);
  outline: none;
}
.kc-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-sm); }
.kc-title {
  font-size: 1rem; font-weight: 700; color: var(--text-primary);
  margin: 0; line-height: 1.4;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.kc-badges { display: flex; gap: 4px; flex-shrink: 0; flex-wrap: wrap; }
.src-badge, .cat-badge {
  font-size: 0.6875rem; font-weight: 600; padding: 2px 8px; border-radius: 999px; white-space: nowrap;
}
.src-ai { background: var(--primary-50); color: var(--primary-700); border: 1px solid var(--primary-200); }
.src-manual { background: var(--surface-3); color: var(--text-secondary); border: 1px solid var(--border); }
.cat-badge { background: var(--warning-50, var(--primary-50)); color: var(--text-secondary); border: 1px solid var(--border); }

.kc-front, .kc-back {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-md) var(--space-lg);
}
.kc-back { background: var(--primary-50); border-color: var(--primary-100); }
.kc-label {
  display: inline-block;
  font-size: 0.6875rem; font-weight: 700; letter-spacing: 0.04em;
  color: var(--text-muted); margin-bottom: 4px; text-transform: uppercase;
}
.kc-text {
  margin: 0; color: var(--text-primary); font-size: 0.875rem; line-height: 1.6;
  display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden;
}

.kc-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tag-chip {
  font-size: 0.75rem; color: var(--text-secondary);
  background: var(--surface-3); border: 1px solid var(--border);
  padding: 2px 8px; border-radius: 999px;
}

.kc-foot { display: flex; align-items: center; justify-content: space-between; margin-top: auto; }
.kc-time { font-size: 0.75rem; color: var(--text-muted); }
.kc-ops { display: flex; gap: 2px; }

@media (max-width: 480px) {
  .cards-page { padding: var(--space-lg) var(--space-md); }
  .toolbar { flex-direction: column; align-items: stretch; }
  .search-input { max-width: 100%; }
}
</style>

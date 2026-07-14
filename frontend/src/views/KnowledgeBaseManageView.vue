<template>
  <div class="kb-manage">
    <div class="kb-header">
      <div>
        <h2 class="page-title">知识库与标签</h2>
        <p class="page-sub">用「分类 + 树状知识库」组织内容：大库可包含子库，问答时选父库会检索整棵子树。</p>
      </div>
      <div class="kb-header-actions">
        <el-button :icon="Library" @click="catDialog = true">新建分类</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建知识库</el-button>
      </div>
    </div>

    <!-- 分类筛选 -->
    <div class="kb-filters">
      <span class="filter-label">分类：</span>
      <el-tag
        v-for="c in categories"
        :key="c.id"
        :class="['filter-chip', { active: activeCategory === c.id }]"
        @click="activeCategory = activeCategory === c.id ? null : c.id"
      >{{ c.name }}
        <el-icon v-if="!isPresetCategory(c.name)" class="chip-del" @click.stop="removeCategory(c)"><X /></el-icon>
      </el-tag>
      <el-tag :class="['filter-chip', { active: activeCategory === null }]" @click="activeCategory = null">全部</el-tag>
    </div>

    <!-- 知识库树 -->
    <div class="kb-tree-wrap" v-loading="loading">
      <el-tree
        v-if="displayTree.length"
        :data="displayTree"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        default-expand-all
        class="kb-tree"
      >
        <template #default="{ data }">
          <div class="kb-node">
            <component
              v-if="categoryIcon(data.categoryName)"
              :is="categoryIcon(data.categoryName)"
              class="kb-node-icon"
            />
            <component
              v-else
              :is="data.children && data.children.length ? FolderOpen : Folder"
              class="kb-node-icon"
            />
            <span class="kb-node-name">{{ data.name }}</span>
            <el-tag v-if="data.categoryName" size="small" type="info" effect="plain" class="kb-node-cat">{{ data.categoryName }}</el-tag>
            <el-tag v-if="data.isSystem" size="small" type="warning" effect="plain">系统</el-tag>
            <span class="kb-node-meta">文档 {{ kbStats[data.id] ?? 0 }}</span>
            <span class="kb-node-actions">
              <el-button size="small" type="primary" link @click.stop="openUpload(data)">上传</el-button>
              <el-button size="small" link @click.stop="openChat(data)">问答</el-button>
              <el-button v-if="!data.isSystem" size="small" link @click.stop="openEdit(data)">编辑</el-button>
              <el-button v-if="!data.isSystem" size="small" type="danger" link @click.stop="removeKb(data)">删除</el-button>
            </span>
          </div>
        </template>
      </el-tree>
      <el-empty v-else description="暂无知识库，点击右上角新建" />
    </div>

    <!-- 新建 / 编辑 知识库 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑知识库' : '新建知识库'" width="460px">
      <el-form :model="form" label-width="84px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：后端技术文档" />
        </el-form-item>
        <el-form-item label="父知识库">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'name', children: 'children' }"
            value-key="id"
            node-key="id"
            placeholder="不选择则为顶级库"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔，如：API, 规范" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建分类 -->
    <el-dialog v-model="catDialog" title="新建分类" width="420px">
      <el-form :model="catForm" label-width="84px">
        <el-form-item label="分类名称" required>
          <el-input v-model="catForm.name" placeholder="如：前端工程" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="catForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">创建</el-button>
      </template>
    </el-dialog>

    <!-- 上传文档 -->
    <el-dialog v-model="uploadVisible" :title="`上传文档到《${uploadTarget?.name}》`" width="460px">
      <el-upload
        drag
        multiple
        :auto-upload="false"
        :on-change="onFileChange"
        :file-list="fileList"
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">拖入文件或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 pdf / txt / md / docx / xlsx</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">开始上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { BookText, Code, Package, Scale, GraduationCap, HeartPulse, Folder, FolderOpen, Plus, Upload, Library, X } from 'lucide-vue-next'
import {
  getKbTree, getCategories, createCategory, deleteCategory,
  createKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase, getKbStats,
  type KbTreeNode, type KbCategory
} from '@/api/knowledgeBase'
import { uploadDocument } from '@/api/knowledge'
import type { UploadUserFile, UploadFile } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const kbTree = ref<KbTreeNode[]>([])
const categories = ref<KbCategory[]>([])
const activeCategory = ref<number | null>(null)
const kbStats = ref<Record<number, number>>({})

const PRESET_CATS = ['技术文档', '产品/电商手册', '法律法规', '教育培训', '医疗健康', '编程与开发']
function isPresetCategory(name: string) { return PRESET_CATS.includes(name) }

const displayTree = computed(() => {
  if (activeCategory.value == null) return kbTree.value
  return filterTree(kbTree.value, activeCategory.value)
})
function filterTree(nodes: KbTreeNode[], catId: number): KbTreeNode[] {
  const res: KbTreeNode[] = []
  for (const n of nodes) {
    const kids = n.children ? filterTree(n.children, catId) : []
    if (n.categoryId === catId || kids.length) res.push({ ...n, children: kids })
  }
  return res
}
function flatten(nodes: KbTreeNode[]): KbTreeNode[] {
  return nodes.flatMap(n => [n, ...(n.children ? flatten(n.children) : [])])
}
function findNode(nodes: KbTreeNode[], id: number): KbTreeNode | undefined {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children) { const f = findNode(n.children, id); if (f) return f }
  }
  return undefined
}

// 编辑时，父库选项需排除自身及其子树，避免成环
const parentOptions = computed(() => {
  if (!editing.value) return kbTree.value
  const exclude = new Set<number>([editing.value.id])
  const root = findNode(kbTree.value, editing.value.id)
  if (root) {
    const add = (n: KbTreeNode) => (n.children || []).forEach(c => { exclude.add(c.id); add(c) })
    add(root)
  }
  const filt = (nodes: KbTreeNode[]): KbTreeNode[] =>
    nodes.filter(n => !exclude.has(n.id)).map(n => ({ ...n, children: filt(n.children || []) }))
  return filt(kbTree.value)
})

const dialogVisible = ref(false)
const editing = ref<KbTreeNode | null>(null)
const form = ref({ name: '', parentId: null as number | null, categoryId: null as number | null, tags: '', description: '' })

const catDialog = ref(false)
const catForm = ref({ name: '', description: '' })

const uploadVisible = ref(false)
const uploadTarget = ref<KbTreeNode | null>(null)
const fileList = ref<UploadUserFile[]>([])
const uploading = ref(false)

async function loadAll() {
  loading.value = true
  try {
    const [treeRes, catRes] = await Promise.all([getKbTree(), getCategories()])
    kbTree.value = treeRes.data || []
    categories.value = catRes.data || []
    const all = flatten(kbTree.value)
    const stats = await Promise.all(all.map(k =>
      getKbStats(k.id).then(r => [k.id, r.data.documentCount] as const).catch(() => [k.id, 0] as const)))
    kbStats.value = Object.fromEntries(stats)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = { name: '', parentId: null, categoryId: null, tags: '', description: '' }
  dialogVisible.value = true
}
function categoryIcon(name?: string) {
  const map: Record<string, any> = {
    '编程与开发': Code,
    '技术文档': BookText,
    '产品/电商手册': Package,
    '法律法规': Scale,
    '教育培训': GraduationCap,
    '医疗健康': HeartPulse
  }
  return (name && map[name]) || null
}

function openEdit(kb: KbTreeNode) {
  editing.value = kb
  form.value = {
    name: kb.name, parentId: kb.parentId ?? null,
    categoryId: kb.categoryId ?? null, tags: kb.tags || '', description: kb.description || ''
  }
  dialogVisible.value = true
}
async function submit() {
  if (!form.value.name.trim()) { ElMessage.warning('请填写名称'); return }
  if (editing.value) {
    await updateKnowledgeBase(editing.value.id, { ...form.value })
    ElMessage.success('已更新')
  } else {
    await createKnowledgeBase({ ...form.value })
    ElMessage.success('已创建')
  }
  dialogVisible.value = false
  loadAll()
}
async function removeKb(kb: KbTreeNode) {
  try {
    await ElMessageBox.confirm(`确定删除知识库《${kb.name}》？文档将保留为未分类。`, '确认', { type: 'warning' })
    await deleteKnowledgeBase(kb.id)
    ElMessage.success('已删除')
    loadAll()
  } catch { /* 取消 */ }
}

async function submitCategory() {
  if (!catForm.value.name.trim()) { ElMessage.warning('请输入分类名称'); return }
  try {
    await createCategory({ name: catForm.value.name.trim(), description: catForm.value.description })
    ElMessage.success('分类已创建')
    catDialog.value = false
    catForm.value = { name: '', description: '' }
    loadAll()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '创建失败')
  }
}
async function removeCategory(c: KbCategory) {
  try {
    await ElMessageBox.confirm(`删除分类《${c.name}》？该分类下的知识库不会被删除，仅解除分类关联。`, '确认', { type: 'warning' })
    await deleteCategory(c.id)
    ElMessage.success('已删除')
    loadAll()
  } catch { /* 取消 */ }
}

function openUpload(kb: KbTreeNode) {
  uploadTarget.value = kb
  fileList.value = []
  uploadVisible.value = true
}
function onFileChange(_file: UploadFile, files: UploadFile[]) {
  fileList.value = files as UploadUserFile[]
}
async function submitUpload() {
  if (!uploadTarget.value || fileList.value.length === 0) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  try {
    for (const f of fileList.value) {
      if (f.raw) await uploadDocument(f.raw, undefined, undefined, uploadTarget.value.id)
    }
    ElMessage.success('上传成功，后台正在解析')
    uploadVisible.value = false
    loadAll()
  } catch (e: any) {
    ElMessage.error('上传失败：' + (e.response?.data?.message || e.message))
  } finally {
    uploading.value = false
  }
}

function openChat(kb: KbTreeNode) {
  router.push({ path: '/chat', query: { kb: String(kb.id) } })
}

onMounted(loadAll)
</script>

<style scoped>
.kb-manage { max-width: 1000px; margin: 0 auto; }

.kb-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: var(--space-2xl); gap: var(--space-md); flex-wrap: wrap; }
.kb-header-actions { display: flex; gap: var(--space-sm); }

.page-title { font-size: 21px; font-weight: 700; color: var(--text-primary); margin: 0 0 4px; }
.page-sub { color: var(--text-secondary); font-size: var(--text-sm); margin: 0; max-width: 620px; line-height: 1.5; }

.kb-filters { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); margin-bottom: var(--space-2xl); }
.filter-label { font-size: var(--text-sm); color: var(--text-secondary); }
.filter-chip { cursor: pointer; transition: all var(--duration-fast); display: inline-flex; align-items: center; gap: 2px; }
.filter-chip.active { background: var(--brand-1); color: #fff; border-color: transparent; }
.chip-del { font-size: var(--text-md); margin-left: 2px; opacity: 0.7; }
.chip-del:hover { opacity: 1; }

.kb-tree-wrap {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-md) var(--space-sm);
  box-shadow: var(--shadow-sm);
  min-height: 200px;
}
.kb-tree { --el-tree-node-hover-bg-color: var(--surface-3); font-size: 14px; }
.kb-node { display: flex; align-items: center; gap: var(--space-sm); width: 100%; padding: 4px 0; }
.kb-node-icon { width: 18px; height: 18px; color: var(--brand-1); flex-shrink: 0; }
.kb-node-name { font-weight: 500; color: var(--text-primary); }
.kb-node-cat { margin-left: 2px; }
.kb-node-meta { font-size: var(--text-xs); color: var(--text-muted); }
.kb-node-actions { margin-left: auto; display: flex; gap: 2px; opacity: 0; transition: opacity var(--duration-fast); }
.kb-node:hover .kb-node-actions { opacity: 1; }

/* 触摸设备 / 手机：常显操作按钮，保证可触控 */
@media (hover: none), (max-width: 480px) {
  .kb-node-actions { opacity: 1; }
}

@media (max-width: 480px) {
  /* 触摸目标 ≥44px */
  .el-button { min-height: 44px; }
}
</style>

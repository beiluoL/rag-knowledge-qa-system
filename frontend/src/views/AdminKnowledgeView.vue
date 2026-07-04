<template>
  <div class="chat-layout">
    <aside class="sidebar" :style="{ width: '280px' }">
      <div class="sidebar-header"><h3>📚 知识库管理</h3></div>
      <div class="sidebar-footer">
        <el-button @click="router.push('/chat')" style="width:100%">← 返回对话</el-button>
      </div>
    </aside>
    <main class="admin-main">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>文档列表</span>
            <div class="header-actions">
              <!-- 视图切换 -->
              <el-radio-group v-model="viewMode" size="small" class="view-toggle">
                <el-radio-button value="list">📋 列表</el-radio-button>
                <el-radio-button value="card">🎴 卡片</el-radio-button>
              </el-radio-group>
              <el-button type="primary" @click="openUploadDialog" :icon="Upload">上传文档</el-button>
              <el-button @click="loadDocuments" :icon="Refresh">刷新</el-button>
            </div>
          </div>
        </template>

        <!-- 统计信息 -->
        <el-row :gutter="16" style="margin-bottom:16px">
          <el-col :span="6"><el-statistic title="文档总数" :value="stats.documentCount" /></el-col>
          <el-col :span="6"><el-statistic title="分块总数" :value="stats.chunkCount" /></el-col>
          <el-col :span="6"><el-statistic title="向量总数" :value="stats.embeddingCount" /></el-col>
        </el-row>

        <!-- ====== 列表视图 ====== -->
        <el-table v-if="viewMode === 'list'" :data="documents" stripe v-loading="loadingDocs">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="文档标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="fileType" label="类型" width="70">
            <template #default="{ row }"><el-tag size="small">{{ row.fileType.toUpperCase() }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块数" width="80" />
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="160">
            <template #default="{ row }">{{ row.createdAt?.substring(0,16) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="viewDetail(row.id)">详情</el-button>
              <el-button size="small" text type="danger" @click="handleDelete(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- ====== 卡片视图 ====== -->
        <div v-else class="card-grid">
          <el-row :gutter="16">
            <el-col v-for="doc in documents" :key="doc.id" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom:16px">
              <div class="doc-card" @click="viewDetail(doc.id)">
                <!-- 类型图标 -->
                <div class="card-icon" :class="'icon-' + doc.fileType">
                  {{ fileIcon(doc.fileType) }}
                </div>
                <!-- 标题 -->
                <div class="card-title" :title="doc.title">{{ doc.title }}</div>
                <!-- 标签行 -->
                <div class="card-tags">
                  <el-tag :type="statusType(doc.status)" size="small">{{ statusText(doc.status) }}</el-tag>
                  <el-tag size="small" type="info">{{ doc.fileType.toUpperCase() }}</el-tag>
                </div>
                <!-- 信息行 -->
                <div class="card-info">
                  <span>📄 {{ doc.chunkCount }} 块</span>
                  <span>💾 {{ formatSize(doc.fileSize) }}</span>
                </div>
                <div class="card-time">{{ doc.createdAt?.substring(0,10) }}</div>
                <!-- 操作 -->
                <div class="card-actions" @click.stop>
                  <el-button size="small" text type="primary" @click="viewDetail(doc.id)">详情</el-button>
                  <el-button size="small" text type="danger" @click="handleDelete(doc.id)">删除</el-button>
                </div>
              </div>
            </el-col>
          </el-row>
          <el-empty v-if="documents.length === 0" description="暂无文档" />
        </div>
      </el-card>

      <!-- ====== 上传弹窗（Tab 模式） ====== -->
      <el-dialog v-model="uploadVisible" title="上传文档" width="560px" @opened="onDialogOpened" @closed="onDialogClosed">
        <el-tabs v-model="uploadTab">
          <!-- Tab 1: 本地文件 -->
          <el-tab-pane label="📁 本地文件" name="file">
            <div
              class="drop-zone"
              :class="{ 'drop-active': isDragging }"
              @dragover.prevent="isDragging = true"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="onDrop"
            >
              <el-icon :size="40" color="#909399"><UploadFilled /></el-icon>
              <p>拖拽文件到此处，或点击下方按钮选择</p>
              <el-button type="primary" @click="triggerFileInput">选择文件</el-button>
              <input ref="fileInputRef" type="file" multiple hidden
                accept=".pdf,.txt,.md,.docx,.xlsx,.csv,.doc,.xls"
                @change="onFileInputChange" />
            </div>
            <!-- 待上传文件列表 -->
            <div v-if="pendingFiles.length > 0" class="pending-files">
              <div class="pending-header">
                <span>待上传文件 ({{ pendingFiles.length }})</span>
                <el-button size="small" text type="danger" @click="pendingFiles = []">清空</el-button>
              </div>
              <div v-for="(f, idx) in pendingFiles" :key="idx" class="pending-item">
                <span class="pending-name">{{ f.name }}</span>
                <span class="pending-size">{{ formatSize(f.size) }}</span>
                <el-button size="small" text type="danger" @click="pendingFiles.splice(idx, 1)">✕</el-button>
              </div>
            </div>
            <div class="upload-tip">支持 PDF / Word / Excel / Markdown / TXT / CSV，单文件 ≤ 20MB</div>
          </el-tab-pane>

          <!-- Tab 2: URL 导入 -->
          <el-tab-pane label="🔗 URL 导入" name="url">
            <el-form label-width="80px">
              <el-form-item label="网页地址">
                <el-input v-model="importUrl" placeholder="https://example.com/article" />
              </el-form-item>
              <el-form-item label="导入方式">
                <el-radio-group v-model="importMode">
                  <el-radio value="text">纯文本（提取正文）</el-radio>
                  <el-radio value="pdf">网页文档（保留格式）</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- Tab 3: 粘贴上传 -->
          <el-tab-pane label="📋 粘贴上传" name="paste">
            <div class="paste-area" :class="{ 'paste-has-content': pasteText || pasteImage }">
              <template v-if="!pasteText && !pasteImage">
                <el-icon :size="36" color="#c0c4cc"><DocumentCopy /></el-icon>
                <p>在此区域按 Ctrl+V（Mac: ⌘V）粘贴</p>
                <p class="paste-hint">支持粘贴截图或复制的文字</p>
              </template>
              <template v-if="pasteText">
                <div class="paste-preview">
                  <span class="paste-label">📝 文字内容</span>
                  <div class="paste-text-preview">{{ pasteText.substring(0, 500) }}{{ pasteText.length > 500 ? '...' : '' }}</div>
                  <span class="paste-char-count">共 {{ pasteText.length }} 字</span>
                </div>
              </template>
              <template v-if="pasteImage">
                <div class="paste-preview">
                  <span class="paste-label">🖼️ 图片</span>
                  <img :src="pasteImage" class="paste-img-preview" />
                </div>
              </template>
              <el-button v-if="pasteText || pasteImage" size="small" @click="pasteText=''; pasteImage=''">清除</el-button>
            </div>
            <div class="upload-tip">截图后在此按 Ctrl+V 自动识别</div>
          </el-tab-pane>
        </el-tabs>

        <template #footer>
          <el-button @click="uploadVisible = false">取消</el-button>
          <el-button
            v-if="uploadTab === 'file' && pendingFiles.length > 0"
            type="primary" :loading="uploading"
            @click="handleBatchUpload">
            批量上传 ({{ pendingFiles.length }} 个)
          </el-button>
          <el-button
            v-if="uploadTab === 'url' && importUrl"
            type="primary" :loading="uploading"
            @click="handleUrlImport">
            开始导入
          </el-button>
          <el-button
            v-if="uploadTab === 'paste' && (pasteText || pasteImage)"
            type="primary" :loading="uploading"
            @click="handlePasteUpload">
            确认上传
          </el-button>
        </template>
      </el-dialog>

      <!-- 详情抽屉 -->
      <el-drawer v-model="detailVisible" title="文档详情" size="500px">
        <div v-if="detailDoc">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="标题">{{ detailDoc.title }}</el-descriptions-item>
            <el-descriptions-item label="文件类型">{{ detailDoc.fileType }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detailDoc.status }}</el-descriptions-item>
            <el-descriptions-item label="分块数">{{ detailDoc.chunkCount }}</el-descriptions-item>
          </el-descriptions>
          <el-divider>分块预览</el-divider>
          <div v-for="chunk in detailChunks" :key="chunk.id" class="chunk-preview">
            <div class="chunk-index">#{{ chunk.chunkIndex }}</div>
            <div class="chunk-content">{{ chunk.content.substring(0, 200) }}{{ chunk.content.length > 200 ? '...' : '' }}</div>
          </div>
        </div>
      </el-drawer>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Refresh, UploadFilled, DocumentCopy } from '@element-plus/icons-vue'
import { getDocuments, uploadDocuments, deleteDocument, getDocumentDetail, getStats, importFromUrl,
  type DocumentItem, type DocumentChunk } from '@/api/knowledge'

const router = useRouter()
const viewMode = ref<'list' | 'card'>('list')
const documents = ref<DocumentItem[]>([])
const loadingDocs = ref(false)

// 上传状态
const uploadVisible = ref(false)
const uploadTab = ref('file')
const uploading = ref(false)
const isDragging = ref(false)
const pendingFiles = ref<File[]>([])
const fileInputRef = ref<HTMLInputElement>()
const importUrl = ref('')
const importMode = ref('text')
const pasteText = ref('')
const pasteImage = ref('')

// 详情状态
const detailVisible = ref(false)
const detailDoc = ref<DocumentItem | null>(null)
const detailChunks = ref<DocumentChunk[]>([])

const stats = reactive({ documentCount: 0, chunkCount: 0, embeddingCount: 0 })

// ── 工具函数 ──
function statusType(status: string) {
  const map: Record<string, string> = { PENDING: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return map[status] || 'info'
}
function statusText(status: string) {
  const map: Record<string, string> = { PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }
  return map[status] || status
}
function formatSize(bytes: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024*1024) return (bytes/1024).toFixed(1)+'KB'
  return (bytes/(1024*1024)).toFixed(1)+'MB'
}
function fileIcon(type: string) {
  const icons: Record<string, string> = { pdf: '📕', docx: '📘', doc: '📘', xlsx: '📗', xls: '📗', csv: '📊', md: '📝', txt: '📄', html: '🌐' }
  return icons[type] || '📎'
}

// ── 文档列表 ──
async function loadDocuments() {
  loadingDocs.value = true
  try {
    const [docsRes, statsRes] = await Promise.all([getDocuments(), getStats()])
    documents.value = docsRes.data.content
    Object.assign(stats, statsRes.data)
  } finally { loadingDocs.value = false }
}

// ── 上传弹窗 ──
function openUploadDialog() {
  uploadTab.value = 'file'
  pendingFiles.value = []
  importUrl.value = ''
  pasteText.value = ''
  pasteImage.value = ''
  uploadVisible.value = true
}

function triggerFileInput() { fileInputRef.value?.click() }
function onFileInputChange(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files) addFiles(Array.from(target.files))
  target.value = ''
}
function onDrop(e: DragEvent) {
  isDragging.value = false
  if (e.dataTransfer?.files) addFiles(Array.from(e.dataTransfer.files))
}
function addFiles(files: File[]) {
  const allowed = ['pdf','txt','md','docx','xlsx','csv','doc','xls','html']
  for (const f of files) {
    const ext = f.name.split('.').pop()?.toLowerCase() || ''
    if (allowed.includes(ext) && f.size <= 20 * 1024 * 1024) {
      pendingFiles.value.push(f)
    }
  }
}

// 粘贴监听
function onPaste(e: ClipboardEvent) {
  if (!uploadVisible.value || uploadTab.value !== 'paste') return
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      const blob = item.getAsFile()
      if (blob) {
        const reader = new FileReader()
        reader.onload = () => { pasteImage.value = reader.result as string }
        reader.readAsDataURL(blob)
      }
    } else if (item.type === 'text/plain') {
      item.getAsString(text => {
        if (text.trim()) pasteText.value = text.trim()
      })
    }
  }
}

function onDialogOpened() { document.addEventListener('paste', onPaste) }
function onDialogClosed() { document.removeEventListener('paste', onPaste) }
onBeforeUnmount(() => document.removeEventListener('paste', onPaste))

// ── 上传操作 ──
async function handleBatchUpload() {
  if (pendingFiles.value.length === 0) { ElMessage.warning('请添加文件'); return }
  uploading.value = true
  try {
    const { data } = await uploadDocuments(pendingFiles.value)
    ElMessage.success(`上传完成: ${data.success} 成功${data.failed > 0 ? `, ${data.failed} 失败` : ''}`)
    uploadVisible.value = false
    pendingFiles.value = []
    await loadDocuments()
    setTimeout(loadDocuments, 3000)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  } finally { uploading.value = false }
}

async function handleUrlImport() {
  if (!importUrl.value) { ElMessage.warning('请输入URL'); return }
  uploading.value = true
  try {
    const { data } = await importFromUrl(importUrl.value, importMode.value)
    ElMessage.success(data.message || '导入成功')
    uploadVisible.value = false
    await loadDocuments()
    setTimeout(loadDocuments, 3000)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '导入失败')
  } finally { uploading.value = false }
}

async function handlePasteUpload() {
  uploading.value = true
  try {
    if (pasteImage.value) {
      // Base64 图片 → Blob → File
      const resp = await fetch(pasteImage.value)
      const blob = await resp.blob()
      const file = new File([blob], `paste-${Date.now()}.png`, { type: 'image/png' })
      await uploadDocuments([file])
    } else if (pasteText.value) {
      const blob = new Blob([pasteText.value], { type: 'text/plain' })
      const file = new File([blob], `paste-${Date.now()}.txt`, { type: 'text/plain' })
      await uploadDocuments([file])
    }
    ElMessage.success('上传成功')
    uploadVisible.value = false
    pasteText.value = ''
    pasteImage.value = ''
    await loadDocuments()
    setTimeout(loadDocuments, 3000)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  } finally { uploading.value = false }
}

// ── 文档操作 ──
async function viewDetail(id: number) {
  try {
    const { data } = await getDocumentDetail(id)
    detailDoc.value = data.document
    detailChunks.value = data.chunks
    detailVisible.value = true
  } catch { ElMessage.error('获取详情失败') }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('删除后将同时删除所有分块和向量数据，确定删除？', '确认', { type: 'warning' })
    await deleteDocument(id)
    ElMessage.success('已删除')
    await loadDocuments()
  } catch { /* 取消 */ }
}

onMounted(loadDocuments)
</script>

<style scoped>
.chat-layout { height: 100vh; display: flex; }
.sidebar { background: #fff; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column; }
.sidebar-header { padding: 16px; border-bottom: 1px solid #ebeef5; }
.sidebar-footer { padding: 16px; border-top: 1px solid #ebeef5; margin-top: auto; }
.admin-main { flex: 1; padding: 24px; overflow-y: auto; background: #f5f7fa; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.view-toggle { margin-right: 4px; }

/* ── 拖拽上传区 ── */
.drop-zone {
  border: 2px dashed #dcdfe6; border-radius: 10px;
  padding: 32px; text-align: center;
  transition: all 0.2s; cursor: pointer; color: #909399;
}
.drop-zone:hover, .drop-zone.drop-active {
  border-color: #409eff; background: #ecf5ff; color: #409eff;
}
.drop-zone p { margin: 8px 0; font-size: 13px; }

/* ── 待上传文件列表 ── */
.pending-files { margin-top: 12px; }
.pending-header { display: flex; justify-content: space-between; align-items: center; font-size: 13px; font-weight: 500; margin-bottom: 8px; }
.pending-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px;
  background: #f5f7fa; border-radius: 6px; margin-bottom: 4px; font-size: 13px;
}
.pending-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pending-size { color: #909399; font-size: 12px; white-space: nowrap; }

/* ── URL 导入 ── */
.upload-tip { margin-top: 12px; font-size: 12px; color: #c0c4cc; }

/* ── 粘贴区 ── */
.paste-area {
  border: 2px dashed #dcdfe6; border-radius: 10px;
  padding: 40px 20px; text-align: center; color: #c0c4cc;
  transition: all 0.2s; min-height: 150px;
}
.paste-area.paste-has-content { padding: 16px; text-align: left; }
.paste-area p { margin: 8px 0 0; font-size: 13px; }
.paste-hint { font-size: 12px !important; color: #dcdfe6; }
.paste-preview { margin-bottom: 12px; }
.paste-label { font-weight: 600; color: #303133; display: block; margin-bottom: 6px; }
.paste-text-preview {
  background: #f5f7fa; border-radius: 6px; padding: 10px;
  font-size: 13px; color: #606266; max-height: 200px; overflow-y: auto;
  white-space: pre-wrap; word-break: break-all;
}
.paste-img-preview { max-width: 100%; max-height: 300px; border-radius: 6px; border: 1px solid #ebeef5; }
.paste-char-count { font-size: 12px; color: #909399; display: block; margin-top: 4px; }

/* ── 卡片视图 ── */
.card-grid { margin-top: 4px; }
.doc-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 10px;
  padding: 20px 16px 14px; cursor: pointer; position: relative;
  transition: all 0.2s; height: 100%;
}
.doc-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.08); border-color: #409eff;
  transform: translateY(-2px);
}
.card-icon {
  width: 44px; height: 44px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px; background: #f5f7fa; margin-bottom: 10px;
}
.icon-pdf { background: #fef0f0; }
.icon-docx, .icon-doc { background: #ecf5ff; }
.icon-xlsx, .icon-xls, .icon-csv { background: #f0f9eb; }
.icon-md, .icon-txt { background: #f5f7fa; }
.card-title {
  font-size: 14px; font-weight: 600; color: #303133;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  margin-bottom: 8px;
}
.card-tags { display: flex; gap: 6px; margin-bottom: 8px; }
.card-info { display: flex; gap: 12px; font-size: 12px; color: #909399; margin-bottom: 4px; }
.card-time { font-size: 11px; color: #c0c4cc; margin-bottom: 8px; }
.card-actions { display: flex; gap: 4px; justify-content: flex-end; border-top: 1px solid #f0f0f0; padding-top: 8px; }

/* ── 分块：保持原有 ── */
.chunk-preview { margin-bottom: 12px; padding: 8px; background: #f5f7fa; border-radius: 6px; }
.chunk-index { font-weight: 600; color: #409eff; margin-bottom: 4px; }
.chunk-content { font-size: 13px; color: #606266; line-height: 1.6; }
</style>

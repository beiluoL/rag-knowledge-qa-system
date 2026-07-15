<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">文档管理</h1>
        <p class="page-subtitle">选择知识库后管理其文档，支持分块、向量化与在线预览。</p>
      </div>
      <div class="header-right">
        <el-tree-select
          v-model="selectedKbId"
          :data="kbTree"
          :props="{ label: 'name', children: 'children', value: 'id' }"
          placeholder="选择知识库..."
          clearable
          check-strictly
          filterable
          @change="onKbChange"
          class="kb-select"
        />
        <el-button :icon="MessageCircle" @click="router.push('/chat')">返回对话</el-button>
      </div>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">文档列表</span>
          <div class="header-actions">
            <el-radio-group v-model="viewMode" size="small" class="view-toggle">
              <el-radio-button value="list">
                <el-icon><List /></el-icon> 列表
              </el-radio-button>
              <el-radio-button value="card">
                <el-icon><LayoutGrid /></el-icon> 卡片
              </el-radio-button>
            </el-radio-group>
            <div class="header-actions-right">
              <el-button v-if="selectedIds.length > 0" type="danger" @click="handleBatchDelete">
                删除 ({{ selectedIds.length }})
              </el-button>
              <el-button type="primary" @click="openUploadDialog" :icon="Upload">上传</el-button>
              <el-dropdown trigger="click">
                <el-button :icon="MoreHorizontal" aria-label="更多操作" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :icon="Download" @click="exportData">导出</el-dropdown-item>
                    <el-dropdown-item :icon="RefreshCw" @click="loadDocuments">刷新</el-dropdown-item>
                    <el-dropdown-item :icon="Trash2" divided @click="handleClearAll">清空知识库</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>
      </template>

      <!-- 统计概览 -->
      <div class="stat-grid">
        <div class="ui-stat-card">
          <div class="ui-stat-icon"><el-icon><FileText /></el-icon></div>
          <div class="ui-stat-meta">
            <div class="ui-stat-value">{{ stats.documentCount }}</div>
            <div class="ui-stat-label">文档总数</div>
          </div>
        </div>
        <div class="ui-stat-card">
          <div class="ui-stat-icon" style="background: var(--success-light); color: var(--success)">
            <el-icon><Layers /></el-icon>
          </div>
          <div class="ui-stat-meta">
            <div class="ui-stat-value">{{ stats.chunkCount }}</div>
            <div class="ui-stat-label">分块总数</div>
          </div>
        </div>
        <div class="ui-stat-card">
          <div class="ui-stat-icon" style="background: var(--primary-50); color: var(--primary-600)">
            <el-icon><Database /></el-icon>
          </div>
          <div class="ui-stat-meta">
            <div class="ui-stat-value">{{ stats.embeddingCount }}</div>
            <div class="ui-stat-label">向量总数</div>
          </div>
        </div>
      </div>

      <!-- 搜索 + 状态筛选 -->
      <div class="tool-row">
        <el-input v-model="searchKeyword" placeholder="搜索文档标题或标签..." clearable
          :prefix-icon="Search" @input="onSearch" @keyup.enter="loadDocuments" @clear="loadDocuments" class="search-input" />
        <el-segmented v-model="statusFilter" :options="statusOptions" @change="loadDocuments" class="status-segment" />
      </div>

      <!-- ====== 列表 / 卡片视图 ====== -->
      <div class="doc-content" v-loading="loadingDocs">
        <!-- 列表视图 -->
        <el-table v-if="viewMode === 'list'" :data="documents" stripe class="doc-table">
          <el-table-column type="selection" width="40" />
          <el-table-column label="" width="48" align="center">
            <template #default="{ row }">
              <span class="file-ico" :class="fileColorClass(row.fileType)" :title="row.fileType.toUpperCase()">
                <el-icon><component :is="fileIconComp(row.fileType)" /></el-icon>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="标签" width="140">
            <template #default="{ row }">
              <el-tag v-for="t in parseTags(row.tags)" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="fileType" label="类型" width="70">
            <template #default="{ row }"><el-tag size="small">{{ row.fileType.toUpperCase() }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块" width="60" />
          <el-table-column label="大小" width="80">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <button class="icon-btn" type="button" title="详情" @click="viewDetail(row.id)"><el-icon><Eye /></el-icon></button>
                <button class="icon-btn" type="button" title="编辑" @click="openEditDialog(row)"><el-icon><Pencil /></el-icon></button>
                <button class="icon-btn" type="button" title="预览" @click="openPreview(row)"><el-icon><FileSearch /></el-icon></button>
                <button class="icon-btn danger" type="button" title="删除" @click="handleDelete(row.id)"><el-icon><Trash2 /></el-icon></button>
              </div>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无文档" />
          </template>
        </el-table>

        <!-- 卡片视图 -->
        <div v-else class="card-grid">
          <el-row :gutter="16">
            <el-col v-for="doc in documents" :key="doc.id" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom:16px">
              <div class="doc-card">
                <div class="card-check"><el-checkbox :model-value="selectedIds.includes(doc.id)" @change="(v: boolean) => toggleSelect(doc.id, v)" /></div>
                <div class="card-icon" :class="fileColorClass(doc.fileType)" @click="viewDetail(doc.id)">
                  <el-icon><component :is="fileIconComp(doc.fileType)" /></el-icon>
                </div>
                <div class="card-title" :title="doc.title" @click="viewDetail(doc.id)">{{ doc.title }}</div>
                <div class="card-tags">
                  <el-tag :type="statusType(doc.status)" size="small">{{ statusText(doc.status) }}</el-tag>
                  <el-tag size="small" type="info">{{ doc.fileType.toUpperCase() }}</el-tag>
                  <el-tag v-for="t in parseTags(doc.tags)" :key="t" size="small" type="warning">{{ t }}</el-tag>
                </div>
                <div class="card-info">
                  <span><el-icon><FileText /></el-icon> {{ doc.chunkCount }} 块</span>
                  <span><el-icon><Coins /></el-icon> {{ formatSize(doc.fileSize) }}</span>
                </div>
                <div class="card-time">{{ doc.createdAt?.substring(0,10) }}</div>
                <div class="card-actions">
                  <button class="icon-btn" type="button" title="详情" @click="viewDetail(doc.id)"><el-icon><Eye /></el-icon></button>
                  <button class="icon-btn" type="button" title="编辑" @click="openEditDialog(doc)"><el-icon><Pencil /></el-icon></button>
                  <button class="icon-btn" type="button" title="预览" @click="openPreview(doc)"><el-icon><FileSearch /></el-icon></button>
                  <button class="icon-btn danger" type="button" title="删除" @click="handleDelete(doc.id)"><el-icon><Trash2 /></el-icon></button>
                </div>
              </div>
            </el-col>
          </el-row>
          <el-empty v-if="documents.length === 0" description="暂无文档" />
        </div>
      </div>
    </el-card>

    <!-- ═════════ 上传弹窗 ═════════ -->
    <el-dialog v-model="uploadVisible" title="上传文档" width="580px" @opened="onDialogOpened" @closed="onDialogClosed">
      <el-tabs v-model="uploadTab">
        <el-tab-pane name="file">
          <template #label><el-icon><Folder /></el-icon> 本地文件</template>
          <div class="drop-zone" :class="{ 'drop-active': isDragging }"
            @dragover.prevent="isDragging=true" @dragleave.prevent="isDragging=false" @drop.prevent="onDrop">
            <el-icon class="drop-icon icon-2xl"><Upload /></el-icon>
            <p>拖拽文件到此处，或点击按钮选择</p>
            <el-button type="primary" @click="triggerFileInput">选择文件</el-button>
            <input ref="fileInputRef" type="file" multiple hidden
              accept=".pdf,.txt,.md,.docx,.xlsx,.csv,.doc,.xls" @change="onFileInputChange" />
          </div>
          <div v-if="pendingFiles.length > 0" class="pending-files">
            <div class="pending-header">
              <span>待上传 ({{ pendingFiles.length }})</span>
              <el-button size="small" text type="danger" @click="pendingFiles=[]">清空</el-button>
            </div>
            <div v-for="(f,idx) in pendingFiles" :key="idx" class="pending-item">
              <span class="pending-name">{{ f.name }}</span>
              <span class="pending-size">{{ formatSize(f.size) }}</span>
              <el-button size="small" text type="danger" @click="pendingFiles.splice(idx,1)">✕</el-button>
            </div>
          </div>
          <!-- 切分参数 -->
          <el-divider />
          <el-form label-width="110px" size="small">
            <el-form-item label="分块大小(字符)">
              <el-input-number v-model="customChunkSize" :min="100" :max="2000" :step="100" />
            </el-form-item>
            <el-form-item label="重叠(字符)">
              <el-input-number v-model="customOverlap" :min="0" :max="500" :step="50" />
            </el-form-item>
          </el-form>
          <div class="upload-tip">默认 500字/块 + 100字重叠。支持 PDF/Word/Excel/MD/TXT/CSV ≤20MB</div>
        </el-tab-pane>
        <el-tab-pane name="url">
          <template #label><el-icon><Link /></el-icon> URL 导入</template>
          <el-form label-width="80px">
            <el-form-item label="网页地址"><el-input v-model="importUrl" placeholder="https://..." /></el-form-item>
            <el-form-item label="方式">
              <el-radio-group v-model="importMode">
                <el-radio value="text">纯文本</el-radio>
                <el-radio value="pdf">网页文档</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane name="paste">
          <template #label><el-icon><Copy /></el-icon> 粘贴上传</template>
          <div class="paste-area" :class="{ 'paste-has-content': pasteText || pasteImage }">
            <template v-if="!pasteText && !pasteImage">
              <el-icon class="paste-icon icon-xl"><Copy /></el-icon>
              <p>Ctrl+V 粘贴截图或文字</p>
            </template>
            <div v-if="pasteText" class="paste-preview">
              <span class="paste-label"><el-icon><FileText /></el-icon> 文字 ({{ pasteText.length }}字)</span>
              <div class="paste-text-preview">{{ pasteText.substring(0,300) }}{{ pasteText.length>300?'...':'' }}</div>
            </div>
            <div v-if="pasteImage" class="paste-preview">
              <span class="paste-label"><el-icon><Image /></el-icon> 图片</span>
              <img :src="pasteImage" class="paste-img-preview" />
            </div>
            <el-button v-if="pasteText||pasteImage" size="small" @click="pasteText='';pasteImage=''" style="margin-top:8px">清除</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <div class="upload-footer">
          <el-tree-select
            v-model="uploadKbId"
            :data="kbTree"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            placeholder="目标知识库（必选）"
            check-strictly
            filterable
            class="upload-kb-select"
          />
          <div class="upload-footer-btns">
            <el-button @click="uploadVisible=false">取消</el-button>
            <el-button v-if="uploadTab==='file'&&pendingFiles.length" type="primary" :loading="uploading" :disabled="!uploadKbId" @click="handleBatchUpload">上传 ({{ pendingFiles.length }})</el-button>
            <el-button v-if="uploadTab==='url'&&importUrl" type="primary" :loading="uploading" :disabled="!uploadKbId" @click="handleUrlImport">导入</el-button>
            <el-button v-if="uploadTab==='paste'&&(pasteText||pasteImage)" type="primary" :loading="uploading" :disabled="!uploadKbId" @click="handlePasteUpload">确认上传</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- ═════════ 编辑弹窗 ═════════ -->
    <el-dialog v-model="editVisible" title="编辑文档" width="480px">
      <el-form label-width="70px">
        <el-form-item label="标题"><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="标签">
          <el-input v-model="editForm.tags" placeholder="逗号分隔，如：手机,苹果,2024款" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="文档描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ═════════ 详情抽屉（分块可视化） ═════════ -->
    <el-drawer v-model="detailVisible" title="文档详情" size="600px">
      <div v-if="detailDoc">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="标题">{{ detailDoc.title }}</el-descriptions-item>
          <el-descriptions-item label="标签">
            <el-tag v-for="t in parseTags(detailDoc.tags)" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag>
            <span v-if="!detailDoc.tags" class="muted-text">无标签</span>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ detailDoc.fileType }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailDoc.status }}</el-descriptions-item>
          <el-descriptions-item label="分块数">{{ detailDoc.chunkCount }}</el-descriptions-item>
        </el-descriptions>
        <el-divider>分块列表（<span class="vec-legend"><el-icon class="vec-ok"><CheckCircle2 /></el-icon>有向量</span> / <span class="vec-legend muted"><span class="vec-empty"></span>无向量</span>）</el-divider>
        <div v-for="chunk in detailChunks" :key="chunk.id" class="chunk-card" :class="{ 'chunk-has-vec': chunk.hasEmbedding }">
          <div class="chunk-header">
            <span class="chunk-num">#{{ chunk.chunkIndex }}</span>
            <span class="chunk-status">
              <el-icon v-if="chunk.hasEmbedding" class="vec-ok"><CheckCircle2 /></el-icon>
              <span v-else class="vec-empty"></span>
            </span>
            <span class="chunk-tokens">{{ chunk.tokenCount }} tokens</span>
            <el-button size="small" text type="danger" @click="handleDeleteChunk(chunk.id)">删除</el-button>
          </div>
          <div class="chunk-body">
            <template v-if="editingChunkId === chunk.id">
              <el-input v-model="editingChunkContent" type="textarea" :rows="3" />
              <div style="margin-top:6px;text-align:right">
                <el-button size="small" @click="editingChunkId=null">取消</el-button>
                <el-button size="small" type="primary" @click="handleSaveChunk(chunk.id)">保存</el-button>
              </div>
            </template>
            <template v-else>
              <div class="chunk-text">{{ chunk.content }}</div>
              <el-button size="small" text type="primary" @click="startEditChunk(chunk)">编辑</el-button>
            </template>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- ═════════ 预览抽屉 ═════════ -->
    <el-drawer v-model="previewVisible" title="文档预览" size="70%" @closed="previewContent=''; previewType=''">
      <div v-if="previewLoading" class="preview-loading">
        <el-icon class="is-loading preview-spin icon-2xl"><Loader2 /></el-icon>
        <p>正在加载预览...</p>
      </div>
      <!-- PDF -->
      <iframe v-if="previewType==='pdf'" :src="previewUrl" class="preview-frame" />
      <!-- 图片 -->
      <div v-else-if="previewType==='image'" class="preview-image-wrap">
        <img :src="previewUrl" class="preview-image" />
      </div>
      <!-- Word → HTML -->
      <div v-else-if="previewType==='docx'" v-html="previewContent" class="preview-html" />
      <!-- Excel → 表格 -->
      <div v-else-if="previewType==='xlsx'" class="preview-excel">
        <div v-for="(sheet, sName) in previewExcelData" :key="sName">
          <h4 class="sheet-title">{{ sName }}</h4>
          <div class="excel-scroll">
            <table class="excel-table">
              <tbody>
                <tr v-for="(row, ri) in (sheet as any[][])" :key="ri">
                  <td v-for="(cell, ci) in row" :key="ci">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <!-- TXT/MD/其他 -->
      <div v-else-if="previewContent" class="preview-text" v-html="renderPreviewMarkdown(previewContent)" />
      <div v-else-if="!previewLoading" class="preview-empty">
        暂不支持预览此格式
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Upload, RefreshCw, Copy, Search, Download,
  List, LayoutGrid, Folder, Link, Image, Coins, CheckCircle2, MessageCircle,
  FileText, StickyNote, Code, Table, Loader2,
  MoreHorizontal, Trash2, Eye, Pencil, FileSearch, Layers, Database
} from 'lucide-vue-next'
import { getDocuments, uploadDocuments, deleteDocument, getDocumentDetail, getStats,
  importFromUrl, updateDocument, updateChunk, deleteChunk,
  batchDeleteDocuments, clearAllDocuments, exportDocuments, getDocumentContentUrl,
  type DocumentItem, type DocumentChunk } from '@/api/knowledge'
import { getKbTree, type KbTreeNode } from '@/api/knowledgeBase'
import mammoth from 'mammoth'
import * as XLSX from 'xlsx'

const router = useRouter()
const route = useRoute()
const viewMode = ref<'list'|'card'>('list')
const documents = ref<DocumentItem[]>([])
const loadingDocs = ref(false)
const searchKeyword = ref('')
const statusFilter = ref('')
const selectedIds = ref<number[]>([])
// KB 选择
const selectedKbId = ref<number | null>(null)
const uploadKbId = ref<number | null>(null)
const kbTree = ref<KbTreeNode[]>([])
const statusOptions = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' }
]
let searchTimer: ReturnType<typeof setTimeout> | null = null

// 上传
const uploadVisible = ref(false); const uploadTab = ref('file'); const uploading = ref(false)
const isDragging = ref(false); const pendingFiles = ref<File[]>([]); const fileInputRef = ref<HTMLInputElement>()
const importUrl = ref(''); const importMode = ref('text'); const pasteText = ref(''); const pasteImage = ref('')
const customChunkSize = ref(500); const customOverlap = ref(100)

// 编辑
const editVisible = ref(false); const saving = ref(false)
const editForm = reactive<{id: number|null; title: string; tags: string; description: string}>({id:null,title:'',tags:'',description:''})

// 详情
const detailVisible = ref(false); const detailDoc = ref<DocumentItem|null>(null)
const detailChunks = ref<DocumentChunk[]>([]); const editingChunkId = ref<number|null>(null); const editingChunkContent = ref('')

// 预览
const previewVisible = ref(false); const previewLoading = ref(false)
const previewType = ref(''); const previewUrl = ref(''); const previewContent = ref('')
const previewExcelData = ref<Record<string, any[][]>>({})

const stats = reactive({ documentCount: 0, chunkCount: 0, embeddingCount: 0 })

// 文件类型 → Lucide 图标组件（列表与卡片共用，保证图标一致）
const FILE_ICONS: Record<string, any> = {
  pdf: FileText, doc: FileText, docx: FileText,
  xlsx: Table, xls: Table, csv: Table,
  md: StickyNote, txt: StickyNote, html: Code, xml: Code
}
function fileIconComp(t: string) { return FILE_ICONS[t] || FileText }
// 文件类型 → 配色类名（与卡片视图共用 .icon-*）
function fileColorClass(t: string) {
  const map: Record<string, string> = {
    pdf: 'icon-pdf', doc: 'icon-doc', docx: 'icon-docx',
    xlsx: 'icon-xlsx', xls: 'icon-xls', csv: 'icon-csv',
    md: 'icon-md', txt: 'icon-txt', html: 'icon-html', xml: 'icon-xml'
  }
  return map[t] || 'icon-md'
}

// ── 工具 ──
function statusType(s:string){ const m:Record<string,string>={PENDING:'info',PROCESSING:'warning',COMPLETED:'success',FAILED:'danger'}; return m[s]||'info' }
function statusText(s:string){ const m:Record<string,string>={PENDING:'待处理',PROCESSING:'处理中',COMPLETED:'已完成',FAILED:'失败'}; return m[s]||s }
function formatSize(b:number){ if(!b) return '-'; if(b<1024) return b+'B'; if(b<1048576) return (b/1024).toFixed(1)+'KB'; return (b/1048576).toFixed(1)+'MB' }
function parseTags(s?:string){ return s ? s.split(',').map(t=>t.trim()).filter(Boolean) : [] }
function toggleSelect(id:number, v:boolean){ if(v) selectedIds.value.push(id); else selectedIds.value = selectedIds.value.filter(i=>i!==id) }

// ── 搜索 ──
function onSearch(){ if(searchTimer) clearTimeout(searchTimer); searchTimer = setTimeout(loadDocuments, 300) }

// ── 列表 ──
async function loadDocuments(){
  loadingDocs.value = true
  try {
    const kw = searchKeyword.value || undefined
    const st = statusFilter.value || undefined
    const kbId = selectedKbId.value || undefined
    const [docsRes, statsRes] = await Promise.all([getDocuments(0, 50, kw, st, kbId), getStats(kbId)])
    documents.value = docsRes.data.content
    Object.assign(stats, statsRes.data)
  } finally { loadingDocs.value = false }
}

function onKbChange() { loadDocuments() }

async function loadKbTree() {
  try { const { data } = await getKbTree(); kbTree.value = data } catch { /* ignore */ }
}

// ── 上传 ──
async function openUploadDialog(){ uploadTab.value='file'; pendingFiles.value=[]; importUrl.value=''; pasteText.value=''; pasteImage.value=''; uploadKbId.value=selectedKbId.value; uploadVisible.value=true }
function triggerFileInput(){ fileInputRef.value?.click() }
function onFileInputChange(e:Event){ const t=e.target as HTMLInputElement; if(t.files) addFiles(Array.from(t.files)); t.value='' }
function onDrop(e:DragEvent){ isDragging.value=false; if(e.dataTransfer?.files) addFiles(Array.from(e.dataTransfer.files)) }
function addFiles(files:File[]){ const allowed=['pdf','txt','md','docx','xlsx','csv','doc','xls','html']; for(const f of files){ const ext=f.name.split('.').pop()?.toLowerCase()||''; if(allowed.includes(ext)&&f.size<=20971520) pendingFiles.value.push(f) } }
function onPaste(e:ClipboardEvent){ if(!uploadVisible.value||uploadTab.value!=='paste') return; const items=e.clipboardData?.items; if(!items) return; for(const item of items){ if(item.type.startsWith('image/')){ const blob=item.getAsFile(); if(blob){ const r=new FileReader(); r.onload=()=>{pasteImage.value=r.result as string}; r.readAsDataURL(blob) } }else if(item.type==='text/plain'){ item.getAsString(t=>{ if(t.trim()) pasteText.value=t.trim() }) } } }
function onDialogOpened(){ document.addEventListener('paste',onPaste) }
function onDialogClosed(){ document.removeEventListener('paste',onPaste) }
onBeforeUnmount(()=>document.removeEventListener('paste',onPaste))

async function handleBatchUpload(){
  if(!pendingFiles.value.length){ ElMessage.warning('请添加文件'); return }
  if(!uploadKbId.value){ ElMessage.warning('请选择目标知识库'); return }
  uploading.value = true
  try {
    const { data } = await uploadDocuments(pendingFiles.value, uploadKbId.value!)
    ElMessage.success(`上传完成: ${data.success} 成功${data.failed>0?`, ${data.failed} 失败`:''}`)
    uploadVisible.value = false; pendingFiles.value = []
    await loadDocuments(); setTimeout(loadDocuments, 3000)
  } catch(e:any){ ElMessage.error(e.response?.data?.message||'上传失败') }
  finally { uploading.value = false }
}
async function handleUrlImport(){
  if(!importUrl.value){ ElMessage.warning('请输入URL'); return }
  if(!uploadKbId.value){ ElMessage.warning('请选择目标知识库'); return }
  uploading.value = true
  try { const { data } = await importFromUrl(importUrl.value, importMode.value, uploadKbId.value!); ElMessage.success(data.message); uploadVisible.value = false; await loadDocuments(); setTimeout(loadDocuments,3000) }
  catch(e:any){ ElMessage.error(e.response?.data?.message||'导入失败') }
  finally { uploading.value = false }
}
async function handlePasteUpload(){
  uploading.value = true
  try {
    if(pasteImage.value){ const resp=await fetch(pasteImage.value); const blob=await resp.blob(); const file=new File([blob],`paste-${Date.now()}.png`,{type:'image/png'}); await uploadDocuments([file]) }
    else if(pasteText.value){ const blob=new Blob([pasteText.value],{type:'text/plain'}); const file=new File([blob],`paste-${Date.now()}.txt`,{type:'text/plain'}); await uploadDocuments([file]) }
    ElMessage.success('上传成功'); uploadVisible.value = false; pasteText.value=''; pasteImage.value=''; await loadDocuments(); setTimeout(loadDocuments,3000)
  } catch(e:any){ ElMessage.error('上传失败') }
  finally { uploading.value = false }
}

// ── 编辑 ──
function openEditDialog(row: DocumentItem){ editForm.id=row.id; editForm.title=row.title; editForm.tags=row.tags||''; editForm.description=(row as any).description||''; editVisible.value=true }
async function handleSaveEdit(){
  if(!editForm.id) return
  saving.value = true
  try { await updateDocument(editForm.id, {title:editForm.title,tags:editForm.tags,description:editForm.description}); ElMessage.success('已更新'); editVisible.value = false; await loadDocuments() }
  catch(e:any){ ElMessage.error('保存失败') }
  finally { saving.value = false }
}

// ── 详情 + 分块 ──
async function viewDetail(id:number){
  try { const { data } = await getDocumentDetail(id); detailDoc.value = data.document; detailChunks.value = data.chunks; detailVisible.value = true }
  catch { ElMessage.error('获取详情失败') }
}
function startEditChunk(c: DocumentChunk){ editingChunkId.value = c.id; editingChunkContent.value = c.content }
async function handleSaveChunk(chunkId:number){
  try { await updateChunk(chunkId, editingChunkContent.value); ElMessage.success('分块已更新'); editingChunkId.value = null; await viewDetail(detailDoc.value!.id) }
  catch { ElMessage.error('保存分块失败') }
}
async function handleDeleteChunk(chunkId:number){
  try { await ElMessageBox.confirm('确定删除该分块？','确认',{type:'warning'}); await deleteChunk(chunkId); ElMessage.success('已删除'); await viewDetail(detailDoc.value!.id) }
  catch { /* 取消 */ }
}

// ── 删除 ──
async function handleDelete(id:number){
  try { await ElMessageBox.confirm('删除后分块和向量也删除，确定？','确认',{type:'warning'}); await deleteDocument(id); ElMessage.success('已删除'); await loadDocuments() }
  catch { /* 取消 */ }
}
async function handleBatchDelete(){
  try { await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个文档？`,'确认',{type:'warning'}); await batchDeleteDocuments(selectedIds.value); ElMessage.success('已删除'); selectedIds.value=[]; await loadDocuments() }
  catch { /* 取消 */ }
}
async function handleClearAll(){
  try { await ElMessageBox.confirm('确定清空整个知识库？此操作不可恢复！','⚠️ 危险操作',{type:'error',confirmButtonText:'确认清空'}); await clearAllDocuments(); ElMessage.success('知识库已清空'); selectedIds.value=[]; await loadDocuments() }
  catch { /* 取消 */ }
}

// ── 导出 ──
async function exportData(){
  try { const { data } = await exportDocuments(); const blob = new Blob([JSON.stringify(data,null,2)],{type:'application/json'}); const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download='knowledge-export.json'; a.click(); ElMessage.success('导出成功') }
  catch { ElMessage.error('导出失败') }
}

// ── 预览 ──
async function openPreview(row: DocumentItem) {
  previewVisible.value = true; previewLoading.value = true
  previewContent.value = ''; previewType.value = ''; previewExcelData.value = {}
  const token = localStorage.getItem('accessToken')
  const url = getDocumentContentUrl(row.id)
  const fileType = row.fileType.toLowerCase()

  try {
    if (fileType === 'pdf') {
      previewType.value = 'pdf'
      // PDF 直接用 iframe，加上 token 认证
      previewUrl.value = url
    } else if (['png','jpg','jpeg','gif','svg','webp'].includes(fileType)) {
      previewType.value = 'image'
      previewUrl.value = url
    } else if (fileType === 'docx') {
      // mammoth.js 转换 docx → HTML
      const resp = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      const blob = await resp.blob()
      const arrayBuffer = await blob.arrayBuffer()
      const result = await mammoth.convertToHtml({ arrayBuffer })
      previewType.value = 'docx'
      previewContent.value = result.value
    } else if (['xlsx','xls','csv'].includes(fileType)) {
      // xlsx 库解析表格
      const resp = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      const blob = await resp.blob()
      const arrayBuffer = await blob.arrayBuffer()
      const workbook = XLSX.read(arrayBuffer, { type: 'array' })
      const sheets: Record<string, any[][]> = {}
      for (const name of workbook.SheetNames) {
        const sheet = workbook.Sheets[name]
        sheets[name] = XLSX.utils.sheet_to_json(sheet, { header: 1 }) as any[][]
      }
      previewType.value = 'xlsx'
      previewExcelData.value = sheets
    } else if (['txt','md','html','xml'].includes(fileType)) {
      // 文本文件直接显示
      const resp = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      const text = await resp.text()
      previewType.value = 'text'
      previewContent.value = text
    } else {
      // doc/xls 等旧格式，尝试显示文本
      const resp = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      const text = await resp.text()
      previewType.value = 'text'
      previewContent.value = text
    }
  } catch (e: any) {
    previewContent.value = '预览加载失败: ' + (e.message || '未知错误')
    previewType.value = 'text'
  } finally {
    previewLoading.value = false
  }
}

// Markdown 渲染（预览用）
import { marked } from 'marked'
function renderPreviewMarkdown(text: string) {
  if (!text) return ''
  return marked.parse(text) as string
}

onMounted(() => { loadKbTree(); const sq = route.query.search as string; if (sq) searchKeyword.value = sq; loadDocuments() })
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.card-title { font-weight: 600; font-size: 1rem; color: var(--text-primary); }
.header-actions { display: flex; align-items: center; gap: var(--space-sm); flex-wrap: wrap; }
.header-actions-right { display: flex; align-items: center; gap: var(--space-sm); }
.view-toggle { margin-right: 4px; }

.header-right { display: flex; align-items: center; gap: var(--space-md); }
.kb-select { width: 240px; }

.upload-footer { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); flex-wrap: wrap; }
.upload-footer-btns { display: flex; gap: var(--space-sm); margin-left: auto; }
.upload-kb-select { width: 200px; }

.stat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-md); margin-bottom: var(--space-lg); }
.tool-row { display: flex; gap: var(--space-sm); align-items: center; margin-bottom: var(--space-lg); flex-wrap: wrap; }
.search-input { max-width: 420px; flex: 1; min-width: 200px; }
.status-segment { flex-shrink: 0; }

.doc-content { min-height: 120px; }

/* ── 上传拖拽区 ── */
.drop-zone { border: 2px dashed var(--border); border-radius: var(--radius-md); padding: 32px; text-align: center; transition: all var(--duration-fast); cursor: pointer; color: var(--text-secondary); }
.drop-zone:hover, .drop-zone.drop-active { border-color: var(--primary-500); background: var(--primary-50); color: var(--primary-600); }
.drop-icon { color: var(--text-muted); }
.drop-zone:hover .drop-icon, .drop-zone.drop-active .drop-icon { color: var(--primary-600); }
.drop-zone p { margin: 8px 0; font-size: var(--text-sm); }
.pending-files { margin-top: 12px; }
.pending-header { display: flex; justify-content: space-between; align-items: center; font-size: var(--text-sm); font-weight: 500; margin-bottom: 8px; }
.pending-item { display: flex; align-items: center; gap: var(--space-sm); padding: 8px 10px; background: var(--surface-2); border-radius: var(--radius-sm); margin-bottom: 4px; font-size: var(--text-sm); }
.pending-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-primary); }
.pending-size { color: var(--text-muted); font-size: var(--text-xs); white-space: nowrap; }
.upload-tip { margin-top: 12px; font-size: var(--text-xs); color: var(--text-muted); }

/* ── 粘贴区 ── */
.paste-area { border: 2px dashed var(--border); border-radius: var(--radius-md); padding: 40px 20px; text-align: center; color: var(--text-muted); transition: all var(--duration-fast); min-height: 150px; }
.paste-area.paste-has-content { padding: 16px; text-align: left; }
.paste-area p { margin: 8px 0 0; font-size: var(--text-sm); }
.paste-icon { color: var(--text-muted); }
.paste-preview { margin-bottom: 12px; }
.paste-label { font-weight: 600; color: var(--text-primary); display: inline-flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.paste-text-preview { background: var(--surface-2); border-radius: var(--radius-sm); padding: 10px; font-size: var(--text-sm); color: var(--text-secondary); max-height: 150px; overflow-y: auto; white-space: pre-wrap; }
.paste-img-preview { max-width: 100%; max-height: 300px; border-radius: var(--radius-sm); border: 1px solid var(--border); }

/* ── 卡片视图 ── */
.card-grid { margin-top: 4px; }
.doc-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 20px 16px 14px; position: relative; transition: box-shadow var(--duration-base) var(--ease-out), border-color var(--duration-base), transform var(--duration-base) var(--ease-out); }
.doc-card:hover { box-shadow: var(--shadow-md); border-color: var(--primary-500); transform: translateY(-2px); }
.card-check { position: absolute; top: 8px; right: 10px; }
.card-icon { width: 44px; height: 44px; border-radius: var(--radius-md); display: flex; align-items: center; justify-content: center; font-size: 22px; background: var(--surface-3); margin-bottom: 10px; cursor: pointer; color: var(--text-secondary); }
.card-icon :deep(.el-icon) { font-size: 22px; }
.icon-pdf { background: var(--danger-light); color: var(--danger); }
.icon-docx, .icon-doc { background: var(--primary-50); color: var(--primary-600); }
.icon-xlsx, .icon-xls, .icon-csv { background: var(--success-light); color: var(--success); }
.icon-md, .icon-txt { background: var(--surface-3); color: var(--text-secondary); }
.card-title { font-size: var(--text-sm); font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-bottom: 8px; cursor: pointer; }
.card-tags { display: flex; gap: 4px; flex-wrap: wrap; margin-bottom: 8px; }

/* ── 列表/卡片统一文件图标盒（与卡片配色一致） ── */
.file-ico { width: 30px; height: 30px; border-radius: var(--radius-md); display: inline-flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
.file-ico :deep(.el-icon) { font-size: 16px; }
.icon-html, .icon-xml { background: var(--surface-3); color: var(--text-secondary); }

/* ── 列表表格文字统一 ── */
.doc-table :deep(.el-table__cell) { font-size: var(--text-sm); color: var(--text-primary); vertical-align: middle; }
.doc-table :deep(.el-table th .cell) { font-weight: 600; color: var(--text-primary); }
.doc-table :deep(.el-table .cell) { line-height: 1.5; }
.card-info { display: flex; gap: 12px; font-size: var(--text-xs); color: var(--text-secondary); margin-bottom: 4px; }
.card-info span { display: inline-flex; align-items: center; gap: 4px; }
.card-info :deep(.el-icon) { font-size: 14px; }
.card-time { font-size: 11px; color: var(--text-muted); margin-bottom: 8px; }
.card-actions { display: flex; gap: 4px; justify-content: flex-end; border-top: 1px solid var(--divider); padding-top: 10px; }
.row-actions { display: flex; align-items: center; gap: 2px; }

/* 行内 / 卡片图标按钮 */
.icon-btn {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  color: var(--text-muted);
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast), transform var(--duration-fast);
}
.icon-btn :deep(.el-icon) { font-size: 16px; }
.icon-btn:hover { background: var(--surface-2); color: var(--primary-600); }
.icon-btn:active { transform: scale(0.92); }
.icon-btn.danger:hover { background: var(--danger-light); color: var(--danger); }
.icon-btn:focus-visible { outline: 2px solid var(--primary-400); outline-offset: 1px; }

/* ── 分块卡片 ── */
.chunk-card { padding: 10px 12px; background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius-md); margin-bottom: 8px; transition: all var(--duration-fast); }
.chunk-card.chunk-has-vec { border-left: 3px solid var(--success); }
.chunk-header { display: flex; align-items: center; gap: var(--space-sm); margin-bottom: 6px; }
.chunk-num { font-weight: 600; color: var(--primary-600); font-size: var(--text-sm); }
.chunk-status { display: inline-flex; align-items: center; }
.vec-ok { color: var(--success); }
.vec-empty { width: 14px; height: 14px; border-radius: 50%; border: 2px solid var(--border); display: inline-block; }
.vec-legend { display: inline-flex; align-items: center; gap: 4px; }
.vec-legend.muted, .vec-legend.muted .vec-empty { color: var(--text-muted); }
.chunk-tokens { font-size: 11px; color: var(--text-muted); margin-left: auto; }
.chunk-text { font-size: var(--text-sm); color: var(--text-secondary); line-height: 1.6; margin-bottom: 4px; white-space: pre-wrap; }
.muted-text { color: var(--text-muted); }

/* ── 预览 ── */
.preview-loading { text-align: center; padding: 60px; color: var(--text-secondary); }
.preview-spin { color: var(--text-muted); }
.preview-loading p { margin-top: 16px; }
.preview-frame { width: 100%; height: 75vh; border: none; border-radius: var(--radius-md); }
.preview-image-wrap { text-align: center; }
.preview-image { max-width: 100%; max-height: 75vh; border-radius: var(--radius-md); }
.preview-empty { text-align: center; padding: 60px; color: var(--text-muted); }

.preview-html { font-size: 14px; line-height: 1.8; color: var(--text-primary); }
.preview-html :deep(h1) { font-size: var(--text-xl); margin: 16px 0 8px; }
.preview-html :deep(h2) { font-size: var(--text-lg); margin: 14px 0 6px; }
.preview-html :deep(h3) { font-size: var(--text-md); margin: 12px 0 6px; }
.preview-html :deep(p) { margin: 8px 0; }
.preview-html :deep(table) { border-collapse: collapse; width: 100%; margin: 12px 0; }
.preview-html :deep(td), .preview-html :deep(th) { border: 1px solid var(--border); padding: 6px 10px; font-size: var(--text-sm); }
.preview-html :deep(strong) { font-weight: 600; }
.preview-html :deep(em) { font-style: italic; }
.preview-text { font-size: var(--text-sm); line-height: 1.8; color: var(--text-primary); white-space: pre-wrap; background: var(--surface-2); padding: 16px; border-radius: var(--radius-md); max-height: 75vh; overflow-y: auto; }
.preview-text :deep(h1), .preview-text :deep(h2), .preview-text :deep(h3) { margin: 12px 0 6px; }
.preview-text :deep(p) { margin: 6px 0; }
.preview-text :deep(code) { background: var(--surface-3); padding: 2px 6px; border-radius: var(--radius-sm); font-size: var(--text-xs); }
.preview-text :deep(pre) { background: var(--code-bg); color: var(--code-text); padding: 12px; border-radius: var(--radius-md); overflow-x: auto; }
.preview-text :deep(pre code) { background: none; padding: 0; color: inherit; }
.excel-scroll { overflow-x: auto; }
.sheet-title { margin: 16px 0 8px; font-size: 14px; color: var(--text-primary); }
.excel-table { border-collapse: collapse; font-size: var(--text-xs); width: 100%; }
.excel-table td { border: 1px solid var(--border); padding: 4px 8px; white-space: nowrap; max-width: 300px; overflow: hidden; text-overflow: ellipsis; }
.excel-table tr:nth-child(even) { background: var(--surface-2); }
.excel-table tr:first-child td { background: var(--primary-50); font-weight: 600; }

/* ── 手机：操作按钮堆叠、表格横向滚动 ── */
@media (max-width: 768px) {
  .stat-grid { grid-template-columns: 1fr; }
  .header-actions-right { flex-wrap: wrap; }
}
@media (max-width: 480px) {
  .card-actions { flex-wrap: wrap; justify-content: flex-start; }
  .tool-row { flex-direction: column; align-items: stretch; }
  .search-input, .status-segment { max-width: 100%; width: 100%; }
  /* 触摸目标 ≥44px */
  .el-button { min-height: 44px; }
}
</style>

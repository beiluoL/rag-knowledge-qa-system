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
              <el-radio-group v-model="viewMode" size="small" class="view-toggle">
                <el-radio-button value="list">📋 列表</el-radio-button>
                <el-radio-button value="card">🎴 卡片</el-radio-button>
              </el-radio-group>
              <el-button type="primary" @click="openUploadDialog" :icon="Upload">上传</el-button>
              <el-button v-if="selectedIds.length > 0" type="danger" @click="handleBatchDelete">
                删除 ({{ selectedIds.length }})
              </el-button>
              <el-button @click="exportData">📥 导出</el-button>
              <el-button text type="danger" @click="handleClearAll">清空知识库</el-button>
              <el-button @click="loadDocuments" :icon="Refresh">刷新</el-button>
            </div>
          </div>
        </template>

        <!-- 统计 + 搜索 -->
        <el-row :gutter="16" style="margin-bottom:12px">
          <el-col :span="6"><el-statistic title="文档总数" :value="stats.documentCount" /></el-col>
          <el-col :span="6"><el-statistic title="分块总数" :value="stats.chunkCount" /></el-col>
          <el-col :span="6"><el-statistic title="向量总数" :value="stats.embeddingCount" /></el-col>
        </el-row>
        <div style="margin-bottom:12px">
          <el-input v-model="searchKeyword" placeholder="搜索文档标题或标签..." clearable
            :prefix-icon="Search" @input="onSearch" style="max-width:360px" />
        </div>

        <!-- ====== 列表视图 ====== -->
        <el-table v-if="viewMode === 'list'" :data="documents" stripe v-loading="loadingDocs"
          @selection-change="(rows: DocumentItem[]) => selectedIds = rows.map(r => r.id)">
          <el-table-column type="selection" width="40" />
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
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="viewDetail(row.id)">详情</el-button>
              <el-button size="small" text type="primary" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" text @click="openPreview(row)">预览</el-button>
              <el-button size="small" text type="danger" @click="handleDelete(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- ====== 卡片视图 ====== -->
        <div v-else class="card-grid">
          <el-row :gutter="16">
            <el-col v-for="doc in documents" :key="doc.id" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom:16px">
              <div class="doc-card">
                <div class="card-check"><el-checkbox :model-value="selectedIds.includes(doc.id)" @change="(v: boolean) => toggleSelect(doc.id, v)" /></div>
                <div class="card-icon" :class="'icon-' + doc.fileType" @click="viewDetail(doc.id)">{{ fileIcon(doc.fileType) }}</div>
                <div class="card-title" :title="doc.title" @click="viewDetail(doc.id)">{{ doc.title }}</div>
                <div class="card-tags">
                  <el-tag :type="statusType(doc.status)" size="small">{{ statusText(doc.status) }}</el-tag>
                  <el-tag size="small" type="info">{{ doc.fileType.toUpperCase() }}</el-tag>
                  <el-tag v-for="t in parseTags(doc.tags)" :key="t" size="small" type="warning">{{ t }}</el-tag>
                </div>
                <div class="card-info"><span>📄 {{ doc.chunkCount }} 块</span><span>💾 {{ formatSize(doc.fileSize) }}</span></div>
                <div class="card-time">{{ doc.createdAt?.substring(0,10) }}</div>
                <div class="card-actions">
                  <el-button size="small" text type="primary" @click="viewDetail(doc.id)">详情</el-button>
                  <el-button size="small" text type="primary" @click="openEditDialog(doc)">编辑</el-button>
                  <el-button size="small" text @click="openPreview(doc)">预览</el-button>
                  <el-button size="small" text type="danger" @click="handleDelete(doc.id)">删除</el-button>
                </div>
              </div>
            </el-col>
          </el-row>
          <el-empty v-if="documents.length === 0" description="暂无文档" />
        </div>
      </el-card>

      <!-- ═════════ 上传弹窗 ═════════ -->
      <el-dialog v-model="uploadVisible" title="上传文档" width="580px" @opened="onDialogOpened" @closed="onDialogClosed">
        <el-tabs v-model="uploadTab">
          <el-tab-pane label="📁 本地文件" name="file">
            <div class="drop-zone" :class="{ 'drop-active': isDragging }"
              @dragover.prevent="isDragging=true" @dragleave.prevent="isDragging=false" @drop.prevent="onDrop">
              <el-icon :size="40" color="#909399"><UploadFilled /></el-icon>
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
          <el-tab-pane label="🔗 URL 导入" name="url">
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
          <el-tab-pane label="📋 粘贴上传" name="paste">
            <div class="paste-area" :class="{ 'paste-has-content': pasteText || pasteImage }">
              <template v-if="!pasteText && !pasteImage">
                <el-icon :size="36" color="#c0c4cc"><DocumentCopy /></el-icon>
                <p>Ctrl+V 粘贴截图或文字</p>
              </template>
              <div v-if="pasteText" class="paste-preview">
                <span class="paste-label">📝 文字 ({{ pasteText.length }}字)</span>
                <div class="paste-text-preview">{{ pasteText.substring(0,300) }}{{ pasteText.length>300?'...':'' }}</div>
              </div>
              <div v-if="pasteImage" class="paste-preview">
                <span class="paste-label">🖼️ 图片</span>
                <img :src="pasteImage" class="paste-img-preview" />
              </div>
              <el-button v-if="pasteText||pasteImage" size="small" @click="pasteText='';pasteImage=''" style="margin-top:8px">清除</el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
        <template #footer>
          <el-button @click="uploadVisible=false">取消</el-button>
          <el-button v-if="uploadTab==='file'&&pendingFiles.length" type="primary" :loading="uploading" @click="handleBatchUpload">上传 ({{ pendingFiles.length }})</el-button>
          <el-button v-if="uploadTab==='url'&&importUrl" type="primary" :loading="uploading" @click="handleUrlImport">导入</el-button>
          <el-button v-if="uploadTab==='paste'&&(pasteText||pasteImage)" type="primary" :loading="uploading" @click="handlePasteUpload">确认上传</el-button>
        </template>
      </el-dialog>

      <!-- ═════════ 编辑弹窗 ═════════ -->
      <el-dialog v-model="editVisible" title="编辑文档" width="480px">
        <el-form label-width="70px">
          <el-form-item label="标题"><el-input v-model="editForm.title" /></el-form-item>
          <el-form-item label="标签">
            <el-input v-model="editForm.tags" placeholder="逗号分隔，如：手机,苹果,2024款" />
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
              <span v-if="!detailDoc.tags" style="color:#c0c4cc">无标签</span>
            </el-descriptions-item>
            <el-descriptions-item label="类型">{{ detailDoc.fileType }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detailDoc.status }}</el-descriptions-item>
            <el-descriptions-item label="分块数">{{ detailDoc.chunkCount }}</el-descriptions-item>
          </el-descriptions>
          <el-divider>分块列表（💚有向量 / ⬜无向量）</el-divider>
          <div v-for="chunk in detailChunks" :key="chunk.id" class="chunk-card" :class="{ 'chunk-has-vec': chunk.hasEmbedding }">
            <div class="chunk-header">
              <span class="chunk-num">#{{ chunk.chunkIndex }}</span>
              <span class="chunk-status">{{ chunk.hasEmbedding ? '💚' : '⬜' }}</span>
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
        <div v-if="previewLoading" style="text-align:center;padding:60px">
          <el-icon :size="40" class="is-loading"><Loading /></el-icon>
          <p style="margin-top:16px;color:#909399">正在加载预览...</p>
        </div>
        <!-- PDF -->
        <iframe v-if="previewType==='pdf'" :src="previewUrl" style="width:100%;height:75vh;border:none;border-radius:8px" />
        <!-- 图片 -->
        <div v-else-if="previewType==='image'" style="text-align:center">
          <img :src="previewUrl" style="max-width:100%;max-height:75vh;border-radius:8px" />
        </div>
        <!-- Word → HTML -->
        <div v-else-if="previewType==='docx'" v-html="previewContent" class="preview-html" />
        <!-- Excel → 表格 -->
        <div v-else-if="previewType==='xlsx'" class="preview-excel">
          <div v-for="(sheet, sName) in previewExcelData" :key="sName">
            <h4 style="margin:16px 0 8px">{{ sName }}</h4>
            <div style="overflow-x:auto">
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
        <div v-else-if="!previewLoading" style="text-align:center;padding:60px;color:#c0c4cc">
          暂不支持预览此格式
        </div>
      </el-drawer>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Refresh, UploadFilled, DocumentCopy, Search } from '@element-plus/icons-vue'
import { getDocuments, uploadDocuments, deleteDocument, getDocumentDetail, getStats,
  importFromUrl, updateDocument, updateChunk, deleteChunk,
  batchDeleteDocuments, clearAllDocuments, exportDocuments, getDocumentContentUrl,
  type DocumentItem, type DocumentChunk } from '@/api/knowledge'
import mammoth from 'mammoth'
import * as XLSX from 'xlsx'

const router = useRouter()
const viewMode = ref<'list'|'card'>('list')
const documents = ref<DocumentItem[]>([])
const loadingDocs = ref(false)
const searchKeyword = ref('')
const selectedIds = ref<number[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

// 上传
const uploadVisible = ref(false); const uploadTab = ref('file'); const uploading = ref(false)
const isDragging = ref(false); const pendingFiles = ref<File[]>([]); const fileInputRef = ref<HTMLInputElement>()
const importUrl = ref(''); const importMode = ref('text'); const pasteText = ref(''); const pasteImage = ref('')
const customChunkSize = ref(500); const customOverlap = ref(100)

// 编辑
const editVisible = ref(false); const saving = ref(false)
const editForm = reactive<{id: number|null; title: string; tags: string}>({id:null,title:'',tags:''})

// 详情
const detailVisible = ref(false); const detailDoc = ref<DocumentItem|null>(null)
const detailChunks = ref<DocumentChunk[]>([]); const editingChunkId = ref<number|null>(null); const editingChunkContent = ref('')

// 预览
const previewVisible = ref(false); const previewLoading = ref(false)
const previewType = ref(''); const previewUrl = ref(''); const previewContent = ref('')
const previewExcelData = ref<Record<string, any[][]>>({})

const stats = reactive({ documentCount: 0, chunkCount: 0, embeddingCount: 0 })

// ── 工具 ──
function statusType(s:string){ const m:Record<string,string>={PENDING:'info',PROCESSING:'warning',COMPLETED:'success',FAILED:'danger'}; return m[s]||'info' }
function statusText(s:string){ const m:Record<string,string>={PENDING:'待处理',PROCESSING:'处理中',COMPLETED:'已完成',FAILED:'失败'}; return m[s]||s }
function formatSize(b:number){ if(!b) return '-'; if(b<1024) return b+'B'; if(b<1048576) return (b/1024).toFixed(1)+'KB'; return (b/1048576).toFixed(1)+'MB' }
function fileIcon(t:string){ const i:Record<string,string>={pdf:'📕',docx:'📘',doc:'📘',xlsx:'📗',xls:'📗',csv:'📊',md:'📝',txt:'📄',html:'🌐'}; return i[t]||'📎' }
function parseTags(s?:string){ return s ? s.split(',').map(t=>t.trim()).filter(Boolean) : [] }
function toggleSelect(id:number, v:boolean){ if(v) selectedIds.value.push(id); else selectedIds.value = selectedIds.value.filter(i=>i!==id) }

// ── 搜索 ──
function onSearch(){ if(searchTimer) clearTimeout(searchTimer); searchTimer = setTimeout(loadDocuments, 300) }

// ── 列表 ──
async function loadDocuments(){
  loadingDocs.value = true
  try {
    const kw = searchKeyword.value || undefined
    const [docsRes, statsRes] = await Promise.all([getDocuments(0, 50, kw), getStats()])
    documents.value = docsRes.data.content
    Object.assign(stats, statsRes.data)
  } finally { loadingDocs.value = false }
}

// ── 上传 ──
function openUploadDialog(){ uploadTab.value='file'; pendingFiles.value=[]; importUrl.value=''; pasteText.value=''; pasteImage.value=''; uploadVisible.value=true }
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
  uploading.value = true
  try {
    const { data } = await uploadDocuments(pendingFiles.value)
    ElMessage.success(`上传完成: ${data.success} 成功${data.failed>0?`, ${data.failed} 失败`:''}`)
    uploadVisible.value = false; pendingFiles.value = []
    await loadDocuments(); setTimeout(loadDocuments, 3000)
  } catch(e:any){ ElMessage.error(e.response?.data?.message||'上传失败') }
  finally { uploading.value = false }
}
async function handleUrlImport(){
  if(!importUrl.value){ ElMessage.warning('请输入URL'); return }
  uploading.value = true
  try { const { data } = await importFromUrl(importUrl.value, importMode.value); ElMessage.success(data.message); uploadVisible.value = false; await loadDocuments(); setTimeout(loadDocuments,3000) }
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
function openEditDialog(row: DocumentItem){ editForm.id=row.id; editForm.title=row.title; editForm.tags=row.tags||''; editVisible.value=true }
async function handleSaveEdit(){
  if(!editForm.id) return
  saving.value = true
  try { await updateDocument(editForm.id, {title:editForm.title,tags:editForm.tags}); ElMessage.success('已更新'); editVisible.value = false; await loadDocuments() }
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

onMounted(loadDocuments)
</script>

<style scoped>
.chat-layout{height:100vh;display:flex}
.sidebar{background:#fff;border-right:1px solid #e4e7ed;display:flex;flex-direction:column}
.sidebar-header{padding:16px;border-bottom:1px solid #ebeef5}
.sidebar-footer{padding:16px;border-top:1px solid #ebeef5;margin-top:auto}
.admin-main{flex:1;padding:24px;overflow-y:auto;background:#f5f7fa}
.card-header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px}
.header-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.view-toggle{margin-right:4px}

.drop-zone{border:2px dashed #dcdfe6;border-radius:10px;padding:32px;text-align:center;transition:all .2s;cursor:pointer;color:#909399}
.drop-zone:hover,.drop-zone.drop-active{border-color:#409eff;background:#ecf5ff;color:#409eff}
.drop-zone p{margin:8px 0;font-size:13px}
.pending-files{margin-top:12px}
.pending-header{display:flex;justify-content:space-between;align-items:center;font-size:13px;font-weight:500;margin-bottom:8px}
.pending-item{display:flex;align-items:center;gap:8px;padding:8px 10px;background:#f5f7fa;border-radius:6px;margin-bottom:4px;font-size:13px}
.pending-name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.pending-size{color:#909399;font-size:12px;white-space:nowrap}
.upload-tip{margin-top:12px;font-size:12px;color:#c0c4cc}

.paste-area{border:2px dashed #dcdfe6;border-radius:10px;padding:40px 20px;text-align:center;color:#c0c4cc;transition:all .2s;min-height:150px}
.paste-area.paste-has-content{padding:16px;text-align:left}
.paste-area p{margin:8px 0 0;font-size:13px}
.paste-preview{margin-bottom:12px}.paste-label{font-weight:600;color:#303133;display:block;margin-bottom:6px}
.paste-text-preview{background:#f5f7fa;border-radius:6px;padding:10px;font-size:13px;color:#606266;max-height:150px;overflow-y:auto;white-space:pre-wrap}
.paste-img-preview{max-width:100%;max-height:300px;border-radius:6px;border:1px solid #ebeef5}

.card-grid{margin-top:4px}
.doc-card{background:#fff;border:1px solid #ebeef5;border-radius:10px;padding:20px 16px 14px;position:relative;transition:all .2s}
.doc-card:hover{box-shadow:0 4px 16px rgba(0,0,0,.08);border-color:#409eff;transform:translateY(-2px)}
.card-check{position:absolute;top:8px;right:10px}
.card-icon{width:44px;height:44px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:24px;background:#f5f7fa;margin-bottom:10px;cursor:pointer}
.icon-pdf{background:#fef0f0}.icon-docx,.icon-doc{background:#ecf5ff}.icon-xlsx,.icon-xls,.icon-csv{background:#f0f9eb}.icon-md,.icon-txt{background:#f5f7fa}
.card-title{font-size:14px;font-weight:600;color:#303133;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin-bottom:8px;cursor:pointer}
.card-tags{display:flex;gap:4px;flex-wrap:wrap;margin-bottom:8px}
.card-info{display:flex;gap:12px;font-size:12px;color:#909399;margin-bottom:4px}
.card-time{font-size:11px;color:#c0c4cc;margin-bottom:8px}
.card-actions{display:flex;gap:4px;justify-content:flex-end;border-top:1px solid #f0f0f0;padding-top:8px}

.chunk-card{padding:10px 12px;background:#fafbfc;border:1px solid #ebeef5;border-radius:8px;margin-bottom:8px;transition:all .2s}
.chunk-card.chunk-has-vec{border-left:3px solid #67c23a}
.chunk-header{display:flex;align-items:center;gap:8px;margin-bottom:6px}
.chunk-num{font-weight:600;color:#409eff;font-size:13px}
.chunk-status{font-size:16px}
.chunk-tokens{font-size:11px;color:#909399;margin-left:auto}
.chunk-text{font-size:13px;color:#606266;line-height:1.6;margin-bottom:4px;white-space:pre-wrap}

/* ── 预览 ── */
.preview-html{font-size:14px;line-height:1.8;color:#303133}
.preview-html :deep(h1){font-size:22px;margin:16px 0 8px}
.preview-html :deep(h2){font-size:18px;margin:14px 0 6px}
.preview-html :deep(h3){font-size:16px;margin:12px 0 6px}
.preview-html :deep(p){margin:8px 0}
.preview-html :deep(table){border-collapse:collapse;width:100%;margin:12px 0}
.preview-html :deep(td),.preview-html :deep(th){border:1px solid #e4e7ed;padding:6px 10px;font-size:13px}
.preview-html :deep(strong){font-weight:600}
.preview-html :deep(em){font-style:italic}
.preview-text{font-size:13px;line-height:1.8;color:#303133;white-space:pre-wrap;background:#fafbfc;padding:16px;border-radius:8px;max-height:75vh;overflow-y:auto}
.preview-text :deep(h1),.preview-text :deep(h2),.preview-text :deep(h3){margin:12px 0 6px}
.preview-text :deep(p){margin:6px 0}
.preview-text :deep(code){background:#f0f0f0;padding:2px 6px;border-radius:4px;font-size:12px}
.preview-text :deep(pre){background:#2d2d2d;color:#f8f8f2;padding:12px;border-radius:8px;overflow-x:auto}
.preview-text :deep(pre code){background:none;padding:0;color:inherit}
.excel-table{border-collapse:collapse;font-size:12px;width:100%}
.excel-table td{border:1px solid #e4e7ed;padding:4px 8px;white-space:nowrap;max-width:300px;overflow:hidden;text-overflow:ellipsis}
.excel-table tr:nth-child(even){background:#fafbfc}
.excel-table tr:first-child td{background:#f0f5ff;font-weight:600}
</style>

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
            <div>
              <el-button type="primary" @click="uploadVisible = true" :icon="Upload">上传文档</el-button>
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

        <!-- 文档表格 -->
        <el-table :data="documents" stripe v-loading="loadingDocs">
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
      </el-card>

      <!-- 上传弹窗 -->
      <el-dialog v-model="uploadVisible" title="上传文档" width="500px">
        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          accept=".pdf,.txt,.md,.docx,.xlsx,.csv,.doc,.xls"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或点击上传</div>
          <template #tip>
            <div class="el-upload__tip">
              支持 PDF / Word / Excel / Markdown / TXT，最大 20MB
            </div>
          </template>
        </el-upload>
        <template #footer>
          <el-button @click="uploadVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="handleUpload">开始上传</el-button>
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Refresh } from '@element-plus/icons-vue'
import { getDocuments, uploadDocument, deleteDocument, getDocumentDetail, getStats,
  type DocumentItem, type DocumentChunk } from '@/api/knowledge'

const router = useRouter()
const documents = ref<DocumentItem[]>([])
const loadingDocs = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const detailVisible = ref(false)
const detailDoc = ref<DocumentItem | null>(null)
const detailChunks = ref<DocumentChunk[]>([])
const uploadFile = ref<File | null>(null)

const stats = reactive({ documentCount: 0, chunkCount: 0, embeddingCount: 0 })

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

async function loadDocuments() {
  loadingDocs.value = true
  try {
    const [docsRes, statsRes] = await Promise.all([getDocuments(), getStats()])
    documents.value = docsRes.data.content
    Object.assign(stats, statsRes.data)
  } finally { loadingDocs.value = false }
}

function handleFileChange(file: any) { uploadFile.value = file.raw }
async function handleUpload() {
  if (!uploadFile.value) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  try {
    await uploadDocument(uploadFile.value)
    ElMessage.success('上传成功，正在后台处理...')
    uploadVisible.value = false
    uploadFile.value = null
    await loadDocuments()
    // 定时刷新状态
    setTimeout(loadDocuments, 3000)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  } finally { uploading.value = false }
}

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
.card-header { display: flex; justify-content: space-between; align-items: center; }
.chunk-preview { margin-bottom: 12px; padding: 8px; background: #f5f7fa; border-radius: 6px; }
.chunk-index { font-weight: 600; color: #409eff; margin-bottom: 4px; }
.chunk-content { font-size: 13px; color: #606266; line-height: 1.6; }
</style>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">对话配置</h1>
        <p class="page-subtitle">
          集中管理 AI 模式、AI 框架与 RAG 过程可视化开关，配置重启后保留。
        </p>
      </div>
      <el-button :icon="MessageSquare" @click="router.push('/chat')">返回对话</el-button>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="ui-loading" role="status" aria-live="polite">
      <el-icon class="is-loading loading-spin icon-md"><Loader2 /></el-icon>
      <span>正在加载对话配置…</span>
    </div>

    <!-- 错误提示 -->
    <el-alert
      v-else-if="error"
      type="error"
      :closable="false"
      show-icon
      class="cfg-error"
      :title="error"
    />

    <!-- 配置主体 -->
    <template v-else-if="config">
      <!-- 1) AI 模式 -->
      <section class="ui-card cfg-card">
        <div class="cfg-card-head">
          <span class="cfg-icon icon-md"><Cpu /></span>
          <div class="cfg-head-text">
            <h2 class="cfg-title">AI 模式</h2>
            <p class="cfg-desc">离线使用本机 Ollama 推理；在线使用阿里云百炼 API。</p>
          </div>
        </div>
        <el-radio-group v-model="form.aiMode" class="cfg-radio">
          <el-radio value="offline" border>离线 Ollama</el-radio>
          <el-radio value="online" border>在线 DashScope</el-radio>
        </el-radio-group>
      </section>

      <!-- 2) AI 框架 -->
      <section class="ui-card cfg-card">
        <div class="cfg-card-head">
          <span class="cfg-icon icon-md"><Workflow /></span>
          <div class="cfg-head-text">
            <h2 class="cfg-title">AI 框架</h2>
            <p class="cfg-desc">选择底层调用库：Spring AI 或 LangChain4j。</p>
          </div>
        </div>
        <el-radio-group v-model="form.aiFramework" class="cfg-radio">
          <el-radio value="spring-ai" border>Spring AI</el-radio>
          <el-radio value="langchain4j" border>LangChain4j</el-radio>
        </el-radio-group>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="cfg-tip"
          title="切换后不同 embedding 维度可能需要重新向量化"
        />
      </section>

      <!-- 3) RAG 过程可视化 -->
      <section class="ui-card cfg-card">
        <div class="cfg-card-head">
          <span class="cfg-icon icon-md"><Eye /></span>
          <div class="cfg-head-text">
            <h2 class="cfg-title">RAG 过程可视化</h2>
            <p class="cfg-desc">开启后，对话中将展示检索、重排、生成等推理步骤。</p>
          </div>
          <el-switch
            v-model="form.ragVisualizationEnabled"
            class="cfg-switch"
            aria-label="RAG 过程可视化开关"
          />
        </div>
      </section>

      <!-- 当前 embedding 维度（只读） -->
      <section class="ui-card cfg-card">
        <div class="cfg-card-head">
          <span class="cfg-icon icon-md"><Settings /></span>
          <div class="cfg-head-text">
            <h2 class="cfg-title">当前 Embedding 维度</h2>
            <p class="cfg-desc">由 AI 模式与框架共同决定，切换后可能变化。</p>
          </div>
          <span class="cfg-dimension">{{ config.dimension }}</span>
        </div>
      </section>

      <div class="cfg-actions">
        <el-button type="primary" :loading="saving" :icon="Settings" @click="handleSave">
          保存配置
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Cpu, Workflow, Eye, Settings, MessageSquare, Loader2
} from 'lucide-vue-next'
import {
  getConversationConfig, updateConversationConfig,
  type ConversationConfig as Config
} from '@/api/conversation-config'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const config = ref<Config | null>(null)

const form = reactive<{
  aiMode: string
  aiFramework: string
  ragVisualizationEnabled: boolean
}>({
  aiMode: 'offline',
  aiFramework: 'spring-ai',
  ragVisualizationEnabled: true
})

async function loadConfig() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await getConversationConfig()
    config.value = data
    form.aiMode = data.aiMode
    form.aiFramework = data.aiFramework
    form.ragVisualizationEnabled = data.ragVisualizationEnabled
  } catch (e: any) {
    error.value = e.response?.data?.message || '加载对话配置失败'
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const { data } = await updateConversationConfig({
      aiMode: form.aiMode,
      aiFramework: form.aiFramework,
      ragVisualizationEnabled: form.ragVisualizationEnabled
    })
    config.value = data
    ElMessage.success('对话配置已保存')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.cfg-card { padding: var(--space-xl); margin-bottom: var(--space-lg); }

.cfg-card-head {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.cfg-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--primary-50);
  color: var(--primary-600);
}

.cfg-head-text { flex: 1; min-width: 0; }

.cfg-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
}

.cfg-desc {
  margin: 4px 0 0;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.cfg-radio {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
  margin-top: var(--space-lg);
}

.cfg-switch { margin-left: auto; }

.cfg-tip { margin-top: var(--space-md); }

.cfg-dimension {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--primary-600);
  font-variant-numeric: tabular-nums;
  font-family: var(--font-mono);
}

.cfg-error { margin-bottom: var(--space-lg); }

.cfg-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-md);
}

.loading-spin { color: var(--text-muted); }

@media (max-width: 480px) {
  .cfg-radio { flex-direction: column; }
  .cfg-radio :deep(.el-radio) { width: 100%; margin-right: 0; }
}
</style>

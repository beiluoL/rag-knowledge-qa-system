<template>
  <div class="page-container writing-page">
    <!-- 头部 -->
    <header class="page-header">
      <div class="page-header__main">
        <div class="page-header__icon" aria-hidden="true">
          <el-icon><PenLine /></el-icon>
        </div>
        <div>
          <h1 class="page-title">智能写作</h1>
          <p class="page-subtitle">基于知识库内容辅助撰写学习总结、报告或文章，所有内容锚定资料并附引用来源。</p>
        </div>
      </div>
    </header>

    <!-- 配置区 -->
    <section class="ui-card writing-config" aria-label="写作配置">
      <div class="writing-config__grid">
        <div class="writing-config__field writing-config__field--grow">
          <label class="field-label" for="topic-input">
            文章主题 <span class="req" aria-hidden="true">*</span>
          </label>
          <el-input
            id="topic-input"
            v-model="topic"
            placeholder="例如：Spring Boot 事务管理最佳实践"
          />
        </div>
        <div class="writing-config__field">
          <label class="field-label" for="kb-select">知识库范围</label>
          <el-tree-select
            id="kb-select"
            v-model="kbId"
            :data="kbTree"
            :props="{ label: 'name', children: 'children' }"
            value-key="id"
            node-key="id"
            placeholder="全部知识库"
            clearable
            default-expand-all
            style="width: 100%"
          />
        </div>
        <div class="writing-config__field">
          <label class="field-label" for="style-select">风格</label>
          <el-select
            id="style-select"
            v-model="style"
            placeholder="默认"
            clearable
            style="width: 100%"
          >
            <el-option label="正式" value="正式" />
            <el-option label="通俗" value="通俗易懂" />
            <el-option label="教学" value="教学讲解" />
            <el-option label="简洁" value="简洁要点" />
          </el-select>
        </div>
        <div class="writing-config__field">
          <label class="field-label" for="length-input">篇幅(字)</label>
          <el-input-number
            id="length-input"
            v-model="length"
            :min="200"
            :max="3000"
            :step="200"
            controls-position="right"
            style="width: 100%"
          />
        </div>
      </div>
      <div class="writing-config__field writing-config__field--full">
        <label class="field-label" for="outline-input">大纲要求（可选）</label>
        <el-input
          id="outline-input"
          v-model="outline"
          type="textarea"
          :rows="2"
          placeholder="例如：先讲概念，再给示例，最后总结常见坑"
        />
      </div>
      <div class="writing-config__actions">
        <el-button type="primary" :loading="loading" :icon="PenLine" @click="compose">生成文章</el-button>
        <el-button v-if="article" :icon="Copy" @click="copyArticle">复制全文</el-button>
      </div>
    </section>

    <!-- 加载中 -->
    <div v-if="loading" class="ui-loading writing-loading" role="status" aria-live="polite">
      <el-icon class="is-loading"><Loader2 /></el-icon>
      <span>正在基于知识库撰写文章，请稍候…</span>
    </div>

    <!-- 结果区 -->
    <section v-else-if="article" class="ui-card writing-result">
      <div class="writing-result__head">
        <h2 class="writing-result__title">
          <el-icon aria-hidden="true"><FileText /></el-icon> 生成结果
        </h2>
        <span class="writing-result__meta">主题：{{ topic }}</span>
      </div>
      <div class="article markdown-body" v-html="md(article)" />
      <div v-if="references.length" class="refs">
        <el-divider content-position="left">
          <span class="refs__title"><el-icon aria-hidden="true"><Library /></el-icon> 引用来源</span>
        </el-divider>
        <div class="refs__tags">
          <el-tag
            v-for="(r, i) in references"
            :key="r.chunkId"
            type="success"
            size="small"
            class="ref-tag"
          >
            [{{ i + 1 }}] {{ r.documentTitle }} · {{ (r.score * 100).toFixed(0) }}%
          </el-tag>
        </div>
      </div>
    </section>

    <div v-else class="empty-card">
      <el-icon class="empty-icon-xl"><Pencil /></el-icon>
      <h3>AI 智能写作</h3>
      <p>填写文章主题，选择知识库范围，AI 将从你的知识库中提取素材撰文。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Loader2,
  PenLine,
  Copy,
  FileText,
  Library
} from 'lucide-vue-next'
import { marked } from 'marked'
import hljs from 'highlight.js'
import { getKbTree, type KbTreeNode } from '@/api/knowledgeBase'
import { composeWriting } from '@/api/writing'
import type { Reference } from '@/api/chat'

const kbId = ref<number | null>(null)
const kbTree = ref<KbTreeNode[]>([])
const topic = ref('')
const style = ref('')
const outline = ref('')
const length = ref(800)
const loading = ref(false)
const article = ref('')
const references = ref<Reference[]>([])

const renderer = new marked.Renderer()
renderer.code = function ({ text, lang }: { text: string; lang?: string }) {
  if (lang && hljs.getLanguage(lang)) {
    return `<pre><code class="hljs language-${lang}">${hljs.highlight(text, { language: lang }).value}</code></pre>`
  }
  return `<pre><code class="hljs">${hljs.highlightAuto(text).value}</code></pre>`
}
marked.setOptions({ renderer })
function md(text: string) {
  return text ? (marked.parse(text) as string) : ''
}

async function loadKb() {
  try {
    const { data } = await getKbTree()
    kbTree.value = data || []
  } catch { /* ignore */ }
}

async function compose() {
  if (!topic.value.trim()) {
    ElMessage.warning('请填写文章主题')
    return
  }
  loading.value = true
  article.value = ''
  try {
    const { data } = await composeWriting({
      topic: topic.value.trim(),
      knowledgeBaseId: kbId.value ?? undefined,
      outline: outline.value.trim() || undefined,
      style: style.value || undefined,
      length: length.value
    })
    article.value = data.article || ''
    references.value = data.references || []
  } catch (e: any) {
    ElMessage.error('生成失败：' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

function copyArticle() {
  navigator.clipboard.writeText(article.value).then(
    () => ElMessage.success('已复制全文'),
    () => ElMessage.error('复制失败')
  )
}

onMounted(loadKb)
</script>

<style scoped>
.writing-page {
  max-width: 980px;
}

/* ── 头部图标 ── */
.page-header__main { display: flex; align-items: flex-start; gap: var(--space-lg); }
.page-header__icon {
  width: 44px; height: 44px; flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--primary-50);
  color: var(--primary-600);
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
}

/* ── 配置区 ── */
.writing-config { padding: var(--space-xl) var(--space-2xl); }
.writing-config__grid { display: flex; gap: var(--space-lg); flex-wrap: wrap; align-items: flex-end; }
.writing-config__field { display: flex; flex-direction: column; gap: var(--space-sm); }
.writing-config__field--grow { flex: 1; min-width: 240px; }
.writing-config__field--full { margin-top: var(--space-lg); }
.field-label { font-size: 0.8125rem; font-weight: 600; color: var(--text-secondary); }
.req { color: var(--danger); }
.writing-config__actions { margin-top: var(--space-lg); display: flex; gap: var(--space-md); flex-wrap: wrap; }

/* ── 加载态 ── */
.writing-loading { border: 1px solid var(--border); border-radius: var(--radius-lg); }

/* ── 结果区（宽度自适应） ── */
.writing-result { padding: var(--space-2xl); }
.writing-result__head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-lg); flex-wrap: wrap; }
.writing-result__title { display: inline-flex; align-items: center; gap: var(--space-sm); font-size: 1.0625rem; font-weight: 700; color: var(--text-primary); margin: 0; }
.writing-result__meta { font-size: 0.8125rem; color: var(--text-muted); }
.article { font-size: 0.9375rem; color: var(--text-primary); max-width: 760px; }

.refs { margin-top: var(--space-md); }
.refs__title { display: inline-flex; align-items: center; gap: var(--space-xs); color: var(--text-secondary); }
.refs__tags { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.ref-tag { cursor: default; }

/* ── 响应式：手机端配置区垂直堆叠、按钮全宽 ── */
@media (max-width: 640px) {
  .writing-config { padding: var(--space-lg); }
  .writing-config__grid { flex-direction: column; align-items: stretch; }
  .writing-config__field { width: 100%; }
  .writing-config__actions { flex-direction: column; }
  .writing-config__actions .el-button { width: 100%; }
  .writing-result { padding: var(--space-lg); }
}
</style>

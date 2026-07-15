<template>
  <div class="page-container">
    <div v-if="loading" class="ui-loading" role="status">
      <el-icon class="is-loading icon-md"><Loader2 /></el-icon>
      <span>正在加载章节内容…</span>
    </div>

    <template v-else-if="detail">
      <div class="article-topbar">
        <button class="back-btn" type="button" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回路径
        </button>
        <div class="topbar-path">
          <span class="topbar-path-label">{{ pathTitle }}</span>
          <span class="topbar-sep">/</span>
          <span class="topbar-node-label">{{ detail.title }}</span>
        </div>
      </div>

      <article class="article-card ui-card">
        <header class="article-head">
          <h1 class="article-title">{{ detail.title }}</h1>
          <p v-if="detail.description" class="article-desc">{{ detail.description }}</p>
          <div v-if="detail.document" class="article-meta">
            <el-tag size="small" type="info">{{ detail.document.fileType?.toUpperCase() }}</el-tag>
            <span class="meta-doc">{{ detail.document.title }}</span>
            <span class="meta-status" :class="detail.document.status">
              {{ detail.document.status === 'COMPLETED' ? '已解析' : detail.document.status }}
            </span>
          </div>
        </header>

        <div v-if="detail.chunks && detail.chunks.length" class="article-body markdown-body">
          <template v-for="c in detail.chunks" :key="c.index">
            <div v-html="renderMd(c.content)" class="chunk-section" />
          </template>
        </div>

        <div v-else class="article-empty">
          <el-icon class="icon-xl"><FileText /></el-icon>
          <p>该章节暂无内容。关联的文档可能尚未解析完成。</p>
        </div>
      </article>
    </template>

    <el-alert v-else type="error" :closable="false" show-icon :title="error || '章节不存在'" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loader2, ArrowLeft, FileText } from 'lucide-vue-next'
import { getNodeDetail, type NodeDetail } from '@/api/learningPath'
import { getLearningPathDetail } from '@/api/learningPath'
import { marked } from 'marked'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const detail = ref<NodeDetail | null>(null)
const pathTitle = ref('')

function renderMd(text: string) {
  if (!text) return ''
  return marked.parse(text) as string
}

onMounted(async () => {
  const pathId = Number(route.params.pathId)
  const nodeId = Number(route.params.nodeId)
  if (!pathId || !nodeId) { error.value = '参数无效'; loading.value = false; return }

  try {
    const [pathRes, nodeRes] = await Promise.all([
      getLearningPathDetail(pathId),
      getNodeDetail(pathId, nodeId)
    ])
    pathTitle.value = pathRes.data.title || '学习路径'
    detail.value = nodeRes.data
  } catch (e: any) {
    error.value = e.response?.data?.message || '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.article-topbar {
  display: flex; align-items: center; gap: var(--space-lg);
  padding: var(--space-lg) 0; margin-bottom: var(--space-xl);
  border-bottom: 1px solid var(--divider);
}
.back-btn {
  border: 1px solid var(--border); background: var(--surface); color: var(--text-secondary);
  border-radius: var(--radius-md); padding: 8px 12px; cursor: pointer; font: inherit;
  display: inline-flex; align-items: center; gap: 4px; flex-shrink: 0;
  transition: border-color var(--duration-fast), color var(--duration-fast);
}
.back-btn:hover { border-color: var(--primary-200); color: var(--primary-600); }
.topbar-path { display: flex; align-items: center; gap: var(--space-sm); font-size: 0.875rem; color: var(--text-muted); }
.topbar-path-label { color: var(--text-secondary); }
.topbar-sep { color: var(--text-muted); }
.topbar-node-label { color: var(--text-primary); font-weight: 600; }

/* 文章卡片 */
.article-card { padding: var(--space-3xl); max-width: 820px; margin: 0 auto; }
.article-head { margin-bottom: var(--space-2xl); padding-bottom: var(--space-lg); border-bottom: 1px solid var(--divider); }
.article-title { font-size: 1.75rem; font-weight: 800; color: var(--text-primary); line-height: 1.3; margin: 0 0 var(--space-sm); }
.article-desc { font-size: 0.9375rem; color: var(--text-secondary); margin: 0; }
.article-meta { display: flex; align-items: center; gap: var(--space-sm); margin-top: var(--space-md); }
.meta-doc { font-size: 0.8125rem; color: var(--text-secondary); }
.meta-status { font-size: 0.75rem; padding: 2px 8px; border-radius: 999px; background: var(--surface-3); color: var(--text-muted); }
.meta-status.COMPLETED { background: var(--success-light); color: var(--success); }

.article-body { font-size: 1rem; line-height: 1.85; color: var(--text-primary); }
.chunk-section { margin-bottom: var(--space-xl); }

.article-body :deep(h1) { font-size: 1.5rem; font-weight: 700; margin: 1.5em 0 0.5em; color: var(--text-primary); }
.article-body :deep(h2) { font-size: 1.25rem; font-weight: 700; margin: 1.4em 0 0.4em; color: var(--text-primary); }
.article-body :deep(h3) { font-size: 1.1rem; font-weight: 600; margin: 1.2em 0 0.3em; color: var(--text-primary); }
.article-body :deep(p) { margin: 0.8em 0; }
.article-body :deep(ul), .article-body :deep(ol) { padding-left: 1.5em; margin: 0.6em 0; }
.article-body :deep(li) { margin: 0.3em 0; }
.article-body :deep(pre) { background: var(--code-bg); color: var(--code-text); border-radius: var(--radius-md); padding: var(--space-lg); overflow-x: auto; margin: 1em 0; }
.article-body :deep(code) { background: var(--surface-3); padding: 2px 6px; border-radius: var(--radius-sm); font-size: 0.875em; }
.article-body :deep(pre code) { background: none; padding: 0; }
.article-body :deep(blockquote) { border-left: 3px solid var(--primary-300); padding-left: var(--space-lg); color: var(--text-secondary); margin: 1em 0; }
.article-body :deep(table) { border-collapse: collapse; width: 100%; margin: 1em 0; }
.article-body :deep(th), .article-body :deep(td) { border: 1px solid var(--border); padding: 8px 12px; text-align: left; }
.article-body :deep(th) { background: var(--surface-2); font-weight: 600; }

.article-empty { text-align: center; padding: var(--space-4xl); color: var(--text-muted); }
.article-empty p { margin-top: var(--space-md); }

@media (max-width: 640px) {
  .article-card { padding: var(--space-xl); }
  .article-title { font-size: 1.35rem; }
}
</style>

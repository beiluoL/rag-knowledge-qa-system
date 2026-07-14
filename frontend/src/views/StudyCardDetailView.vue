<template>
  <div class="page-container card-detail">
    <!-- 顶部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">卡片详情</h1>
        <p class="page-subtitle">
          <el-icon class="crumb-ico"><BookOpen /></el-icon>
          {{ card?.documentTitle || '加载中…' }}
          <span class="crumb-idx">· 第 {{ Number(index) + 1 }} / {{ total }} 张</span>
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="ArrowLeft" @click="router.push('/learn/cards' + (kbId ? `?kb=${kbId}` : ''))">返回卡片库</el-button>
        <el-button :icon="List" @click="router.push('/learn')">学习中心</el-button>
      </div>
    </div>

    <div v-if="loading" class="ui-loading" aria-busy="true">
      <el-icon class="is-loading"><Loader2 /></el-icon> 正在加载卡片…
    </div>
    <div v-else-if="!card" class="ui-empty">
      <el-icon class="empty-icon"><FileQuestion /></el-icon>
      <p>未找到该卡片，可能已被移除。</p>
      <el-button type="primary" @click="router.push('/learn/cards')">返回卡片库</el-button>
    </div>

    <template v-else>
      <el-card class="detail-card">
        <!-- 问题 / 要点 -->
        <div class="q-block">
          <span class="q-tag">要点</span>
          <div class="q-text">{{ card.front }}</div>
        </div>

        <!-- 完整内容（文字 + 代码） -->
        <el-divider>完整内容</el-divider>
        <div class="md-content" v-html="renderedBack"></div>
      </el-card>

      <!-- 上一张 / 下一张 -->
      <div class="nav-bar">
        <el-button :icon="ArrowLeft" :disabled="Number(index) <= 0" @click="go(Number(index) - 1)">上一张</el-button>
        <span class="nav-pos">{{ Number(index) + 1 }} / {{ total }}</span>
        <el-button type="primary" :icon="ArrowRight" :disabled="Number(index) >= total - 1" @click="go(Number(index) + 1)">
          下一张
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, BookOpen, List, Loader2, FileQuestion } from 'lucide-vue-next'
import { getStudyCards, type StudyCard } from '@/api/learning'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()
const kbId = route.params.kbId as string
const index = route.params.index as string

const card = ref<StudyCard | null>(null)
const total = ref(0)
const loading = ref(false)
const renderedBack = computed(() => renderMarkdown(card.value?.back))

async function load() {
  loading.value = true
  try {
    const { data } = await getStudyCards(Number(kbId), 'list')
    total.value = data?.length || 0
    card.value = data?.[Number(index)] ?? null
  } finally {
    loading.value = false
  }
}

function go(i: number) {
  router.push(`/learn/card/${kbId}/${i}`)
}

// 路由参数变化时重新加载（上一张 / 下一张）
watch(() => route.params.index, load)
onMounted(load)
</script>

<style scoped>
.header-actions { display: flex; gap: var(--space-sm); flex-wrap: wrap; }
.crumb-ico { font-size: 14px; vertical-align: -2px; color: var(--text-muted); }
.crumb-idx { color: var(--text-muted); font-size: var(--text-sm); margin-left: 4px; }

.detail-card { margin-bottom: var(--space-lg); }

/* 问题块 */
.q-block { display: flex; align-items: flex-start; gap: var(--space-md); padding: var(--space-md) var(--space-lg); background: var(--primary-50); border-left: 3px solid var(--primary-500); border-radius: var(--radius-sm); }
.q-tag { font-size: var(--text-xs); font-weight: 700; color: var(--primary-600); background: var(--surface); border: 1px solid var(--primary-100); border-radius: var(--radius-sm); padding: 2px 8px; flex-shrink: 0; }
.q-text { font-size: var(--text-base); font-weight: 600; color: var(--text-primary); line-height: 1.6; }

/* 渲染内容（与 ChatView markdown 风格一致） */
.md-content { font-size: var(--text-sm); line-height: 1.8; color: var(--text-primary); word-break: break-word; }
.md-content :deep(h1), .md-content :deep(h2), .md-content :deep(h3) { margin: 16px 0 8px; line-height: 1.4; }
.md-content :deep(h1) { font-size: var(--text-xl); }
.md-content :deep(h2) { font-size: var(--text-lg); }
.md-content :deep(h3) { font-size: var(--text-md); }
.md-content :deep(p) { margin: 8px 0; }
.md-content :deep(a) { color: var(--primary-600); text-decoration: none; }
.md-content :deep(a:hover) { text-decoration: underline; }
.md-content :deep(ul), .md-content :deep(ol) { padding-left: 22px; margin: 8px 0; }
.md-content :deep(li) { margin: 4px 0; }
.md-content :deep(blockquote) { border-left: 3px solid var(--border); padding-left: 12px; color: var(--text-secondary); margin: 8px 0; }
.md-content :deep(table) { border-collapse: collapse; width: 100%; margin: 12px 0; }
.md-content :deep(td), .md-content :deep(th) { border: 1px solid var(--border); padding: 6px 10px; font-size: var(--text-sm); }
.md-content :deep(img) { max-width: 100%; border-radius: var(--radius-sm); }
.md-content :deep(hr) { border: none; border-top: 1px solid var(--divider); margin: 16px 0; }
.md-content :deep(strong) { font-weight: 600; }
.md-content :deep(code):not(.hljs) { background: var(--surface-3); padding: 2px 6px; border-radius: var(--radius-sm); font-size: 0.9em; }
.md-content :deep(pre) { background: #1e293b; border-radius: var(--radius-md); padding: 14px 16px; overflow-x: auto; margin: 12px 0; }
.md-content :deep(pre code.hljs) { background: none; padding: 0; color: #e2e8f0; font-size: var(--text-sm); line-height: 1.6; }

/* 导航 */
.nav-bar { display: flex; align-items: center; justify-content: center; gap: var(--space-lg); }
.nav-pos { font-size: var(--text-sm); color: var(--text-secondary); }

/* 空 / 加载 */
.empty-icon { font-size: 40px; color: var(--text-muted); display: block; margin-bottom: var(--space-md); }
.ui-empty { text-align: center; padding: 48px; }
.ui-empty p { color: var(--text-muted); max-width: 360px; margin: 0 auto var(--space-md); line-height: 1.6; }
.ui-loading { text-align: center; padding: 48px; color: var(--text-secondary); display: flex; align-items: center; justify-content: center; gap: var(--space-sm); }
.is-loading { animation: rotating 1.2s linear infinite; }
@keyframes rotating { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>

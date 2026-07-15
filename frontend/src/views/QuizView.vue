<template>
  <div class="page-container quiz-page">
    <!-- 头部 -->
    <header class="page-header">
      <div class="page-header__main">
        <div class="page-header__icon" aria-hidden="true">
          <el-icon><FileText /></el-icon>
        </div>
        <div>
          <h1 class="page-title">智能出题</h1>
          <p class="page-subtitle">基于知识库内容自动生成练习题，覆盖不同知识点，答案均附来源，避免「凭空编造」。</p>
        </div>
      </div>
    </header>

    <!-- 配置区 -->
    <section class="ui-card quiz-config" aria-label="出题配置">
      <div class="quiz-config__row">
        <div class="quiz-config__field quiz-config__field--grow">
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
        <div class="quiz-config__field">
          <span class="field-label" id="type-label">题型</span>
          <el-radio-group v-model="quizType" aria-labelledby="type-label">
            <el-radio value="mc">选择题</el-radio>
            <el-radio value="qa">问答题</el-radio>
          </el-radio-group>
        </div>
        <div class="quiz-config__field">
          <label class="field-label" for="count-input">题目数量</label>
          <el-input-number
            id="count-input"
            v-model="count"
            :min="1"
            :max="20"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
        </div>
        <el-button
          type="primary"
          class="quiz-config__submit"
          :loading="loading"
          :icon="Wand2"
          @click="generate"
        >
          生成题目
        </el-button>
      </div>
    </section>

    <!-- 加载中 -->
    <div v-if="loading" class="ui-loading quiz-loading" role="status" aria-live="polite">
      <el-icon class="is-loading"><Loader2 /></el-icon>
      <span>正在基于知识库内容生成题目，请稍候…</span>
    </div>

    <!-- 结果区 -->
    <div v-else-if="questions.length" class="quiz-body">
      <section v-if="quizType === 'mc'" class="ui-card quiz-score" aria-label="答题统计">
        <div class="quiz-score__item">
          <span class="quiz-score__num">{{ answeredCount }}</span>
          <span class="quiz-score__label">已答 / {{ questions.length }}</span>
        </div>
        <div class="quiz-score__item">
          <span class="quiz-score__num quiz-score__num--ok">{{ correctCount }}</span>
          <span class="quiz-score__label">正确</span>
        </div>
        <div class="quiz-score__item">
          <span class="quiz-score__num">{{ accuracy }}%</span>
          <span class="quiz-score__label">正确率</span>
        </div>
        <el-button size="small" text type="primary" :icon="RotateCcw" @click="resetAnswers">重做</el-button>
      </section>

      <article v-for="q in questions" :key="q.index" class="ui-card q-card">
        <div class="q-head">
          <span class="q-index" aria-hidden="true">{{ q.index }}</span>
          <span class="q-q" v-html="md(q.question)" />
        </div>

        <!-- 选择题 -->
        <div v-if="quizType === 'mc'" class="q-options">
          <label
            v-for="(opt, i) in q.options"
            :key="i"
            class="opt"
            :class="optionClass(q, opt)"
          >
            <input
              type="radio"
              :name="'q' + q.index"
              :value="opt"
              v-model="q._selected"
              :disabled="q._revealed"
              @change="onSelect(q)"
            />
            <span class="opt-text" v-html="md(opt)" />
          </label>
        </div>

        <div class="q-actions">
          <el-button size="small" :icon="Eye" @click="toggleReveal(q)">
            {{ q._revealed ? '隐藏答案' : '显示答案' }}
          </el-button>
          <span v-if="q._revealed && q.source" class="q-source">
            <el-icon aria-hidden="true"><Library /></el-icon>
            来源：{{ q.source }}
          </span>
        </div>

        <div v-if="q._revealed" class="q-answer">
          <div class="ans-row">
            <span class="ans-label">
              <el-icon aria-hidden="true"><CheckCircle2 /></el-icon> 答案
            </span>
            <div class="ans-text" v-html="md(q.answer)" />
          </div>
          <div class="ans-row">
            <span class="ans-label">
              <el-icon aria-hidden="true"><Info /></el-icon> 解析
            </span>
            <div class="ans-text" v-html="md(q.explanation)" />
          </div>
        </div>
      </article>
    </div>

    <div v-else class="empty-card">
      <el-icon class="empty-icon-xl"><Wand2 /></el-icon>
      <h3>准备开始智能出题</h3>
      <p>选择上方知识库范围、题型和数量，AI 将从知识库中提取要点生成练习题。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Loader2,
  Wand2,
  Eye,
  FileText,
  Library,
  CheckCircle2,
  Info,
  RotateCcw
} from 'lucide-vue-next'
import { marked } from 'marked'
import hljs from 'highlight.js'
import { getKbTree, type KbTreeNode } from '@/api/knowledgeBase'
import { generateQuiz, type QuizQuestion } from '@/api/quiz'

interface QuizItem extends QuizQuestion {
  _selected: string
  _revealed: boolean
}

const kbId = ref<number | null>(null)
const kbTree = ref<KbTreeNode[]>([])
const quizType = ref<'mc' | 'qa'>('mc')
const count = ref(5)
const loading = ref(false)
const questions = ref<QuizItem[]>([])
const route = useRoute()

function treeHasId(nodes: KbTreeNode[], id: number): boolean {
  for (const n of nodes) {
    if (n.id === id) return true
    if (n.children && treeHasId(n.children, id)) return true
  }
  return false
}

const renderer = new marked.Renderer()
renderer.code = function ({ text, lang }: { text: string; lang?: string }) {
  if (lang && hljs.getLanguage(lang)) {
    return `<pre><code class="hljs language-${lang}">${hljs.highlight(text, { language: lang }).value}</code></pre>`
  }
  return `<pre><code class="hljs">${hljs.highlightAuto(text).value}</code></pre>`
}
marked.setOptions({ renderer })
function md(text?: string) {
  return text ? (marked.parse(text) as string) : ''
}

const answeredCount = computed(() => questions.value.filter(q => q._selected).length)
const correctCount = computed(() =>
  questions.value.filter(q => q._revealed && q._selected && q._selected === q.answer).length
)
const accuracy = computed(() => {
  const ans = answeredCount.value
  return ans === 0 ? 0 : Math.round((correctCount.value / ans) * 100)
})

function optionClass(q: QuizItem, opt: string) {
  if (!q._revealed) return q._selected === opt ? 'opt-selected' : ''
  if (opt === q.answer) return 'opt-correct'
  if (q._selected === opt) return 'opt-wrong'
  return ''
}
function onSelect(_q?: QuizItem) { /* 选择即记录，揭示后判分 */ }
function toggleReveal(q: QuizItem) { q._revealed = !q._revealed }
function resetAnswers() {
  questions.value.forEach(q => { q._selected = ''; q._revealed = false })
}

async function loadKb() {
  try {
    const { data } = await getKbTree()
    kbTree.value = data || []
  } catch { /* ignore */ }
}

async function generate() {
  loading.value = true
  try {
    const { data } = await generateQuiz({ knowledgeBaseId: kbId.value ?? undefined, type: quizType.value, count: count.value })
    questions.value = (data.questions || []).map(q => ({ ...q, _selected: '', _revealed: false }))
  } catch (e: any) {
    ElMessage.error('生成失败：' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadKb()
  const qKb = route.query.kb ? Number(route.query.kb) : null
  if (qKb && treeHasId(kbTree.value, qKb)) {
    kbId.value = qKb
    generate()
  }
})
</script>

<style scoped>
.quiz-page {
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
.quiz-config { padding: var(--space-xl) var(--space-2xl); }
.quiz-config__row { display: flex; align-items: flex-end; gap: var(--space-lg); flex-wrap: wrap; }
.quiz-config__field { display: flex; flex-direction: column; gap: var(--space-sm); }
.quiz-config__field--grow { flex: 1; min-width: 240px; }
.quiz-config__submit { flex-shrink: 0; }

.field-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text-secondary);
}

/* ── 加载态 ── */
.quiz-loading { border: 1px solid var(--border); border-radius: var(--radius-lg); }

/* ── 答题统计 ── */
.quiz-score {
  display: flex;
  align-items: center;
  gap: var(--space-2xl);
  padding: var(--space-lg) var(--space-xl);
  margin-bottom: var(--space-lg);
  flex-wrap: wrap;
}
.quiz-score__item { display: flex; flex-direction: column; gap: 2px; }
.quiz-score__num { font-size: 1.25rem; font-weight: 800; color: var(--text-primary); line-height: 1.2; }
.quiz-score__num--ok { color: var(--success); }
.quiz-score__label { font-size: 0.8125rem; color: var(--text-secondary); }

/* ── 题目卡片 ── */
.q-card { padding: var(--space-xl); margin-bottom: var(--space-lg); }
.q-head { display: flex; gap: var(--space-md); align-items: flex-start; margin-bottom: var(--space-md); }
.q-index {
  flex-shrink: 0; width: 28px; height: 28px; border-radius: var(--radius-md);
  background: var(--brand-gradient); color: var(--text-inverse);
  font-size: 0.8125rem; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.q-q { font-size: 0.9375rem; font-weight: 600; color: var(--text-primary); line-height: 1.6; }

.q-options { display: flex; flex-direction: column; gap: var(--space-sm); margin: var(--space-xs) 0 var(--space-md); }
.opt {
  display: flex; align-items: flex-start; gap: var(--space-md);
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color var(--duration-fast), background-color var(--duration-fast);
  font-size: 0.875rem;
  color: var(--text-primary);
  min-height: 44px;
}
.opt:hover { border-color: var(--primary-500); background: var(--primary-50); }
.opt:focus-within { border-color: var(--primary-500); box-shadow: 0 0 0 3px var(--primary-100); }
.opt input { margin-top: 4px; accent-color: var(--primary-600); flex-shrink: 0; }
.opt-text { flex: 1; line-height: 1.6; }
.opt-selected { border-color: var(--primary-500); background: var(--primary-50); }
.opt-correct { border-color: var(--success); background: var(--success-light); }
.opt-wrong { border-color: var(--danger); background: var(--danger-light); }

.q-actions { display: flex; align-items: center; gap: var(--space-md); flex-wrap: wrap; }
.q-source { display: inline-flex; align-items: center; gap: var(--space-xs); font-size: 0.8125rem; color: var(--text-secondary); }

.q-answer {
  margin-top: var(--space-md);
  padding: var(--space-lg);
  border-radius: var(--radius-md);
  background: var(--surface-2);
  border: 1px solid var(--border);
}
.ans-row { margin-bottom: var(--space-sm); }
.ans-row:last-child { margin-bottom: 0; }
.ans-label {
  display: inline-flex; align-items: center; gap: var(--space-xs);
  font-size: 0.8125rem; font-weight: 700; color: var(--text-secondary);
  margin-bottom: var(--space-xs);
}
.ans-text { font-size: 0.875rem; color: var(--text-primary); line-height: 1.7; }

/* ── 响应式：手机端配置区垂直堆叠，选项点击区域更大 ── */
@media (max-width: 640px) {
  .quiz-config { padding: var(--space-lg); }
  .quiz-config__row { flex-direction: column; align-items: stretch; }
  .quiz-config__field { width: 100%; }
  .quiz-config__submit { width: 100%; }
  .opt { padding: 14px; min-height: 48px; }
}
</style>

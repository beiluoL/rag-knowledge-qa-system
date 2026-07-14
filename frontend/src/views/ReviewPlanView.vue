<template>
  <div class="page-container plan-page">
    <!-- 头部 -->
    <header class="page-header">
      <div class="page-header__main">
        <div class="page-header__icon" aria-hidden="true">
          <el-icon><Calendar /></el-icon>
        </div>
        <div>
          <h1 class="page-title">个性化复习计划</h1>
          <p class="page-subtitle">基于知识库文档与你的学习目标，由 AI 生成按天递进的复习安排，循序渐进覆盖核心内容。</p>
        </div>
      </div>
    </header>

    <!-- 配置区 -->
    <section class="ui-card plan-config" aria-label="计划配置">
      <div class="plan-config__grid">
        <div class="plan-config__field plan-config__field--grow">
          <label class="field-label" for="kb-select">
            知识库 <span class="req" aria-hidden="true">*</span>
          </label>
          <el-tree-select
            id="kb-select"
            v-model="kbId"
            :data="kbTree"
            :props="{ label: 'name', children: 'children' }"
            value-key="id"
            node-key="id"
            placeholder="请选择知识库"
            clearable
            default-expand-all
            style="width: 100%"
          />
        </div>
        <div class="plan-config__field">
          <label class="field-label" for="days-input">总天数</label>
          <el-input-number
            id="days-input"
            v-model="days"
            :min="3"
            :max="60"
            controls-position="right"
            style="width: 100%"
          />
        </div>
        <div class="plan-config__field">
          <label class="field-label" for="minutes-input">每天时长(分钟)</label>
          <el-input-number
            id="minutes-input"
            v-model="dailyMinutes"
            :min="10"
            :max="240"
            :step="10"
            controls-position="right"
            style="width: 100%"
          />
        </div>
      </div>
      <div class="plan-config__field plan-config__field--full">
        <label class="field-label" for="goal-input">学习目标（可选）</label>
        <el-input
          id="goal-input"
          v-model="goal"
          type="textarea"
          :rows="2"
          placeholder="例如：两周内掌握 Spring Boot 核心，能独立完成后端接口开发"
        />
      </div>
      <div class="plan-config__actions">
        <el-button type="primary" :loading="loading" :icon="Calendar" @click="generate">生成复习计划</el-button>
      </div>
    </section>

    <!-- 加载中 -->
    <div v-if="loading" class="ui-loading plan-loading" role="status" aria-live="polite">
      <el-icon class="is-loading"><Loader2 /></el-icon>
      <span>正在为你规划复习路线，请稍候…</span>
    </div>

    <!-- 结果区 -->
    <section v-else-if="plan" class="plan-result">
      <div class="ui-card plan-summary">
        <div class="ps-item"><span class="ps-num">{{ plan.days }}</span><span class="ps-label">天</span></div>
        <div class="ps-item"><span class="ps-num">{{ plan.dailyMinutes }}</span><span class="ps-label">分钟/天</span></div>
        <div class="ps-item"><span class="ps-num">{{ plan.docCount }}</span><span class="ps-label">篇文档</span></div>
        <div class="ps-goal">
          <el-icon aria-hidden="true"><Target /></el-icon>
          <span>{{ plan.goal }}</span>
        </div>
      </div>

      <div class="timeline">
        <article v-for="d in plan.plan" :key="d.day" class="ui-card day-card">
          <div class="day-badge">第 {{ d.day }} 天</div>
          <div class="day-body">
            <div class="day-focus">{{ d.focus }}</div>
            <ul class="day-tasks">
              <li v-for="(t, i) in d.tasks" :key="i">{{ t }}</li>
            </ul>
            <div class="day-foot">
              <span class="day-min">
                <el-icon aria-hidden="true"><Timer /></el-icon> {{ d.minutes }} 分钟
              </span>
              <span v-if="d.topics && d.topics.length" class="day-topics">
                <el-icon aria-hidden="true"><Library /></el-icon> {{ d.topics.join('、') }}
              </span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <el-empty v-else description="选择知识库并设定目标，让 AI 为你定制复习路线" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Loader2,
  Calendar,
  Target,
  Timer,
  Library
} from 'lucide-vue-next'
import { getKbTree, type KbTreeNode } from '@/api/knowledgeBase'
import { generateReviewPlan, type ReviewPlan } from '@/api/learning'

const kbId = ref<number | null>(null)
const kbTree = ref<KbTreeNode[]>([])
const goal = ref('')
const days = ref(7)
const dailyMinutes = ref(30)
const loading = ref(false)
const plan = ref<ReviewPlan | null>(null)

async function loadKb() {
  try {
    const { data } = await getKbTree()
    kbTree.value = data || []
  } catch { /* ignore */ }
}

async function generate() {
  if (!kbId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  loading.value = true
  plan.value = null
  try {
    const { data } = await generateReviewPlan({
      knowledgeBaseId: kbId.value,
      goal: goal.value.trim() || undefined,
      days: days.value,
      dailyMinutes: dailyMinutes.value
    })
    plan.value = data
  } catch (e: any) {
    ElMessage.error('生成失败：' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

onMounted(loadKb)
</script>

<style scoped>
.plan-page {
  max-width: 920px;
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
.plan-config { padding: var(--space-xl) var(--space-2xl); }
.plan-config__grid { display: flex; gap: var(--space-lg); flex-wrap: wrap; align-items: flex-end; }
.plan-config__field { display: flex; flex-direction: column; gap: var(--space-sm); }
.plan-config__field--grow { flex: 1; min-width: 240px; }
.plan-config__field--full { margin-top: var(--space-lg); }
.field-label { font-size: 0.8125rem; font-weight: 600; color: var(--text-secondary); }
.req { color: var(--danger); }
.plan-config__actions { margin-top: var(--space-lg); }

/* ── 加载态 ── */
.plan-loading { border: 1px solid var(--border); border-radius: var(--radius-lg); }

/* ── 计划概览 ── */
.plan-summary {
  display: flex; align-items: center; gap: var(--space-2xl);
  padding: var(--space-lg) var(--space-xl);
  margin-bottom: var(--space-lg);
  flex-wrap: wrap;
}
.ps-item { display: flex; flex-direction: column; align-items: center; }
.ps-num { font-size: 1.375rem; font-weight: 800; color: var(--primary-600); line-height: 1.2; }
.ps-label { font-size: 0.8125rem; color: var(--text-muted); }
.ps-goal {
  flex: 1; min-width: 200px;
  display: inline-flex; align-items: center; gap: var(--space-sm);
  font-size: 0.875rem; color: var(--text-primary); font-weight: 600;
}
.ps-goal .el-icon { color: var(--primary-600); }

/* ── 时间线卡片 ── */
.timeline { display: flex; flex-direction: column; gap: var(--space-lg); }
.day-card {
  display: flex; gap: var(--space-lg);
  padding: var(--space-lg) var(--space-xl);
  border-left: 3px solid var(--primary-600);
}
.day-badge {
  flex-shrink: 0; align-self: flex-start;
  background: var(--brand-gradient); color: var(--text-inverse);
  font-size: 0.8125rem; font-weight: 700;
  padding: var(--space-xs) var(--space-md); border-radius: var(--radius-full);
}
.day-body { flex: 1; min-width: 0; }
.day-focus { font-size: 0.9375rem; font-weight: 700; color: var(--text-primary); margin-bottom: var(--space-sm); }
.day-tasks { padding-left: var(--space-lg); margin: 0 0 var(--space-sm); }
.day-tasks li { font-size: 0.84375rem; color: var(--text-secondary); line-height: 1.7; }
.day-foot { display: flex; gap: var(--space-lg); flex-wrap: wrap; font-size: 0.8125rem; color: var(--text-secondary); }
.day-min, .day-topics { display: inline-flex; align-items: center; gap: var(--space-xs); }
.day-min .el-icon { color: var(--primary-600); }
.day-topics .el-icon { color: var(--text-secondary); }

/* ── 响应式：手机端配置区与时间线卡片垂直堆叠 ── */
@media (max-width: 640px) {
  .plan-config { padding: var(--space-lg); }
  .plan-config__grid { flex-direction: column; align-items: stretch; }
  .plan-config__field { width: 100%; }
  .plan-config__actions .el-button { width: 100%; }

  .day-card { flex-direction: column; gap: var(--space-sm); }
  .day-badge { align-self: flex-start; }
}
</style>

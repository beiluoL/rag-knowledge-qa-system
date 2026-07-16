<template>
  <div class="eval-wrap">
    <header class="eval-head">
      <div>
        <h2 class="eval-title">RAG 效果评估</h2>
        <p class="eval-sub">基于问答记录（点赞/踩 + 检索引用）量化答案质量与检索质量</p>
      </div>
      <el-button :icon="RefreshCw" :loading="loading" @click="load">刷新数据</el-button>
    </header>

    <!-- 核心指标 -->
    <div class="metric-row">
      <div class="metric-card">
        <span class="m-label">满意度（点赞率）</span>
        <span class="m-num">{{ overview.satisfaction.satisfactionRate }}<i>%</i></span>
        <span class="m-sub">👍 {{ overview.satisfaction.likes }} · 👎 {{ overview.satisfaction.dislikes }}</span>
      </div>
      <div class="metric-card">
        <span class="m-label">检索命中率</span>
        <span class="m-num">{{ overview.retrieval.hitRate }}<i>%</i></span>
        <span class="m-sub">命中 {{ overview.retrieval.hitCount }} / 共 {{ overview.retrieval.totalAnswered }}</span>
      </div>
      <div class="metric-card">
        <span class="m-label">平均引用文档数</span>
        <span class="m-num">{{ overview.retrieval.avgReferencedDocs }}</span>
        <span class="m-sub">每条回答平均引用</span>
      </div>
      <div class="metric-card warn">
        <span class="m-label">检索不到的问题</span>
        <span class="m-num">{{ overview.retrieval.noHitCount }}</span>
        <span class="m-sub">需补充资料或优化</span>
      </div>
    </div>

    <div class="chart-row">
      <div class="chart-card">
        <h3 class="chart-title">答案满意度分布</h3>
        <div ref="pieRef" class="chart-box"></div>
      </div>
      <div class="chart-card">
        <h3 class="chart-title">被引用最多的文档（Top 10）</h3>
        <div ref="barRef" class="chart-box"></div>
      </div>
    </div>

    <div class="table-card">
      <h3 class="chart-title">检索不到的问题（未命中任何片段）</h3>
      <el-table :data="overview.noHitQuestions" stripe style="width: 100%" empty-text="暂无检索不到的问题 🎉">
        <el-table-column prop="question" label="用户提问" min-width="220" show-overflow-tooltip />
        <el-table-column prop="answer" label="系统回答" min-width="260" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ row.time ? formatTime(row.time) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="goChat(row.conversationId)">去补充</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { RefreshCw } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { getEvaluation } from '@/api/evaluation'

const router = useRouter()
const loading = ref(false)
const pieRef = ref<HTMLDivElement | null>(null)
const barRef = ref<HTMLDivElement | null>(null)
let pieChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null

const emptyOverview = {
  satisfaction: { likes: 0, dislikes: 0, totalAnswered: 0, rated: 0, satisfactionRate: 0 },
  retrieval: { totalAnswered: 0, hitCount: 0, noHitCount: 0, hitRate: 0, avgReferencedDocs: 0 },
  topDocuments: [],
  noHitQuestions: []
}
const overview = ref<any>(emptyOverview)

const PALETTE = ['#2563eb', '#f59e0b', '#8b5cf6', '#10b981', '#ef4444', '#0ea5e9']

async function load() {
  loading.value = true
  try {
    const r = await getEvaluation(10, 20)
    overview.value = (r.data?.data ?? r.data ?? emptyOverview) as any
    await nextTick()
    renderPie()
    renderBar()
  } catch {
    ElMessage.error('加载评估数据失败')
  } finally {
    loading.value = false
  }
}

function renderPie() {
  if (!pieRef.value) return
  const s = overview.value.satisfaction
  const unrated = Math.max(0, s.totalAnswered - s.rated)
  const data = [
    { name: '👍 点赞', value: s.likes },
    { name: '👎 踩', value: s.dislikes },
    { name: '未评价', value: unrated }
  ].filter(d => d.value > 0)
  pieChart = pieChart || echarts.init(pieRef.value)
  pieChart.setOption({
    color: ['#10b981', '#ef4444', '#cbd5e1'],
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle' },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '45%'],
      avoidLabelOverlap: true, label: { formatter: '{b}\n{c}' },
      data
    }]
  })
  pieChart.resize()
}

function renderBar() {
  if (!barRef.value) return
  const docs = overview.value.topDocuments || []
  const names = docs.map((d: any) => d.documentTitle || ('文档#' + d.documentId)).reverse()
  const counts = docs.map((d: any) => d.count).reverse()
  barChart = barChart || echarts.init(barRef.value)
  barChart.setOption({
    grid: { left: 8, right: 24, top: 10, bottom: 10, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } } },
    yAxis: { type: 'category', data: names, axisLabel: { width: 160, overflow: 'truncate' } },
    series: [{
      type: 'bar', data: counts, barWidth: '60%',
      itemStyle: { color: PALETTE[0], borderRadius: [0, 6, 6, 0] },
      label: { show: true, position: 'right' }
    }]
  })
  barChart.resize()
}

function goChat(conversationId: number) {
  if (conversationId) router.push('/chat/' + conversationId)
  else router.push('/chat')
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function onResize() {
  pieChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pieChart?.dispose()
  barChart?.dispose()
})
</script>

<style scoped>
.eval-wrap { max-width: 1100px; margin: 0 auto; }
.eval-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-xl); }
.eval-title { font-size: 22px; font-weight: 800; color: var(--text-primary); margin: 0; }
.eval-sub { color: var(--text-secondary); font-size: 13px; margin: 6px 0 0; }

.metric-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-md); margin-bottom: var(--space-xl); }
.metric-card {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: var(--space-lg); display: flex; flex-direction: column; gap: 4px;
}
.metric-card.warn { border-color: color-mix(in srgb, #f59e0b 40%, var(--border)); }
.m-label { font-size: 13px; color: var(--text-secondary); }
.m-num { font-size: 30px; font-weight: 800; color: var(--primary-600); line-height: 1.1; }
.m-num i { font-size: 16px; font-style: normal; margin-left: 2px; }
.metric-card.warn .m-num { color: #d97706; }
.m-sub { font-size: 12px; color: var(--text-muted); }

.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-md); margin-bottom: var(--space-xl); }
.chart-card, .table-card {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: var(--space-lg);
}
.chart-title { font-size: 15px; font-weight: 700; color: var(--text-primary); margin: 0 0 var(--space-md); }
.chart-box { width: 100%; height: 300px; }

@media (max-width: 900px) {
  .metric-row { grid-template-columns: repeat(2, 1fr); }
  .chart-row { grid-template-columns: 1fr; }
}
</style>

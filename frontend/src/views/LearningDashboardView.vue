<template>
  <div class="learn-dash page-container">
    <!-- 加载骨架 -->
    <div v-if="loading" class="dash-loading" aria-busy="true" aria-label="加载中">
      <div class="hero-skeleton ui-card">
        <div class="sk-avatar"></div>
        <div class="sk-lines">
          <div class="sk-line sk-w60"></div>
          <div class="sk-bar"></div>
          <div class="sk-line sk-w40"></div>
        </div>
      </div>
      <div class="ui-grid cols-4">
        <div v-for="n in 4" :key="n" class="ui-card sk-block"></div>
      </div>
    </div>

    <template v-else>
      <!-- 英雄区：等级 / 经验 / 统计指标 -->
      <section class="hero ui-card" aria-label="学习概览">
        <div class="hero-left">
          <div class="level-ring" :style="{ '--p': dashboard.levelProgress }">
            <div class="level-ring-inner">
              <span class="level-num">{{ dashboard.level }}</span>
              <span class="level-cap">Lv</span>
            </div>
          </div>
          <div class="hero-id">
            <div class="hero-title">
              <span class="brand-gradient-text">{{ dashboard.title }}</span>
              <el-tag v-if="dashboard.level >= 10" size="small" type="warning" effect="dark" class="hero-badge">
                <el-icon><Medal /></el-icon> 高阶
              </el-tag>
            </div>
            <div class="hero-sub">知识探索者 · 学习中心</div>
            <div class="xp-row">
              <div class="xp-bar">
                <div class="xp-fill" :style="{ width: (dashboard.levelProgress * 100) + '%' }"></div>
              </div>
              <span class="xp-text">还差 {{ dashboard.xpToNextLevel }} XP 升级</span>
            </div>
            <div class="xp-total">累计经验 {{ dashboard.xp }} XP</div>
          </div>
        </div>

        <div class="hero-stats">
          <div class="ui-stat-card">
            <div class="ui-stat-icon" :class="{ hot: dashboard.currentStreak > 0 }">
              <el-icon><Zap /></el-icon>
            </div>
            <div>
              <div class="ui-stat-value" :class="{ hot: dashboard.currentStreak > 0 }">{{ dashboard.currentStreak }}</div>
              <div class="ui-stat-label">连续学习（天）</div>
            </div>
          </div>
          <div class="ui-stat-card">
            <div class="ui-stat-icon"><el-icon><BarChart3 /></el-icon></div>
            <div>
              <div class="ui-stat-value">{{ dashboard.longestStreak }}</div>
              <div class="ui-stat-label">最长连续</div>
            </div>
          </div>
          <div class="ui-stat-card">
            <div class="ui-stat-icon"><el-icon><Library /></el-icon></div>
            <div>
              <div class="ui-stat-value">{{ dashboard.cardsStudied }}</div>
              <div class="ui-stat-label">已学卡片</div>
            </div>
          </div>
        </div>
      </section>

      <!-- 每日总结激励条 -->
      <div class="daily-banner" role="status">
        <el-icon class="daily-icon"><Wand2 /></el-icon>
        <span>{{ dailySummary }}</span>
      </div>

      <!-- 四种学习模式 -->
      <div class="section-head">
        <h3 class="ui-section-title">选择学习模式</h3>
      </div>
      <div class="ui-grid cols-4 mode-grid" role="list">
        <button
          v-for="m in modeMeta"
          :key="m.key"
          class="mode-card"
          type="button"
          role="listitem"
          :aria-label="`开始${m.name}：${m.desc}`"
          @click="goMode(m.key)"
        >
          <div class="mode-icon"><el-icon><component :is="m.icon" /></el-icon></div>
          <div class="mode-name">{{ m.name }}</div>
          <div class="mode-desc">{{ m.desc }}</div>
          <div class="mode-go">开始 <el-icon><ArrowRight /></el-icon></div>
        </button>
      </div>

      <!-- 学习工具 -->
      <div class="section-head">
        <h3 class="ui-section-title">学习工具</h3>
      </div>
      <div class="ui-grid tool-grid" role="list">
        <button class="mode-card" type="button" role="listitem" aria-label="浏览学习卡片库" @click="goPath('/learn/cards')">
          <div class="mode-icon"><el-icon><Layers /></el-icon></div>
          <div class="mode-name">学习卡片库</div>
          <div class="mode-desc">以列表 / 时间轴浏览全部卡片，点击查看完整内容</div>
          <div class="mode-go">进入 <el-icon><ArrowRight /></el-icon></div>
        </button>
        <button class="mode-card" type="button" role="listitem" aria-label="打开代码练习编辑器" @click="goPath('/learn/code')">
          <div class="mode-icon"><el-icon><Code2 /></el-icon></div>
          <div class="mode-name">代码练习</div>
          <div class="mode-desc">LeetCode 风格编辑器，编写代码并实时运行查看结果</div>
          <div class="mode-go">进入 <el-icon><ArrowRight /></el-icon></div>
        </button>
        <button class="mode-card" type="button" role="listitem" aria-label="进入知识库对话" @click="goPath('/chat')">
          <div class="mode-icon"><el-icon><MessageCircle /></el-icon></div>
          <div class="mode-name">知识库对话</div>
          <div class="mode-desc">基于已学知识库进行 AI 问答，边学边问巩固所学</div>
          <div class="mode-go">进入 <el-icon><ArrowRight /></el-icon></div>
        </button>
      </div>

      <!-- 今日任务 + 成就 -->
      <div class="bottom-grid">
        <section class="ui-card">
          <div class="panel-head">
            <h3 class="ui-section-title sm">今日任务</h3>
            <el-button size="small" :icon="RefreshCw" :loading="genLoading" @click="generate('daily')">生成今日任务</el-button>
          </div>
          <div v-if="dashboard.todayTasks.length === 0" class="ui-empty">
            <el-icon class="empty-icon"><Notebook /></el-icon>
            <p>还没有今日任务，点击右上角「生成今日任务」自动从知识库派生。</p>
          </div>
          <ul v-else class="task-list">
            <li v-for="t in dashboard.todayTasks" :key="t.id" class="task-item">
              <div class="task-main">
                <el-tag size="small" :type="modeTagType(t.mode)" effect="light" class="task-mode">{{ modeName(t.mode) }}</el-tag>
                <span class="task-title">{{ t.title }}</span>
              </div>
              <div class="task-progress">
                <div class="task-bar"><div class="task-fill" :style="{ width: progressPct(t) + '%' }"></div></div>
                <span class="task-count">{{ t.progressCount }}/{{ t.targetCount }}</span>
              </div>
              <el-button size="small" type="primary" plain @click="goTask(t)">去学习</el-button>
            </li>
          </ul>
        </section>

        <section class="ui-card">
          <div class="panel-head">
            <h3 class="ui-section-title sm">成就 & 称号</h3>
            <el-button size="small" text type="primary" @click="showAllAch = true">查看全部</el-button>
          </div>
          <div v-if="dashboard.recentAchievements.length === 0" class="ui-empty">
            <el-icon class="empty-icon"><Medal /></el-icon>
            <p>完成学习任务即可解锁成就与称号。</p>
          </div>
          <div v-else class="ach-grid">
            <div
              v-for="a in dashboard.recentAchievements"
              :key="a.code"
              class="ach-badge"
              :class="{ locked: !a.unlocked }"
            >
              <span class="ach-icon"><el-icon><Medal /></el-icon></span>
              <span class="ach-name">{{ a.name }}</span>
            </div>
          </div>
        </section>
      </div>

      <!-- 全部成就弹窗 -->
      <el-dialog v-model="showAllAch" title="全部成就" width="560px">
        <div class="ach-all-grid">
          <div v-for="a in allAchievements" :key="a.code" class="ach-all" :class="{ locked: !a.unlocked }">
            <span class="ach-icon"><el-icon><Medal /></el-icon></span>
            <div class="ach-all-text">
              <div class="ach-all-name">{{ a.name }}</div>
              <div class="ach-all-desc">{{ a.description }}</div>
            </div>
            <el-tag size="small" :type="a.unlocked ? 'success' : 'info'" effect="light">
              {{ a.unlocked ? '已解锁' : (a.threshold + ' ' + metricLabel(a.metric)) }}
            </el-tag>
          </div>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  RefreshCw,
  ArrowRight,
  Medal,
  Zap,
  BarChart3,
  Library,
  Notebook,
  Wand2,
  FileText,
  Newspaper,
  Megaphone,
  Trophy,
  Code2,
  Layers,
  MessageCircle
} from 'lucide-vue-next'
import { getDashboard, generateTasks, getAchievements, type Dashboard, type Achievement, type StudyTask } from '@/api/learning'

const router = useRouter()

const loading = ref(true)
const dashboard = ref<Dashboard>({
  xp: 0, level: 1, title: '萌新学员', levelProgress: 0, xpToNextLevel: 0,
  currentStreak: 0, longestStreak: 0, cardsStudied: 0,
  todayTasks: [], recentAchievements: []
})
const allAchievements = ref<Achievement[]>([])
const showAllAch = ref(false)
const genLoading = ref(false)

const modeMeta = [
  { key: 'list', name: '列表学习', icon: FileText, desc: '按条目顺序通读，适合系统性复习' },
  { key: 'flashcard', name: '闪卡记忆', icon: Newspaper, desc: '正面问题、背面答案，点击翻转' },
  { key: 'swipe', name: '刷卡训练', icon: Megaphone, desc: '认识 / 不认识左右滑，高效过卡' },
  { key: 'challenge', name: '闯关挑战', icon: Trophy, desc: '答题闯关，检验掌握程度' }
]

const modeTagMap: Record<string, 'primary' | 'info' | 'warning' | 'success'> = {
  list: 'primary',
  flashcard: 'info',
  swipe: 'warning',
  challenge: 'success'
}

function modeName(mode: string) {
  return modeMeta.find(m => m.key === mode)?.name || mode
}
function modeTagType(mode: string): 'primary' | 'info' | 'warning' | 'success' {
  return modeTagMap[mode] || 'primary'
}
function progressPct(t: StudyTask) {
  if (!t.targetCount) return 0
  return Math.min(100, Math.round((t.progressCount / t.targetCount) * 100))
}
function metricLabel(metric?: string) {
  return { xp: '经验', cards: '卡片', streak: '连续天数', level: '等级' }[metric || ''] || ''
}

const dailySummary = computed(() => {
  const d = dashboard.value
  if (d.currentStreak >= 7) return `太棒了！已连续学习 ${d.currentStreak} 天，保持这个节奏，距离「一代宗师」更近一步。`
  if (d.currentStreak >= 3) return `连续学习 ${d.currentStreak} 天，养成习惯最关键，今天也来学一点吧。`
  if (d.cardsStudied === 0) return `欢迎来到学习中心！挑选一个模式开始你的第一段知识旅程。`
  return `今日已学 ${d.cardsStudied} 张卡片，再坚持一下就能点亮连续学习徽章。`
})

function goMode(mode: string) {
  router.push(`/learn/${mode}`)
}
function goPath(p: string) {
  router.push(p)
}
function goTask(t: StudyTask) {
  const q: Record<string, string> = {}
  if (t.knowledgeBaseId) q.kb = String(t.knowledgeBaseId)
  router.push({ path: `/learn/${t.mode}`, query: q })
}

async function load() {
  try {
    const { data } = await getDashboard()
    dashboard.value = data
  } catch (e) {
    console.error('加载仪表盘失败', e)
  } finally {
    loading.value = false
  }
}

async function generate(cycle: string) {
  genLoading.value = true
  try {
    await generateTasks(cycle)
    ElMessage.success('已生成今日任务')
    await load()
  } catch (e: any) {
    ElMessage.error('生成失败：' + (e.response?.data?.message || e.message))
  } finally {
    genLoading.value = false
  }
}

async function loadAllAch() {
  try {
    const { data } = await getAchievements()
    allAchievements.value = data
  } catch (e) {
    console.error('加载成就失败', e)
  }
}

onMounted(() => {
  load()
  loadAllAch()
})
</script>

<style scoped>
.learn-dash { padding-bottom: var(--space-4xl); }

/* ── 加载骨架 ── */
.dash-loading { display: flex; flex-direction: column; gap: var(--space-xl); }
.hero-skeleton { display: flex; gap: var(--space-xl); align-items: center; }
.sk-avatar { width: 84px; height: 84px; border-radius: 50%; background: var(--surface-3); flex-shrink: 0; }
.sk-lines { flex: 1; display: flex; flex-direction: column; gap: var(--space-md); }
.sk-line { height: 14px; border-radius: var(--radius-sm); background: var(--surface-3); }
.sk-w60 { width: 60%; }
.sk-w40 { width: 40%; }
.sk-bar { height: 8px; width: 220px; border-radius: var(--radius-full); background: var(--surface-3); }
.sk-block { height: 96px; }

/* ── 英雄区 ── */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2xl);
  flex-wrap: wrap;
  margin-bottom: var(--space-xl);
}
.hero-left { display: flex; align-items: center; gap: var(--space-xl); }
.level-ring {
  --p: 0;
  width: 84px; height: 84px;
  border-radius: 50%;
  background: conic-gradient(var(--brand-1) calc(var(--p) * 360deg), var(--surface-3) 0deg);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.level-ring-inner {
  width: 70px; height: 70px;
  border-radius: 50%;
  background: var(--surface);
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  box-shadow: inset 0 2px 6px rgba(15, 23, 42, 0.05);
}
.level-num { font-size: var(--text-2xl); font-weight: 800; color: var(--primary-600); line-height: 1; }
.level-cap { font-size: 11px; color: var(--text-muted); margin-top: 2px; }
.hero-title { font-size: 20px; font-weight: 800; display: flex; align-items: center; gap: var(--space-sm); color: var(--text-primary); }
.hero-badge { display: inline-flex; align-items: center; gap: 4px; }
.hero-sub { font-size: var(--text-sm); color: var(--text-secondary); margin: 2px 0 10px; }
.xp-row { display: flex; align-items: center; gap: var(--space-md); }
.xp-bar { width: 220px; max-width: 50vw; height: 8px; background: var(--surface-3); border-radius: var(--radius-full); overflow: hidden; }
.xp-fill {
  height: 100%;
  background: var(--brand-gradient);
  border-radius: var(--radius-full);
  transition: width 0.8s var(--ease-out);
}
.xp-text { font-size: var(--text-xs); color: var(--text-secondary); white-space: nowrap; }
.xp-total { font-size: var(--text-xs); color: var(--text-muted); margin-top: 6px; }

.hero-stats { display: flex; gap: var(--space-md); flex: 1; justify-content: flex-end; flex-wrap: wrap; }
.hero-stats .ui-stat-icon { background: var(--primary-50); color: var(--primary-600); }
.ui-stat-icon.hot { background: var(--warning-light); color: var(--warning); }
.ui-stat-value.hot { color: var(--warning); }

/* ── 每日总结 ── */
.daily-banner {
  display: flex; align-items: center; gap: var(--space-md);
  background: var(--primary-50);
  border: 1px solid var(--primary-100);
  border-radius: var(--radius-md);
  padding: var(--space-md) var(--space-lg);
  font-size: 13.5px; color: var(--text-secondary);
  margin-bottom: var(--space-2xl);
}
.daily-icon { color: var(--primary-600); font-size: 18px; flex-shrink: 0; }

/* ── 区块标题 ── */
.section-head { margin-bottom: var(--space-lg); }

/* ── 模式网格 ── */
.mode-card {
  text-align: left;
  font: inherit;
  cursor: pointer;
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--space-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  transition: box-shadow var(--duration-fast), transform var(--duration-fast), border-color var(--duration-fast);
}
.mode-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); border-color: var(--primary-200); }
.mode-card:active { transform: translateY(0); }
.mode-icon {
  width: 52px; height: 52px; border-radius: var(--radius-md);
  background: var(--primary-50); color: var(--primary-600);
  display: flex; align-items: center; justify-content: center;
  font-size: 24px; margin-bottom: var(--space-sm);
}
.mode-name { font-size: var(--text-base); font-weight: 700; color: var(--text-primary); }
.mode-desc { font-size: var(--text-xs); color: var(--text-secondary); line-height: 1.5; min-height: 36px; }
.mode-go { font-size: var(--text-sm); font-weight: 600; color: var(--primary-600); display: flex; align-items: center; gap: 4px; margin-top: auto; }

/* ── 底部网格 ── */
.bottom-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: var(--space-xl); margin-top: var(--space-2xl); }
.tool-grid { grid-template-columns: repeat(3, 1fr); }
@media (max-width: 860px) { .tool-grid { grid-template-columns: 1fr; } }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-lg); gap: var(--space-md); }
.ui-section-title.sm { margin-bottom: 0; font-size: var(--text-base); }

/* 任务 */
.empty-icon { font-size: 40px; color: var(--text-muted); display: block; margin-bottom: var(--space-md); }
.ui-empty p { color: var(--text-muted); max-width: 360px; margin: 0 auto; line-height: 1.6; }
.task-list { list-style: none; }
.task-item { display: flex; align-items: center; gap: var(--space-md); padding: var(--space-md) 0; border-top: 1px solid var(--divider); }
.task-main { flex: 1; min-width: 0; display: flex; align-items: center; gap: var(--space-sm); }
.task-mode { flex-shrink: 0; }
.task-title { font-size: 14px; color: var(--text-primary); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-progress { display: flex; align-items: center; gap: var(--space-sm); width: 160px; flex-shrink: 0; }
.task-bar { flex: 1; height: 6px; background: var(--surface-3); border-radius: var(--radius-full); overflow: hidden; }
.task-fill { height: 100%; background: var(--brand-gradient); border-radius: var(--radius-full); transition: width 0.5s; }
.task-count { font-size: var(--text-xs); color: var(--text-muted); white-space: nowrap; }

/* 成就 */
.ach-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--space-md); }
.ach-badge {
  display: flex; align-items: center; gap: var(--space-sm);
  background: var(--primary-50);
  border: 1px solid var(--primary-100);
  border-radius: var(--radius-md); padding: var(--space-md) var(--space-lg);
}
.ach-badge.locked { filter: grayscale(1); opacity: 0.5; }
.ach-icon { color: var(--primary-600); font-size: 20px; display: inline-flex; }
.ach-name { font-size: var(--text-sm); font-weight: 600; color: var(--text-primary); }

/* 全部成就弹窗 */
.ach-all-grid { display: flex; flex-direction: column; gap: var(--space-md); }
.ach-all { display: flex; align-items: center; gap: var(--space-md); padding: var(--space-md) var(--space-lg); border: 1px solid var(--border); border-radius: var(--radius-md); }
.ach-all.locked { filter: grayscale(1); opacity: 0.55; }
.ach-all-text { flex: 1; min-width: 0; }
.ach-all-name { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.ach-all-desc { font-size: var(--text-xs); color: var(--text-secondary); margin-top: 2px; }

/* ── 响应式 ── */
@media (max-width: 860px) {
  .hero { flex-direction: column; align-items: flex-start; gap: var(--space-lg); }
  .hero-stats { width: 100%; justify-content: flex-start; }
  .bottom-grid { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .hero-stats { grid-template-columns: 1fr; display: grid; }
  .hero-stats .ui-stat-card { width: 100%; }
}
</style>

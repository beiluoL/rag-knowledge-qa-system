<template>
  <div class="learn-mode">
    <!-- 未开始：选择知识库 -->
    <div v-if="!started">
      <!-- 准备卡片加载 -->
      <div v-if="startLoading" class="ui-loading" aria-busy="true">
        <el-icon class="is-loading"><Loader2 /></el-icon> 正在准备学习卡片…
      </div>
      <!-- 知识库加载 -->
      <div v-else-if="kbLoading" class="ui-loading" aria-busy="true">
        <el-icon class="is-loading"><Loader2 /></el-icon> 正在加载知识库…
      </div>
      <!-- 选择面板 -->
      <div v-else class="start-box ui-card">
        <div class="start-icon"><el-icon><component :is="meta.icon" /></el-icon></div>
        <h3 class="start-title">开始「{{ meta.name }}」</h3>
        <p class="start-tip">{{ meta.desc }}</p>
        <el-select
          v-model="selectedKbId"
          placeholder="选择要学习哪个知识库"
          aria-label="选择知识库"
          style="width: 100%; max-width: 360px"
          :loading="kbLoading"
        >
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-button type="primary" size="large" class="start-btn" :disabled="!selectedKbId" :loading="startLoading" @click="startSession">
          开始学习
        </el-button>
        <p v-if="knowledgeBases.length === 0" class="start-note">该知识库暂无文档，请先在后台上传内容。</p>
      </div>
    </div>

    <!-- 学习中 -->
    <template v-else>
      <!-- 顶部：返回 + 模式信息 -->
      <header class="mode-top">
        <el-button :icon="ArrowLeft" @click="router.push('/learn')">返回学习中心</el-button>
        <div class="mode-info">
          <span class="mode-info-icon"><el-icon><component :is="meta.icon" /></el-icon></span>
          <div>
            <div class="mode-info-name">{{ meta.name }}</div>
            <div class="mode-info-desc">{{ meta.desc }}</div>
          </div>
        </div>
        <div class="mode-progress">
          <span class="mp-text">已学 {{ doneSet.size }}/{{ cards.length }}</span>
          <div class="mp-bar"><div class="mp-fill" :style="{ width: progressPct + '%' }"></div></div>
        </div>
      </header>

      <!-- 卡片加载中 -->
      <div v-if="startLoading" class="ui-loading" aria-busy="true">
        <el-icon class="is-loading"><Loader2 /></el-icon> 正在准备学习卡片…
      </div>

      <!-- 空状态：无卡片 -->
      <div v-else-if="cards.length === 0" class="ui-empty">
        <el-icon class="empty-icon"><Trash2 /></el-icon>
        <p>该知识库暂无可学习的内容，请先上传文档或更换知识库。</p>
        <el-button type="primary" @click="restart">重新选择</el-button>
      </div>

      <!-- 实际内容 -->
      <div v-else class="session">
        <!-- 列表模式 -->
        <div v-if="mode === 'list'" class="list-mode">
          <div v-for="(c, i) in cards" :key="c.id" class="list-card" :class="{ done: doneSet.has(i) }">
            <div class="list-card-head" @click="toggleExpand(i)" role="button" :aria-expanded="expanded.has(i)" :aria-label="`卡片 ${i + 1}：${c.front}`">
              <span class="list-idx">{{ i + 1 }}</span>
              <span class="list-front">{{ c.front }}</span>
              <span class="list-doc">{{ c.documentTitle }}</span>
              <el-icon class="list-chev" :class="{ open: expanded.has(i) }"><ArrowDown /></el-icon>
            </div>
            <div v-if="expanded.has(i)" class="list-back">{{ c.back }}</div>
            <div class="list-foot">
              <el-button v-if="!doneSet.has(i)" size="small" type="primary" plain @click="markDone(i)">
                <el-icon><Check /></el-icon> 已掌握
              </el-button>
              <el-tag v-else type="success" size="small" effect="light">
                <el-icon><Check /></el-icon> 已完成
              </el-tag>
            </div>
          </div>
          <div class="list-done-bar">
            <el-button type="success" :disabled="doneSet.size === 0" @click="finishSession">完成本组学习</el-button>
          </div>
        </div>

        <!-- 闪卡模式 -->
        <div v-else-if="mode === 'flashcard'" class="flash-mode">
          <div class="flash-card" :class="{ flipped }" @click="flipped = !flipped" role="button" :aria-label="flipped ? '查看答案，点击返回正面' : '查看问题，点击翻面看答案'">
            <div class="flash-face flash-front">
              <span class="flash-tag">问题</span>
              <div class="flash-text">{{ current.front }}</div>
              <div class="flash-doc">{{ current.documentTitle }}</div>
              <div class="flash-hint">点击卡片翻面</div>
            </div>
            <div class="flash-face flash-back">
              <span class="flash-tag back">答案</span>
              <div class="flash-text">{{ current.back }}</div>
              <div class="flash-hint">点击返回正面</div>
            </div>
          </div>
          <div class="flash-actions">
            <el-button size="large" @click="prevCard" :disabled="currentIndex === 0">
              <el-icon><ArrowLeft /></el-icon> 上一张
            </el-button>
            <el-button size="large" type="primary" @click="markDoneAndNext">
              下一张 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 刷卡模式 -->
        <div v-else-if="mode === 'swipe'" class="swipe-mode">
          <div class="swipe-card" :class="swipeClass" role="group" aria-label="刷卡训练：认识或不认识">
            <div class="swipe-front">{{ current.front }}</div>
            <div class="swipe-doc">{{ current.documentTitle }}</div>
            <div class="swipe-watermark" :class="swipeWatermark">{{ swipeWatermark === 'right' ? '认识' : '不认识' }}</div>
          </div>
          <div class="swipe-actions">
            <el-button size="large" type="danger" plain :disabled="animating" @click="swipe('left')">
              <el-icon><X /></el-icon> 不认识
            </el-button>
            <el-button size="large" type="success" plain :disabled="animating" @click="swipe('right')">
              <el-icon><Check /></el-icon> 认识
            </el-button>
          </div>
        </div>

        <!-- 闯关模式 -->
        <div v-else-if="mode === 'challenge'" class="challenge-mode">
          <div class="challenge-card">
            <div class="challenge-q">{{ current.front }}</div>
            <div class="challenge-doc">{{ current.documentTitle }}</div>
            <div v-if="!showAnswer" class="challenge-reveal">
              <el-button type="primary" @click="showAnswer = true">显示答案</el-button>
            </div>
            <div v-else class="challenge-answer">
              <div class="challenge-a">{{ current.back }}</div>
              <div class="challenge-judge">
                <el-button type="success" @click="answer(true)">
                  <el-icon><Check /></el-icon> 我答对了
                </el-button>
                <el-button type="danger" @click="answer(false)">
                  <el-icon><Close /></el-icon> 我答错了
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 结果弹窗 -->
    <el-dialog v-model="sessionDone" :show-close="false" width="440px" align-center>
      <div class="result">
        <div v-if="result.leveledUp" class="level-up-burst result-burst">
          <el-icon class="burst-icon"><Trophy /></el-icon> 升级啦！
        </div>
        <div class="result-xp">+{{ result.xpGained }} XP</div>
        <div class="result-level">
          当前等级 <b>Lv.{{ result.level }}</b> · <span class="brand-gradient-text">{{ result.title }}</span>
        </div>
        <div class="result-row"><el-icon><Zap /></el-icon> 连续学习 {{ result.streak }} 天</div>
        <div v-if="mode === 'challenge'" class="result-row">
          <el-icon><Target /></el-icon> 闯关正确率 {{ correctCount }}/{{ doneSet.size }}
        </div>
        <div v-if="result.unlocked && result.unlocked.length" class="result-unlocks">
          <div class="result-unlock-title"><el-icon><Medal /></el-icon> 新解锁成就</div>
          <div v-for="u in result.unlocked" :key="u.code" class="result-unlock">
            <el-icon><Medal /></el-icon> {{ u.name }}
          </div>
        </div>
        <div class="result-actions">
          <el-button @click="router.push('/learn')">返回中心</el-button>
          <el-button type="primary" @click="restart">再来一组</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowDown,
  ArrowRight,
  Check,
  X,
  Trophy,
  Zap,
  Medal,
  Target,
  Loader2,
  Trash2,
  FileText,
  Newspaper,
  Megaphone
} from 'lucide-vue-next'
import { getStudyCards, completeStudy, type StudyCard } from '@/api/learning'
import { listKnowledgeBases } from '@/api/knowledgeBase'

const route = useRoute()
const router = useRouter()
const mode = (route.params.mode as string) || 'list'

const modeMap: Record<string, { icon: any; name: string; desc: string }> = {
  list: { icon: FileText, name: '列表学习', desc: '按条目顺序通读，适合系统性复习' },
  flashcard: { icon: Newspaper, name: '闪卡记忆', desc: '正面问题、背面答案，点击翻转' },
  swipe: { icon: Megaphone, name: '刷卡训练', desc: '认识 / 不认识左右滑，高效过卡' },
  challenge: { icon: Trophy, name: '闯关挑战', desc: '答题闯关，检验掌握程度' }
}
const meta = modeMap[mode] || modeMap.list

// 知识库
const knowledgeBases = ref<Array<{ id: number; name: string }>>([])
const kbLoading = ref(false)
const selectedKbId = ref<number | null>(null)

// 会话状态
const started = ref(false)
const startLoading = ref(false)
const cards = ref<StudyCard[]>([])
const currentIndex = ref(0)
const doneSet = ref<Set<number>>(new Set())
const expanded = ref<Set<number>>(new Set())
const flipped = ref(false)
const showAnswer = ref(false)
const correctCount = ref(0)
const sessionDone = ref(false)
const animating = ref(false)
const swipeClass = ref('')
const swipeWatermark = ref('')

const result = ref<{ xpGained: number; totalXp: number; level: number; title: string; leveledUp: boolean; streak: number; unlocked: Array<{ code: string; name: string; icon?: string }> }>({
  xpGained: 0, totalXp: 0, level: 1, title: '', leveledUp: false, streak: 0, unlocked: []
})

const current = computed(() => cards.value[currentIndex.value] || { id: 0, documentId: 0, documentTitle: '', front: '', back: '' })
const progressPct = computed(() => cards.value.length ? Math.round((doneSet.value.size / cards.value.length) * 100) : 0)

async function loadKbs() {
  kbLoading.value = true
  try {
    const { data } = await listKnowledgeBases()
    knowledgeBases.value = data || []
  } finally {
    kbLoading.value = false
  }
}

async function startSession() {
  if (!selectedKbId.value) return
  startLoading.value = true
  try {
    const { data } = await getStudyCards(selectedKbId.value, mode)
    cards.value = data || []
    currentIndex.value = 0
    doneSet.value = new Set()
    expanded.value = new Set()
    flipped.value = false
    showAnswer.value = false
    correctCount.value = 0
    started.value = true
    if (cards.value.length === 0) {
      ElMessage.warning('该知识库暂无可学习的内容，请先上传文档')
    }
  } catch (e: any) {
    ElMessage.error('加载卡片失败：' + (e.response?.data?.message || e.message))
  } finally {
    startLoading.value = false
  }
}

function toggleExpand(i: number) {
  const s = new Set(expanded.value)
  s.has(i) ? s.delete(i) : s.add(i)
  expanded.value = s
}

// 列表模式：标记单张完成
function markDone(i: number) {
  const s = new Set(doneSet.value)
  s.add(i)
  doneSet.value = s
}

// 单卡模式：标记当前完成并前进
function markDoneAndNext() {
  markCurrent()
  next()
}
function markCurrent() {
  const s = new Set(doneSet.value)
  s.add(currentIndex.value)
  doneSet.value = s
}
function next() {
  if (currentIndex.value < cards.value.length - 1) {
    currentIndex.value++
    flipped.value = false
    showAnswer.value = false
  } else {
    finishSession()
  }
}
function prevCard() {
  if (currentIndex.value > 0) {
    currentIndex.value--
    flipped.value = false
  }
}

// 刷卡模式
function swipe(dir: 'left' | 'right') {
  if (animating.value) return
  animating.value = true
  swipeWatermark.value = dir
  swipeClass.value = dir === 'right' ? 'swipe-out-right' : 'swipe-out-left'
  markCurrent()
  setTimeout(() => {
    swipeClass.value = ''
    swipeWatermark.value = ''
    animating.value = false
    if (currentIndex.value < cards.value.length - 1) {
      currentIndex.value++
    } else {
      finishSession()
    }
  }, 280)
}

// 闯关模式
function answer(correct: boolean) {
  if (correct) correctCount.value++
  markCurrent()
  next()
}

async function finishSession() {
  if (doneSet.value.size === 0) {
    ElMessage.warning('请先学习至少一张卡片')
    return
  }
  try {
    const { data } = await completeStudy({
      cards: doneSet.value.size,
      knowledgeBaseId: selectedKbId.value ?? undefined,
      mode
    })
    result.value = {
      xpGained: data.xpGained ?? 0,
      totalXp: data.totalXp ?? 0,
      level: data.level ?? 1,
      title: data.title ?? '',
      leveledUp: !!data.leveledUp,
      streak: data.streak ?? 0,
      unlocked: data.unlocked ?? []
    }
    // 成就解锁通知
    const unlocked = data.unlocked as any[] | undefined
    if (unlocked && unlocked.length > 0) {
      for (const a of unlocked) {
        ElMessage.success(`🏆 成就解锁：${a.name || a}`)
      }
    }
  } catch (e: any) {
    ElMessage.error('提交学习记录失败：' + (e.response?.data?.message || e.message))
    return
  }
  sessionDone.value = true
}

function restart() {
  sessionDone.value = false
  startSession()
}

onMounted(async () => {
  await loadKbs()
  const qKb = route.query.kb
  if (qKb) selectedKbId.value = Number(qKb)
})
</script>

<style scoped>
.learn-mode { max-width: 760px; margin: 0 auto; padding-bottom: 40px; }
.mode-top { display: flex; align-items: center; gap: var(--space-lg); margin-bottom: var(--space-2xl); flex-wrap: wrap; }
.mode-info { display: flex; align-items: center; gap: var(--space-md); }
.mode-info-icon { width: 44px; height: 44px; border-radius: var(--radius-md); background: var(--primary-50); color: var(--primary-600); display: flex; align-items: center; justify-content: center; font-size: 22px; flex-shrink: 0; }
.mode-info-name { font-size: var(--text-md); font-weight: 700; color: var(--text-primary); }
.mode-info-desc { font-size: var(--text-xs); color: var(--text-secondary); }
.mode-progress { margin-left: auto; display: flex; align-items: center; gap: var(--space-md); }
.mp-text { font-size: var(--text-sm); color: var(--text-secondary); white-space: nowrap; }
.mp-bar { width: 140px; height: 8px; background: var(--surface-3); border-radius: var(--radius-full); overflow: hidden; }
.mp-fill { height: 100%; background: var(--brand-gradient); border-radius: var(--radius-full); transition: width 0.4s; }

/* 开始页 */
.start-box { display: flex; flex-direction: column; align-items: center; gap: var(--space-lg); padding: var(--space-5xl) var(--space-2xl); text-align: center; }
.start-icon { width: 72px; height: 72px; border-radius: var(--radius-lg); background: var(--primary-50); color: var(--primary-600); display: flex; align-items: center; justify-content: center; font-size: 36px; }
.start-title { margin: 0; font-size: 20px; color: var(--text-primary); }
.start-tip { margin: 0; color: var(--text-secondary); font-size: var(--text-sm); max-width: 360px; line-height: 1.6; }
.start-btn { min-width: 160px; }
.start-note { color: var(--warning); font-size: var(--text-xs); margin: 0; }

/* 空状态 */
.empty-icon { font-size: 40px; color: var(--text-muted); display: block; margin-bottom: var(--space-md); }
.ui-empty p { color: var(--text-muted); max-width: 360px; margin: 0 auto var(--space-md); line-height: 1.6; }

/* 列表模式 */
.list-mode { display: flex; flex-direction: column; gap: var(--space-md); }
.list-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; transition: border-color var(--duration-fast), box-shadow var(--duration-fast); }
.list-card:hover { box-shadow: var(--shadow-sm); }
.list-card.done { opacity: 0.7; border-color: var(--success-light); }
.list-card-head { display: flex; align-items: center; gap: var(--space-md); padding: var(--space-lg) var(--space-xl); cursor: pointer; }
.list-idx { width: 24px; height: 24px; flex-shrink: 0; border-radius: 50%; background: var(--brand-gradient); color: var(--text-inverse); font-size: var(--text-xs); font-weight: 700; display: flex; align-items: center; justify-content: center; }
.list-front { flex: 1; font-size: 14px; color: var(--text-primary); }
.list-doc { font-size: 11px; color: var(--text-muted); max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.list-chev { color: var(--text-muted); transition: transform var(--duration-fast); }
.list-chev.open { transform: rotate(180deg); }
.list-back { padding: 0 var(--space-xl) var(--space-lg) 50px; font-size: var(--text-sm); color: var(--text-secondary); line-height: 1.6; background: var(--surface-2); }
.list-foot { padding: var(--space-md) var(--space-xl); border-top: 1px solid var(--divider); display: flex; justify-content: flex-end; gap: var(--space-sm); }
.list-done-bar { display: flex; justify-content: center; margin-top: var(--space-xl); }

/* 闪卡模式 */
.flash-mode { display: flex; flex-direction: column; align-items: center; gap: var(--space-xl); }
.flash-card {
  width: 100%; height: 300px; perspective: 1200px; cursor: pointer;
}
.flash-face {
  position: absolute; width: 100%; height: 100%;
  backface-visibility: hidden; -webkit-backface-visibility: hidden;
  border-radius: var(--radius-lg); padding: var(--space-2xl);
  display: flex; flex-direction: column; justify-content: center;
  box-shadow: var(--shadow-md); transition: transform 0.5s; border: 1px solid var(--border);
}
.flash-front { background: linear-gradient(135deg, var(--primary-50), var(--primary-100)); }
.flash-back { background: var(--surface-2); transform: rotateY(180deg); }
.flash-card.flipped .flash-front { transform: rotateY(180deg); }
.flash-card.flipped .flash-back { transform: rotateY(360deg); }
.flash-tag { font-size: var(--text-xs); font-weight: 700; color: var(--primary-600); align-self: flex-start; }
.flash-tag.back { color: var(--primary-500); }
.flash-text { font-size: var(--text-lg); line-height: 1.7; color: var(--text-primary); margin: var(--space-md) 0; flex: 1; display: flex; align-items: center; }
.flash-doc { font-size: var(--text-xs); color: var(--text-muted); }
.flash-hint { font-size: 11px; color: var(--primary-300); text-align: center; }
.flash-actions { display: flex; gap: var(--space-md); }

/* 刷卡模式 */
.swipe-mode { display: flex; flex-direction: column; align-items: center; gap: var(--space-xl); }
.swipe-card {
  width: 100%; height: 300px; border-radius: var(--radius-lg);
  background: var(--warning-light); border: 1px solid var(--warning); box-shadow: var(--shadow-md);
  padding: var(--space-2xl); display: flex; flex-direction: column; justify-content: center;
  position: relative; overflow: hidden; transition: transform 0.28s ease, opacity 0.28s ease;
}
.swipe-front { font-size: var(--text-lg); line-height: 1.7; color: var(--text-primary); }
.swipe-doc { font-size: var(--text-xs); color: var(--text-muted); margin-top: var(--space-md); }
.swipe-watermark {
  position: absolute; top: var(--space-xl); font-size: 28px; font-weight: 800; padding: 4px 14px;
  border-radius: var(--radius-md); opacity: 0;
}
.swipe-watermark.right { right: var(--space-xl); color: var(--success); border: 3px solid var(--success); }
.swipe-watermark.left { left: var(--space-xl); color: var(--danger); border: 3px solid var(--danger); transform: rotate(12deg); }
.swipe-card.swipe-out-right { transform: translateX(120%) rotate(12deg); opacity: 0; }
.swipe-card.swipe-out-left { transform: translateX(-120%) rotate(-12deg); opacity: 0; }
.swipe-actions { display: flex; gap: var(--space-lg); }

/* 闯关模式 */
.challenge-mode { display: flex; justify-content: center; }
.challenge-card {
  width: 100%; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: var(--space-2xl); box-shadow: var(--shadow-md);
}
.challenge-q { font-size: 19px; font-weight: 600; color: var(--text-primary); line-height: 1.6; }
.challenge-doc { font-size: var(--text-xs); color: var(--text-muted); margin: var(--space-sm) 0 var(--space-lg); }
.challenge-reveal { display: flex; justify-content: center; }
.challenge-answer { animation: bodyIn 0.25s ease-out; }
.challenge-a { font-size: var(--text-base); color: var(--text-secondary); line-height: 1.7; background: var(--primary-50); border-left: 3px solid var(--primary-500); padding: var(--space-md) var(--space-lg); border-radius: var(--radius-sm); margin-bottom: var(--space-lg); }
.challenge-judge { display: flex; gap: var(--space-md); justify-content: center; }
@keyframes bodyIn { from { opacity: 0; } to { opacity: 1; } }

/* 结果弹窗 */
.result { text-align: center; padding: var(--space-sm); }
.result-burst { font-size: var(--text-lg); font-weight: 800; color: var(--warning); margin-bottom: var(--space-sm); display: flex; align-items: center; justify-content: center; gap: var(--space-xs); }
.burst-icon { font-size: 22px; }
.result-xp { font-size: var(--text-3xl); font-weight: 800; background: var(--brand-gradient); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }
.result-level { font-size: var(--text-base); color: var(--text-primary); margin: var(--space-xs) 0; }
.result-row { font-size: var(--text-sm); color: var(--text-secondary); margin: var(--space-xs) 0; display: flex; align-items: center; justify-content: center; gap: 6px; }
.result-unlocks { margin: var(--space-lg) 0; padding: var(--space-md); background: var(--warning-light); border-radius: var(--radius-md); }
.result-unlock-title { font-size: var(--text-sm); font-weight: 700; color: var(--warning); margin-bottom: var(--space-xs); display: flex; align-items: center; justify-content: center; gap: 6px; }
.result-unlock { font-size: var(--text-sm); color: var(--text-primary); display: flex; align-items: center; justify-content: center; gap: 6px; }
.result-actions { display: flex; gap: var(--space-md); justify-content: center; margin-top: var(--space-xl); }

/* 加载旋转 */
.is-loading { animation: rotating 1.2s linear infinite; }
@keyframes rotating { from { transform: rotate(0); } to { transform: rotate(360deg); } }

/* ── 响应式 ── */
@media (max-width: 480px) {
  .flash-card, .swipe-card { height: 260px; }
  .mode-progress { margin-left: 0; width: 100%; }
  .mode-top { gap: var(--space-md); }
  .list-doc { display: none; }
}
</style>

<template>
  <Teleport to="body">
    <!-- ========== 桌面宠物主体 ========== -->
    <div
      ref="petRef"
      class="desktop-pet"
      :style="petStyle"
      :class="{ dragging: isDragging, walking: isWalking }"
      @mousedown.prevent="onDragStart"
      @dblclick.prevent="openGame"
      @click.stop="onClick"
      role="button"
      aria-label="学习伙伴，点击互动"
      tabindex="0"
    >
      <!-- 对话气泡 -->
      <Transition name="bubble-pop">
        <div v-if="showBubble" class="pet-bubble" :class="bubbleClass">
          <span>{{ bubbleText }}</span>
          <div v-if="bubbleExtra" class="bubble-extra">{{ bubbleExtra }}</div>
        </div>
      </Transition>

      <!-- 经验特效 -->
      <Transition name="float-up">
        <div v-if="xpEffect" class="xp-effect">+{{ xpEffect }} XP</div>
      </Transition>

      <!-- ====== 素材帧播放器（QQ 企鹅精灵图 · Sprite Sheet） ====== -->
      <div
        class="pet-img"
        :style="frameStyle"
        :alt="`学习伙伴 - ${currentAnim}`"
        draggable="false"
        @dragstart.prevent
      ></div>

      <!-- 等级标签 -->
      <div class="pet-level">Lv{{ level }}</div>
    </div>

    <!-- ========== 喂养模式 ========== -->
    <Transition name="fade">
      <div v-if="showFeed" class="feed-overlay" @click="showFeed = false">
        <div class="feed-card" @click.stop>
          <h3>🍰 投喂学习能量</h3>
          <p>每次学习都能获得能量，喂给它会提升亲密度！</p>
          <div class="feed-actions">
            <button class="feed-btn" @click="feed('饼干')" :disabled="feeding">🍪 饼干</button>
            <button class="feed-btn" @click="feed('苹果')" :disabled="feeding">🍎 苹果</button>
            <button class="feed-btn" @click="feed('蛋糕')" :disabled="feeding">🍰 蛋糕</button>
          </div>
          <div v-if="feedResult" class="feed-result">
            <span class="feed-emoji">{{ feedResult === 'love' ? '🥰' : '😋' }}</span>
            <span>{{ feedResult === 'love' ? '超喜欢你！亲密度 +10' : '好吃～亲密度 +5' }}</span>
          </div>
        </div>
      </div>
    </Transition>

    <!-- ========== 双击小游戏 ========== -->
    <Transition name="fade">
      <div v-if="showGame" class="game-overlay" @click="showGame = false">
        <div class="game-card" @click.stop>
          <div v-if="!gameStart">
            <h3>🎮 反应力挑战</h3>
            <p>快速点击出现的 ⭐，看你能拿几分！</p>
            <button class="btn-primary" @click="startGame">开始游戏</button>
          </div>
          <div v-else>
            <h3>🎮 得分：{{ gameScore }}</h3>
            <p>还剩 {{ gameTime }} 秒，快点击 ⭐！</p>
            <div class="game-area">
              <Transition name="pop">
                <div
                  v-if="starVisible"
                  class="game-star"
                  :style="{ left: starX + '%', top: starY + '%' }"
                  @click="hitStar"
                >⭐</div>
              </Transition>
            </div>
            <button class="btn-primary" @click="startGame">重新开始</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
// @ts-ignore - JSON 资源由 Vite 直接提供
import petConfig from './petConfig.json'
import petBus from './petBus'

// 把 JSON 当任意结构使用，避免逐字段类型摩擦
const cfg: any = petConfig

const props = defineProps<{ level: number; xp: number }>()

/* =========================================================
 * 素材帧播放器（Sprite Sheet 模式）
 * - 单张雪碧图 + background-position 切帧，1 次请求替代 164 次
 * - 优先 WebP（lossless，约 5.3MB），不支持回退 PNG
 * ========================================================= */
const PET_BASE = '/pet/'
const sheetMeta: any = cfg.sheet
const assetIndex: Record<string, number[]> = cfg.assetIndex
const animations: Record<string, any> = cfg.animations
const transitions: Record<string, { from: string[]; to: string }> =
  cfg.stateMachine?.transitions ?? {}

// 动画名 <-> 触发名 双向映射：既能走状态机 transition，也能按动画自身的
// trigger 字段直接播放（调试面板 / 名称直发都可用）
const triggerToAnim: Record<string, string> = {}
for (const [name, a] of Object.entries(animations)) {
  triggerToAnim[name] = name
  if (a && a.trigger) triggerToAnim[a.trigger] = name
}

// 显示尺寸（与旧 .pet-img 宽度一致）
const DISPLAY = 150
const scale = DISPLAY / sheetMeta.frameW

function supportsWebp(): boolean {
  try {
    return document.createElement('canvas').toDataURL('image/webp').indexOf('image/webp') > 0
  } catch {
    return false
  }
}
const sheetUrl = computed(() =>
  PET_BASE + (supportsWebp() && sheetMeta.webp ? sheetMeta.webp : sheetMeta.file)
)

// 单张雪碧图预加载（一次即可）
const sheetReady = ref(false)
const sheetImg = new Image()
sheetImg.onload = () => { sheetReady.value = true }
sheetImg.onerror = () => { sheetReady.value = true } // 失败也放行，避免一直空白

const currentAnim = ref('idle')
const frameIndex = ref(0)

const frameStyle = computed<Record<string, string>>(() => {
  const indices = assetIndex[currentAnim.value] || assetIndex.idle
  const idx = indices[frameIndex.value] ?? indices[0]
  const col = idx % sheetMeta.cols
  const row = Math.floor(idx / sheetMeta.cols)
  const x = -col * sheetMeta.frameW * scale
  const y = -row * sheetMeta.frameH * scale
  const sw = sheetMeta.cols * sheetMeta.frameW * scale
  const sh = sheetMeta.rows * sheetMeta.frameH * scale
  return {
    backgroundImage: `url("${sheetUrl.value}")`,
    backgroundSize: `${sw}px ${sh}px`,
    backgroundPosition: `${x}px ${y}px`,
    backgroundRepeat: 'no-repeat',
    width: DISPLAY + 'px',
    height: DISPLAY + 'px',
    visibility: sheetReady.value ? 'visible' : 'hidden'
  }
})

let rafId: number | null = null
let lastTime = 0
let acc = 0

function tick(now: number) {
  const anim = animations[currentAnim.value]
  if (!anim) {
    rafId = requestAnimationFrame(tick)
    return
  }
  const frameDur = 1000 / (anim.fps || 8)
  acc += now - lastTime
  lastTime = now
  if (acc >= frameDur) {
    acc = acc % frameDur
    const frames = assetIndex[currentAnim.value] || assetIndex.idle
    frameIndex.value++
    if (frameIndex.value >= frames.length) {
      if (anim.loop) {
        frameIndex.value = 0
      } else {
        const to = anim.transitionTo || 'idle'
        play(to)
        return
      }
    }
  }
  rafId = requestAnimationFrame(tick)
}

function play(anim: string) {
  if (!animations[anim] || !assetIndex[anim]) anim = 'idle'
  currentAnim.value = anim
  frameIndex.value = 0
  acc = 0
  lastTime = performance.now()
  if (rafId === null) rafId = requestAnimationFrame(tick)
}

// 状态机事件触发：仅当当前动画在 from 列表中（或 from 含 *）才切换
function canTrigger(event: string): string | null {
  const t = transitions[event]
  if (!t) return null
  if (t.from.includes('*') || t.from.includes(currentAnim.value)) return t.to
  return null
}
function trigger(event: string) {
  const to = canTrigger(event)
  if (to) play(to)
}

function startLoop() {
  if (rafId === null) {
    lastTime = performance.now()
    rafId = requestAnimationFrame(tick)
  }
}
function stopLoop() {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
}

/* =========================================================
 * 事件总线订阅：外部学习会话（番茄钟/专注/分心）驱动宠物
 * - 优先走状态机 transition（受 from 约束，避免打断合理状态）
 * - 失败时按动画 trigger 字段 / 动画名直接播放（调试/名称直发必响应）
 * ========================================================= */
function onPetEvent(type: string) {
  const to = canTrigger(type)
  if (to) {
    play(to)
    return
  }
  const direct = triggerToAnim[type]
  if (direct) {
    play(direct)
    return
  }
  if (animations[type]) play(type)
}

/* =========================================================
 * 位置与拖拽
 * ========================================================= */
const petRef = ref<HTMLElement | null>(null)
const pos = ref({ x: 0, y: 0 })
const isDragging = ref(false)
let dragOffset = { x: 0, y: 0 }
let wanderTimer: ReturnType<typeof setTimeout> | null = null
let idleTimer: ReturnType<typeof setTimeout> | null = null
let wasDragged = false

const petStyle = computed(() => ({
  left: pos.value.x + 'px',
  top: pos.value.y + 'px'
}))

function onDragStart(e: MouseEvent) {
  const pet = petRef.value
  if (!pet) return
  isDragging.value = true
  wasDragged = false
  const rect = pet.getBoundingClientRect()
  dragOffset.x = e.clientX - rect.left
  dragOffset.y = e.clientY - rect.top

  const onMove = (ev: MouseEvent) => {
    wasDragged = true
    pos.value.x = Math.max(0, Math.min(window.innerWidth - 160, ev.clientX - dragOffset.x))
    pos.value.y = Math.max(0, Math.min(window.innerHeight - 200, ev.clientY - dragOffset.y))
  }
  const onUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

/* =========================================================
 * 对话气泡 / 互动
 * ========================================================= */
const showBubble = ref(false)
const bubbleText = ref('')
const bubbleExtra = ref('')
const bubbleClass = ref('')

const chats = [
  { text: '你好呀～', tip: '今天一起学习吧！' },
  { text: '学习使我快乐！', tip: '你已经 Lv' + props.level + ' 啦' },
  { text: '你学了多少卡？', tip: '我帮你记着呢～' },
  { text: '努力就有回报', tip: '每一张卡片都不会白学' },
  { text: '知识就是力量', tip: '继续加油💪' },
  { text: '好厉害！', tip: '你的坚持让我感动' },
  { text: '休息一下？', tip: '劳逸结合也很重要' },
  { text: '今天打卡了吗', tip: '连续学习天数别忘了' },
  { text: '嗷呜～', tip: '主人今天想学什么？' },
  { text: '哼哼', tip: '我可不是普通宠物，我会监督你学习' }
]

function showChat() {
  const c = chats[Math.floor(Math.random() * chats.length)]
  bubbleText.value = c.text
  bubbleExtra.value = c.tip
  bubbleClass.value = ''
  showBubble.value = true
  setTimeout(() => { showBubble.value = false }, 3500)
}

const xpEffect = ref(0)

function onClick() {
  if (wasDragged) return
  showChat()
  trigger('user_click') // idle -> happy
}

/* =========================================================
 * 自动游走（walk 动画）
 * ========================================================= */
const isWalking = ref(false)

function randomWander() {
  isWalking.value = true
  play('walk')
  const maxX = window.innerWidth - 160
  const maxY = window.innerHeight - 200
  pos.value.x = Math.random() * maxX
  pos.value.y = Math.random() * maxY
  setTimeout(() => {
    isWalking.value = false
    play('idle')
  }, 2000)
  wanderTimer = setTimeout(randomWander, 15000 + Math.random() * 10000)
}

/* =========================================================
 * 空闲随机情绪动作
 * ========================================================= */
const emotes = ['wave', 'love', 'think', 'surprised', 'giveheart', 'happy']
function randomIdle() {
  if (isWalking.value) {
    idleTimer = setTimeout(randomIdle, 8000 + Math.random() * 12000)
    return
  }
  const e = emotes[Math.floor(Math.random() * emotes.length)]
  play(e) // 这些动画 transitionTo=idle，播完自动回到 idle
  idleTimer = setTimeout(randomIdle, 9000 + Math.random() * 12000)
}

/* =========================================================
 * 夜间模式 -> sleep
 * ========================================================= */
let nightTimer: ReturnType<typeof setInterval> | null = null
function checkNight() {
  const h = new Date().getHours()
  const isNight = h >= (cfg.studyMonitor?.nightModeStartHour ?? 23) ||
    h < (cfg.studyMonitor?.nightModeEndHour ?? 7)
  if (isNight && currentAnim.value !== 'sleep' && currentAnim.value !== 'walk') {
    trigger('night_mode') // -> sleep
  } else if (!isNight && currentAnim.value === 'sleep') {
    play('idle')
  }
}

/* =========================================================
 * 双击小游戏
 * ========================================================= */
const showGame = ref(false)
const gameStart = ref(false)
const starVisible = ref(false)
const starX = ref(50)
const starY = ref(50)
const gameScore = ref(0)
const gameTime = ref(0)
let gameInterval: ReturnType<typeof setInterval> | null = null
let starTimer: ReturnType<typeof setTimeout> | null = null

function openGame() {
  showGame.value = true
  gameStart.value = false
  gameScore.value = 0
}

function startGame() {
  gameStart.value = true
  gameScore.value = 0
  gameTime.value = 15
  spawnStar()
  gameInterval = setInterval(() => {
    gameTime.value--
    if (gameTime.value <= 0) {
      clearInterval(gameInterval!)
      gameStart.value = false
      trigger('task_complete') // -> thumbsup 庆祝
      showChat()
    }
  }, 1000)
}

function spawnStar() {
  starX.value = 10 + Math.random() * 80
  starY.value = 10 + Math.random() * 70
  starVisible.value = true
  starTimer = setTimeout(() => {
    starVisible.value = false
    if (gameStart.value) {
      starTimer = setTimeout(spawnStar, 300 + Math.random() * 800)
    }
  }, 1200)
}

function hitStar() {
  if (!gameStart.value) return
  gameScore.value++
  starVisible.value = false
  if (starTimer) clearTimeout(starTimer)
  xpEffect.value += 3
  setTimeout(() => { xpEffect.value -= 3 }, 800)
  starTimer = setTimeout(spawnStar, 200 + Math.random() * 500)
}

/* =========================================================
 * 喂养
 * ========================================================= */
const showFeed = ref(false)
const feeding = ref(false)
const feedResult = ref<'love' | 'yum' | null>(null)

function onRightClick(e: MouseEvent) {
  e.preventDefault()
  showFeed.value = true
}

function feed(item: string) {
  if (feeding.value) return
  feeding.value = true
  feedResult.value = item === '蛋糕' ? 'love' : 'yum'
  trigger('user_feed') // idle -> eat
  showChat()
  setTimeout(() => { feeding.value = false; feedResult.value = null }, 2000)
}

/* =========================================================
 * 页面可见性 -> 暂停动画
 * ========================================================= */
function onVisibility() {
  if (document.hidden) stopLoop()
  else startLoop()
}

// 关闭/刷新页面 -> 企鹅鞠躬告别
function onAppClose() {
  trigger('app_close') // -> bow
}

/* =========================================================
 * 初始化 / 清理
 * ========================================================= */
onMounted(() => {
  pos.value.x = window.innerWidth - 160
  pos.value.y = window.innerHeight - 200
  sheetImg.src = sheetUrl.value
  play('idle')
  wanderTimer = setTimeout(randomWander, 8000)
  idleTimer = setTimeout(randomIdle, 10000)
  nightTimer = setInterval(checkNight, 60000)
  checkNight()
  petRef.value?.addEventListener('contextmenu', onRightClick)
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('beforeunload', onAppClose)
  // 订阅学习事件总线
  petBus.on('*', onPetEvent as any)
})

onBeforeUnmount(() => {
  stopLoop()
  petRef.value?.removeEventListener('contextmenu', onRightClick)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('beforeunload', onAppClose)
  petBus.off('*', onPetEvent as any)
  if (wanderTimer) clearTimeout(wanderTimer)
  if (idleTimer) clearTimeout(idleTimer)
  if (nightTimer) clearInterval(nightTimer)
  if (gameInterval) clearInterval(gameInterval)
  if (starTimer) clearTimeout(starTimer)
})
</script>

<style scoped>
/* ====== 桌面宠物 ====== */
.desktop-pet {
  position: fixed;
  z-index: 9998;
  cursor: grab;
  user-select: none;
  transition: left 2s var(--ease-out), top 2s var(--ease-out);
}
.desktop-pet.dragging {
  transition: none;
  cursor: grabbing;
}
.desktop-pet.walking {
  animation: walk-bounce 0.35s ease-in-out infinite;
}
@keyframes walk-bounce {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-6px); }
}

.pet-img {
  display: block;
  pointer-events: none;
  user-select: none;
  -webkit-user-drag: none;
}
.desktop-pet:hover .pet-img {
  transform: scale(1.04);
  transition: transform 0.25s var(--ease-out);
}

/* ====== 对话气泡 ====== */
.pet-bubble {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 8px 14px;
  font-size: 0.8125rem;
  color: var(--text-primary);
  box-shadow: var(--shadow-md);
  white-space: nowrap;
  z-index: 2;
  margin-bottom: 12px;
  min-width: 100px;
  text-align: center;
}
.pet-bubble::after {
  content: '';
  position: absolute;
  bottom: -7px; left: 50%;
  transform: translateX(-50%);
  width: 0; height: 0;
  border-left: 7px solid transparent;
  border-right: 7px solid transparent;
  border-top: 7px solid var(--surface);
}
.pet-bubble::before {
  content: '';
  position: absolute;
  bottom: -8px; left: 50%;
  transform: translateX(-50%);
  width: 0; height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 8px solid var(--border);
}
.bubble-extra {
  font-size: 0.6875rem;
  color: var(--text-muted);
  margin-top: 2px;
}

.bubble-pop-enter-active { animation: bubble-pop 0.3s var(--ease-out); }
.bubble-pop-leave-active { animation: bubble-pop 0.2s reverse; }
@keyframes bubble-pop {
  from { opacity: 0; transform: translateX(-50%) scale(0.85) translateY(6px); }
  to   { opacity: 1; transform: translateX(-50%) scale(1) translateY(0); }
}

/* XP 特效 */
.xp-effect {
  position: absolute;
  top: -30px; left: 50%; transform: translateX(-50%);
  font-weight: 700; color: var(--primary-600); font-size: 0.75rem;
  z-index: 3;
}
.float-up-enter-active { animation: float-up 0.8s ease-out; }
.float-up-leave-active { animation: float-up 0.3s ease-in reverse; }
@keyframes float-up {
  from { opacity: 1; transform: translateX(-50%) translateY(0); }
  to   { opacity: 0; transform: translateX(-50%) translateY(-24px); }
}

/* 等级标签 */
.pet-level {
  position: absolute; bottom: -10px; left: 50%;
  transform: translateX(-50%);
  background: var(--primary-600); color: #fff;
  font-size: 0.6875rem; font-weight: 700;
  padding: 2px 10px; border-radius: 999px;
  white-space: nowrap;
}

/* ====== 喂养弹窗 ====== */
.feed-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.3);
  z-index: 9999; display: flex; align-items: center; justify-content: center;
}
.feed-card {
  background: var(--surface); border-radius: var(--radius-xl);
  padding: var(--space-2xl); text-align: center; max-width: 380px;
  box-shadow: var(--shadow-lg);
}
.feed-card h3 { margin: 0 0 var(--space-sm); }
.feed-card p { color: var(--text-secondary); font-size: 0.875rem; margin-bottom: var(--space-lg); }
.feed-actions { display: flex; gap: var(--space-sm); justify-content: center; }
.feed-btn {
  border: 2px solid var(--border); background: var(--surface); border-radius: var(--radius-md);
  padding: 10px 16px; cursor: pointer; font-size: 1rem; transition: transform var(--duration-fast);
}
.feed-btn:hover { transform: scale(1.1); border-color: var(--primary-300); }
.feed-btn:disabled { opacity: 0.5; cursor: default; }
.feed-result { margin-top: var(--space-md); font-size: 0.875rem; color: var(--text-primary); }
.feed-emoji { font-size: 1.5rem; }

/* ====== 游戏弹窗 ====== */
.game-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.3);
  z-index: 9999; display: flex; align-items: center; justify-content: center;
}
.game-card {
  background: var(--surface); border-radius: var(--radius-xl);
  padding: var(--space-2xl); text-align: center; max-width: 420px; width: 90%;
  box-shadow: var(--shadow-lg);
}
.game-card h3 { margin: 0 0 var(--space-sm); }
.game-card p { color: var(--text-secondary); margin-bottom: var(--space-lg); }
.game-area {
  width: 100%; height: 220px; background: var(--surface-2);
  border-radius: var(--radius-lg); position: relative; overflow: hidden;
  margin-bottom: var(--space-lg);
}
.game-star {
  position: absolute; font-size: 2rem; cursor: pointer;
  transform: translate(-50%, -50%); user-select: none;
}
.game-star:hover { font-size: 2.5rem; }
.pop-enter-active { animation: pop-star 0.2s ease-out; }
.pop-leave-active { animation: pop-star 0.15s ease-in reverse; }
@keyframes pop-star {
  from { transform: translate(-50%, -50%) scale(0); }
  to   { transform: translate(-50%, -50%) scale(1); }
}
.btn-primary {
  background: var(--primary-600); color: #fff; border: none;
  border-radius: var(--radius-md); padding: 10px 24px;
  font-size: 0.9375rem; cursor: pointer; font-weight: 600;
  transition: background var(--duration-fast);
}
.btn-primary:hover { background: var(--primary-700); }

.fade-enter-active { animation: fade-in 0.2s; }
.fade-leave-active { animation: fade-in 0.15s reverse; }
@keyframes fade-in {
  from { opacity: 0; }
  to   { opacity: 1; }
}
</style>

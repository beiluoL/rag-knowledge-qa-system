<template>
  <Teleport to="body">
    <!-- ========== 桌面宠物主体 ========== -->
    <div
      ref="petRef"
      class="desktop-pet"
      :style="petStyle"
      :class="{ dragging: isDragging, walking: isWalking, summoning: isSummoning, flying: physics.active }"
      @mousedown.prevent="onDragStart"
      @dblclick.prevent="openGame"
      role="button"
      aria-label="学习伙伴，点击互动"
      tabindex="0"
    >
      <!-- 对话气泡 -->
      <Transition name="bubble-pop">
        <div v-if="showBubble" class="pet-bubble">
          <span>{{ bubbleText }}</span>
          <div v-if="bubbleExtra" class="bubble-extra">{{ bubbleExtra }}</div>
        </div>
      </Transition>

      <!-- 经验特效 -->
      <Transition name="float-up">
        <div v-if="xpEffect" class="xp-effect">+{{ xpEffect }} XP</div>
      </Transition>

      <!-- 控制按钮（召唤/菜单） -->
      <button class="pet-menu-btn" :title="showMenu ? '收起' : '互动菜单'" @mousedown.stop @click.stop="showMenu = !showMenu">
        {{ showMenu ? '×' : '⚙' }}
      </button>

      <!-- 控制面板 -->
      <Transition name="fade">
        <div v-if="showMenu" class="pet-panel" :class="{ below: panelPos.below, alignRight: panelPos.alignRight }" @mousedown.stop @click.stop>
          <div class="pet-hud">
            <div class="hud-row">
              <span class="hud-label">🍖 饱食</span>
              <div class="hud-bar"><i :style="{ width: satietyPct + '%' }" :class="{ low: satietyPct < 25 }"></i></div>
            </div>
            <div class="hud-row">
              <span class="hud-label">⚡ 精力</span>
              <div class="hud-bar"><i :style="{ width: energyPct + '%' }" :class="{ low: energyPct < 25 }"></i></div>
            </div>
            <div class="hud-row">
              <span class="hud-label">💗 亲密</span>
              <div class="hud-bar love"><i :style="{ width: affinityPct + '%' }"></i></div>
            </div>
            <div class="hud-row">
              <span class="hud-label">⭐ 升级</span>
              <div class="hud-bar xp"><i :style="{ width: (100 - xpToNext) + '%' }"></i></div>
            </div>
          </div>
          <div class="pet-actions">
            <button @click="summonTo(windowCenter())">📣 召唤</button>
            <button @click="openFeed">🍰 喂食</button>
            <button @click="petAction">🤚 抚摸</button>
            <button @click="pokeSequence">👉 戳一戳</button>
            <button @click="scoldAction">😱 吓一跳</button>
            <button @click="openGame">🎮 小游戏</button>
            <button @click="takePhoto">📸 拍照</button>
            <button @click="toggleBow">🎀 蝴蝶结</button>
            <button @click="audio.toggle()">{{ audio.enabled.value ? '🔊 音效' : '🔇 静音' }}</button>
            <button class="danger" @click="confirmReset">♻️ 重置</button>
          </div>
        </div>
      </Transition>

      <!-- 素材帧播放器 -->
      <div class="pet-img" :style="frameStyle" :alt="`学习伙伴 - ${currentAnim}`" draggable="false" @dragstart.prevent>
        <div v-if="showBow" class="pet-bow">🎀</div>
      </div>

      <!-- 等级标签 -->
      <div class="pet-level">Lv{{ state.level }}</div>
    </div>

    <!-- ========== 喂养模式 ========== -->
    <Transition name="fade">
      <div v-if="showFeed" class="feed-overlay" @click="showFeed = false">
        <div class="feed-card" @click.stop>
          <h3>🍰 投喂学习能量</h3>
          <p>喂饱它会提升饱食度与亲密度！</p>
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

    <!-- ========== 双击小游戏（接能量球） ========== -->
    <Transition name="fade">
      <div v-if="showGame" class="game-overlay" @click="showGame = false">
        <div class="game-card" @click.stop>
          <div v-if="!gameStart">
            <h3>🎮 接能量球</h3>
            <p>点击下落的能量球接住它，看你能接几个！</p>
            <button class="btn-primary" @click="startGame">开始游戏</button>
          </div>
          <div v-else>
            <h3>🎮 接住：{{ gameScore }}</h3>
            <p>还剩 {{ gameTime }} 秒，快接住能量球！</p>
            <div class="game-area">
              <Transition name="pop">
                <div
                  v-if="orbVisible"
                  class="game-orb"
                  :style="{ left: orbX + '%', top: orbY + '%' }"
                  @click="hitOrb"
                >💡</div>
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
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
// @ts-ignore
import petConfig from './petConfig.json'
import petBus from './petBus'
import { usePetState } from '@/composables/usePetState'
import { usePetAudio } from '@/composables/petAudio'

const cfg: any = petConfig
const props = defineProps<{ level?: number; xp?: number }>()

/* 单例：养成状态 + 音效 */
const petState = usePetState()
const audio = usePetAudio()
const state = petState.state

/* =========================================================
 * 素材帧播放器（Sprite Sheet）
 * ========================================================= */
const PET_BASE = '/pet/'
const sheetMeta: any = cfg.sheet
const assetIndex: Record<string, number[]> = cfg.assetIndex
const animations: Record<string, any> = cfg.animations
const transitions: Record<string, { from: string[]; to: string }> =
  cfg.stateMachine?.transitions ?? {}

const triggerToAnim: Record<string, string> = {}
for (const [name, a] of Object.entries(animations)) {
  triggerToAnim[name] = name
  if (a && a.trigger) triggerToAnim[a.trigger] = name
}

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

const sheetReady = ref(false)
const sheetImg = new Image()
sheetImg.onload = () => { sheetReady.value = true }
sheetImg.onerror = () => { sheetReady.value = true }

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
  // 物理抛掷
  if (physics.active) stepPhysics(now)

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

function canTrigger(event: string): string | null {
  const t = transitions[event]
  if (!t) return null
  if (t.from.includes('*') || t.from.includes(currentAnim.value)) return t.to
  return null
}
function trigger(event: string) {
  const to = canTrigger(event)
  if (to) { play(to); return }
  const direct = triggerToAnim[event]
  if (direct) { play(direct); return }
  if (animations[event]) play(event)
}

function startLoop() {
  if (rafId === null) {
    lastTime = performance.now()
    rafId = requestAnimationFrame(tick)
  }
}
function stopLoop() {
  if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
}

/* 学习事件总线：保留原有 20+ 事件驱动，并联动养成/音效 */
function onPetEvent(type: string) {
  trigger(type)
  switch (type) {
    case 'task_complete':
      petState.doStudyComplete()
      audio.cheer()
      break
    case 'all_goals_complete':
      petState.gainXp(50)
      break
    case 'over_study':
      petState.doStudyMinute()
      break
  }
}

/* =========================================================
 * 位置 / 拖拽 / 抚摸 / 抛掷
 * ========================================================= */
const petRef = ref<HTMLElement | null>(null)
const pos = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const isWalking = ref(false)
const isSummoning = ref(false)
let wanderTimer: ReturnType<typeof setTimeout> | null = null
let idleTimer: ReturnType<typeof setTimeout> | null = null

const petStyle = computed(() => ({
  left: pos.value.x + 'px',
  top: pos.value.y + 'px'
}))

// 物理状态
const physics = ref({ active: false, vx: 0, vy: 0, last: 0 })

function onDragStart(e: MouseEvent) {
  if (showMenu.value) return
  const pet = petRef.value
  if (!pet) return
  isDragging.value = true
  if (physics.value.active) physics.value.active = false
  const rect = pet.getBoundingClientRect()
  const offX = e.clientX - rect.left
  const offY = e.clientY - rect.top
  let downT = performance.now()
  let didMove = false
  let didPet = false
  const samples: { x: number; y: number; t: number }[] = []

  // 长按 = 抚摸
  const longPress = setTimeout(() => {
    if (!didMove) { didPet = true; petAction() }
  }, 500)

  const onMove = (ev: MouseEvent) => {
    samples.push({ x: ev.clientX, y: ev.clientY, t: performance.now() })
    if (samples.length > 6) samples.shift()
    const dx = ev.clientX - (rect.left + offX)
    const dy = ev.clientY - (rect.top + offY)
    if (!didMove && Math.hypot(ev.clientX - (rect.left + offX), ev.clientY - (rect.top + offY)) > 6) {
      didMove = true
      clearTimeout(longPress)
    }
    if (didMove) {
      pos.value.x = Math.max(0, Math.min(window.innerWidth - 160, ev.clientX - offX))
      pos.value.y = Math.max(0, Math.min(window.innerHeight - 200, ev.clientY - offY))
    }
  }
  const onUp = () => {
    clearTimeout(longPress)
    isDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    if (!didMove && !didPet) {
      handleClick()
    } else if (didMove) {
      const v = computeVelocity(samples)
      if (Math.hypot(v.x, v.y) > 0.45) startThrow(v)
    }
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function computeVelocity(samples: { x: number; y: number; t: number }[]) {
  if (samples.length < 2) return { x: 0, y: 0 }
  const a = samples[samples.length - 2]
  const b = samples[samples.length - 1]
  const dt = Math.max(1, b.t - a.t)
  return { x: (b.x - a.x) / dt, y: (b.y - a.y) / dt }
}

function startThrow(v: { x: number; y: number }) {
  physics.value = { active: true, vx: v.x, vy: v.y, last: performance.now() }
  isDragging.value = true
  trigger('user_throw') // surprised
  audio.surprise()
}

function stepPhysics(now: number) {
  const dt = Math.min(40, now - physics.value.last)
  physics.value.last = now
  const g = 0.0022
  physics.value.vy += g * dt
  let nx = pos.value.x + physics.value.vx * dt
  let ny = pos.value.y + physics.value.vy * dt
  const W = window.innerWidth - 160
  const H = window.innerHeight - 200
  if (nx < 0) { nx = 0; physics.value.vx = -physics.value.vx * 0.6 }
  if (nx > W) { nx = W; physics.value.vx = -physics.value.vx * 0.6 }
  if (ny < 0) { ny = 0; physics.value.vy = -physics.value.vy * 0.6 }
  if (ny > H) { ny = H; physics.value.vy = -physics.value.vy * 0.6; physics.value.vx *= 0.8 }
  pos.value.x = nx
  pos.value.y = ny
  const speed = Math.hypot(physics.value.vx, physics.value.vy)
  if (speed < 0.06 && ny >= H - 2) {
    physics.value.active = false
    isDragging.value = false
    play('tired')
    audio.sad()
  }
}

/* 点击连击 -> 普通点击 / 连戳 poke / 连戳过头 angry */
let clickTimes: number[] = []
let pokeStreak = 0
let pokeResetTimer: ReturnType<typeof setTimeout> | null = null

function handleClick() {
  clickTimes.push(performance.now())
  clickTimes = clickTimes.filter((t) => performance.now() - t < 1200)
  showChat()
  trigger('user_click') // happy
  audio.click()
  if (clickTimes.length >= 3) {
    clickTimes = []
    pokeSequence()
  }
}

function petAction() {
  trigger('user_pet') // love
  petState.doPet()
  audio.blip()
  showXp(4)
}

function pokeSequence() {
  trigger('user_poke') // poke
  petState.doPoke()
  audio.poke()
  pokeStreak++
  if (pokeResetTimer) clearTimeout(pokeResetTimer)
  pokeResetTimer = setTimeout(() => { pokeStreak = 0 }, 2000)
  if (pokeStreak >= 5) {
    pokeStreak = 0
    trigger('pet_combo') // angry
    showChat()
  }
}

function scoldAction() {
  trigger('user_scold') // surprised
  petState.doScold()
  audio.surprise()
  showChat()
}

function summonTo(tx: number, ty: number) {
  isSummoning.value = true
  play('walk')
  pos.value.x = Math.max(0, Math.min(window.innerWidth - 160, tx))
  pos.value.y = Math.max(0, Math.min(window.innerHeight - 200, ty))
  setTimeout(() => {
    isSummoning.value = false
    trigger('user_interact') // wave
  }, 1200)
}

function windowCenter() {
  return { x: window.innerWidth / 2 - 80, y: window.innerHeight - 220 }
}

/* =========================================================
 * 对话气泡
 * ========================================================= */
const showBubble = ref(false)
const bubbleText = ref('')
const bubbleExtra = ref('')
const xpEffect = ref(0)

const chats = [
  { text: '你好呀～', tip: '今天一起学习吧！' },
  { text: '学习使我快乐！', tip: '你已经 Lv' + (props.level ?? petState.state.value.level) + ' 啦' },
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
  showBubble.value = true
  setTimeout(() => { showBubble.value = false }, 3500)
}

function showXp(n: number) {
  xpEffect.value = n
  setTimeout(() => { xpEffect.value = 0 }, 900)
}

/* =========================================================
 * 自动游走
 * ========================================================= */
function randomWander() {
  if (physics.value.active) { wanderTimer = setTimeout(randomWander, 8000); return }
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

/* 空闲情绪：受心情影响 */
const emotes = ['wave', 'love', 'think', 'surprised', 'giveheart', 'happy']
function randomIdle() {
  if (isWalking.value || physics.value.active) {
    idleTimer = setTimeout(randomIdle, 8000 + Math.random() * 12000)
    return
  }
  let e: string
  const m = petState.mood.value
  if (m === 'hungry') e = Math.random() < 0.6 ? 'sad' : 'think'
  else if (m === 'tired') e = Math.random() < 0.6 ? 'sleepy' : 'think'
  else if (m === 'happy') e = Math.random() < 0.5 ? 'love' : 'giveheart'
  else e = emotes[Math.floor(Math.random() * emotes.length)]
  play(e)
  idleTimer = setTimeout(randomIdle, 9000 + Math.random() * 12000)
}

/* 夜间模式 */
let nightTimer: ReturnType<typeof setInterval> | null = null
function checkNight() {
  const h = new Date().getHours()
  const isNight = h >= (cfg.studyMonitor?.nightModeStartHour ?? 23) ||
    h < (cfg.studyMonitor?.nightModeEndHour ?? 7)
  if (isNight && currentAnim.value !== 'sleep' && currentAnim.value !== 'walk') {
    trigger('night_mode')
  } else if (!isNight && currentAnim.value === 'sleep') {
    play('idle')
  }
}

/* =========================================================
 * 小游戏：接能量球
 * ========================================================= */
const showGame = ref(false)
const gameStart = ref(false)
const orbVisible = ref(false)
const orbX = ref(50)
const orbY = ref(10)
const gameScore = ref(0)
const gameTime = ref(0)
let gameInterval: ReturnType<typeof setInterval> | null = null
let orbTimer: ReturnType<typeof setTimeout> | null = null

function openGame() {
  showGame.value = true
  gameStart.value = false
  gameScore.value = 0
}

function startGame() {
  gameStart.value = true
  gameScore.value = 0
  gameTime.value = 15
  spawnOrb()
  gameInterval = setInterval(() => {
    gameTime.value--
    if (gameTime.value <= 0) {
      clearInterval(gameInterval!)
      gameStart.value = false
      if (gameScore.value >= 8) { trigger('all_goals_complete'); audio.cheer() }
      else { trigger('goal_missed'); audio.sad() }
      showChat()
    }
  }, 1000)
}

function spawnOrb() {
  orbX.value = 10 + Math.random() * 80
  orbY.value = 10 + Math.random() * 20
  orbVisible.value = true
  orbTimer = setTimeout(() => {
    orbVisible.value = false
    if (gameStart.value) orbTimer = setTimeout(spawnOrb, 300 + Math.random() * 800)
  }, 1200)
}

function hitOrb() {
  if (!gameStart.value) return
  gameScore.value++
  orbVisible.value = false
  audio.blip()
  showXp(3)
  if (orbTimer) clearTimeout(orbTimer)
  orbTimer = setTimeout(spawnOrb, 200 + Math.random() * 500)
}

/* =========================================================
 * 喂养
 * ========================================================= */
const showFeed = ref(false)
const feeding = ref(false)
const feedResult = ref<'love' | 'yum' | null>(null)

function openFeed() { showFeed.value = true; showMenu.value = false }
function onRightClick(e: MouseEvent) { e.preventDefault(); showFeed.value = true }

function feed(item: string) {
  if (feeding.value) return
  feeding.value = true
  const res = petState.doFeed(item)
  feedResult.value = res
  trigger('user_feed')
  audio.munch()
  showChat()
  showXp(8)
  setTimeout(() => { feeding.value = false; feedResult.value = null }, 2000)
}

/* =========================================================
 * 拍照合影
 * ========================================================= */
function takePhoto() {
  showMenu.value = false
  const c = document.createElement('canvas')
  const S = 320
  c.width = S; c.height = S
  const ctx = c.getContext('2d')
  if (!ctx) return
  const grd = ctx.createLinearGradient(0, 0, S, S)
  grd.addColorStop(0, '#2563EB')
  grd.addColorStop(1, '#1e3a8a')
  ctx.fillStyle = grd
  ctx.fillRect(0, 0, S, S)
  const indices = assetIndex[currentAnim.value] || assetIndex.idle
  const idx = indices[frameIndex.value] ?? indices[0]
  const col = idx % sheetMeta.cols
  const row = Math.floor(idx / sheetMeta.cols)
  const fw = sheetMeta.frameW
  const fh = sheetMeta.frameH
  const pad = 36
  try {
    ctx.drawImage(sheetImg, col * fw, row * fh, fw, fh, pad, pad + 10, S - pad * 2, S - pad * 2)
  } catch { /* ignore */ }
  ctx.fillStyle = '#fff'
  ctx.textAlign = 'center'
  ctx.font = 'bold 24px sans-serif'
  ctx.fillText('🐧 学习伙伴', S / 2, 44)
  ctx.font = '16px sans-serif'
  const moodText = petState.mood.value === 'happy' ? '心情超好' : petState.mood.value === 'hungry' ? '饿饿' : petState.mood.value === 'tired' ? '累累了' : '状态不错'
  ctx.fillText('Lv' + petState.state.value.level + ' · ' + moodText, S / 2, S - 28)
  c.toBlob((b) => {
    if (!b) return
    const u = URL.createObjectURL(b)
    const a = document.createElement('a')
    a.href = u
    a.download = 'study-pet-Lv' + petState.state.value.level + '.png'
    a.click()
    URL.revokeObjectURL(u)
  })
  bubbleText.value = '茄子～📸'
  bubbleExtra.value = ''
  showBubble.value = true
  setTimeout(() => { showBubble.value = false }, 2500)
}

/* 蝴蝶结装饰 */
const showBow = ref(false)
function toggleBow() { showBow.value = !showBow.value; showMenu.value = false }

/* 重置 */
function confirmReset() {
  petState.reset()
  showMenu.value = false
  showChat()
  trigger('all_goals_complete')
}

/* 升级庆祝 */
watch(petState.levelUpFlag, () => {
  trigger('level_up') // celebrate
  audio.levelup()
  bubbleText.value = '升级啦！Lv' + petState.state.value.level + ' 🎉'
  bubbleExtra.value = '我又变强了一点～'
  showBubble.value = true
  setTimeout(() => { showBubble.value = false }, 3500)
})

/* 可见性 / 关闭 */
function onVisibility() { if (document.hidden) stopLoop(); else startLoop() }
function onAppClose() { trigger('app_close') }

/* 初始化 */
onMounted(() => {
  pos.value.x = window.innerWidth - 160
  pos.value.y = window.innerHeight - 200
  sheetImg.src = sheetUrl.value
  petState.initPetState()
  if (props.xp) petState.seed(props.xp, props.level ?? 1)
  play('idle')
  wanderTimer = setTimeout(randomWander, 8000)
  idleTimer = setTimeout(randomIdle, 10000)
  nightTimer = setInterval(checkNight, 60000)
  checkNight()
  petRef.value?.addEventListener('contextmenu', onRightClick)
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('beforeunload', onAppClose)
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
  if (orbTimer) clearTimeout(orbTimer)
  if (pokeResetTimer) clearTimeout(pokeResetTimer)
})

/* 控制菜单显隐 */
const showMenu = ref(false)

/* 菜单自适应定位：永远停在宠物侧外侧，不挡身体；贴顶翻下方；按左右半屏朝屏幕内侧展开 */
const panelPos = computed(() => {
  const below = pos.value.y < 340
  const alignRight = pos.value.x + 75 > window.innerWidth / 2
  return { below, alignRight }
})

/* HUD 计算 */
const satietyPct = petState.satietyPct
const energyPct = petState.energyPct
const affinityPct = petState.affinityPct
const xpToNext = petState.xpToNext
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
.desktop-pet.dragging,
.desktop-pet.flying {
  transition: none;
  cursor: grabbing;
}
.desktop-pet.summoning {
  transition: left 1.2s var(--ease-out), top 1.2s var(--ease-out);
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
  position: relative;
}
.desktop-pet:hover .pet-img {
  transform: scale(1.04);
  transition: transform 0.25s var(--ease-out);
}
.pet-bow {
  position: absolute;
  top: -14px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 1.6rem;
  filter: drop-shadow(0 2px 2px rgba(0,0,0,0.2));
}

/* 控制按钮 */
.pet-menu-btn {
  position: absolute;
  top: -34px;
  left: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-primary);
  cursor: pointer;
  font-size: 0.95rem;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
  z-index: 4;
}
.pet-menu-btn:hover { border-color: var(--primary-400); }

/* 控制面板：浮在宠物侧外侧，永远不遮挡身体 */
.pet-panel {
  position: absolute;
  bottom: calc(100% + 8px);
  left: calc(100% + 8px);
  width: 220px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  padding: var(--space-md);
  z-index: 5;
}
/* 贴屏幕顶部时翻到下方 */
.pet-panel.below {
  bottom: auto;
  top: calc(100% + 8px);
}
/* 宠物在右半屏时往左外侧展开，避免溢出右侧屏幕 */
.pet-panel.alignRight {
  left: auto;
  right: calc(100% + 8px);
}
.pet-hud { margin-bottom: var(--space-sm); }
.hud-row { display: flex; align-items: center; gap: 6px; margin-bottom: 5px; font-size: 0.6875rem; }
.hud-label { width: 44px; color: var(--text-secondary); flex-shrink: 0; }
.hud-bar {
  flex: 1; height: 7px; background: var(--surface-2);
  border-radius: 999px; overflow: hidden;
}
.hud-bar i { display: block; height: 100%; background: var(--primary-500); border-radius: 999px; transition: width 0.4s var(--ease-out); }
.hud-bar i.low { background: #ef4444; }
.hud-bar.love i { background: #ec4899; }
.hud-bar.xp i { background: #f59e0b; }
.pet-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.pet-actions button {
  border: 1px solid var(--border);
  background: var(--surface-2);
  border-radius: var(--radius-md);
  padding: 7px 4px;
  font-size: 0.75rem;
  cursor: pointer;
  color: var(--text-primary);
  transition: transform var(--duration-fast), border-color var(--duration-fast);
}
.pet-actions button:hover { transform: scale(1.05); border-color: var(--primary-400); }
.pet-actions button.danger:hover { border-color: #ef4444; color: #ef4444; }

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
.feed-actions { display: flex; gap: var(--space-sm); justify-content: center; flex-wrap: wrap; }
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
.game-orb {
  position: absolute; font-size: 2rem; cursor: pointer;
  transform: translate(-50%, -50%); user-select: none;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,0.25));
}
.game-orb:hover { font-size: 2.5rem; }
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

import { ref, computed } from 'vue'

/**
 * 宠物养成状态（单例）
 * - 饱食度 / 精力 / 亲密度 / 经验 / 等级
 * - localStorage 持久化 + 离线时间衰减
 * - 互动动作会改需求与经验，经验跨阈值触发升级
 */

const KEY = 'pet-state-v1'

export type Mood = 'hungry' | 'tired' | 'happy' | 'normal'

interface PetState {
  satiety: number // 饱食 0-100
  energy: number // 精力 0-100
  affinity: number // 亲密度 0-100
  xp: number // 累计经验
  level: number // 等级
  lastSeen: number // 上次时间戳，用于离线衰减
}

function def(): PetState {
  return { satiety: 100, energy: 100, affinity: 50, xp: 0, level: 1, lastSeen: Date.now() }
}

function clamp(v: number): number {
  return Math.max(0, Math.min(100, v))
}

function load(): PetState {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const p = JSON.parse(raw)
      return { ...def(), ...p }
    }
  } catch {
    /* ignore */
  }
  return def()
}

// 模块级单例：StudyPet 与控制菜单共享
const state = ref<PetState>(load())
const levelUpFlag = ref(0) // 每次升级 +1，组件监听播放庆祝

// 离线衰减（按分钟），温和曲线避免惩罚感
function applyDecay(minutes: number) {
  if (minutes <= 0) return
  state.value.satiety = clamp(state.value.satiety - minutes * 0.35)
  state.value.energy = clamp(state.value.energy - minutes * 0.22)
  state.value.affinity = clamp(state.value.affinity - minutes * 0.06)
}

function persist() {
  state.value.lastSeen = Date.now()
  try {
    localStorage.setItem(KEY, JSON.stringify(state.value))
  } catch {
    /* ignore */
  }
}

// 启动：补算离线衰减 + 每分钟自然衰减
function initPetState() {
  const now = Date.now()
  const offlineMin = (now - (state.value.lastSeen || now)) / 60000
  applyDecay(offlineMin)
  persist()
  setInterval(() => {
    applyDecay(1)
    persist()
  }, 60000)
}

function xpForLevel(level: number): number {
  return (level - 1) * 100
}

function addXp(n: number) {
  const before = state.value.level
  state.value.xp += n
  const newLevel = Math.floor(state.value.xp / 100) + 1
  if (newLevel > state.value.level) {
    state.value.level = newLevel
    levelUpFlag.value++
  }
  persist()
}

export function usePetState() {
  const mood = computed<Mood>(() => {
    if (state.value.satiety < 25) return 'hungry'
    if (state.value.energy < 25) return 'tired'
    if (state.value.affinity > 85) return 'happy'
    return 'normal'
  })

  const satietyPct = computed(() => Math.round(state.value.satiety))
  const energyPct = computed(() => Math.round(state.value.energy))
  const affinityPct = computed(() => Math.round(state.value.affinity))
  const xpToNext = computed(() => 100 - (state.value.xp % 100))

  function doFeed(item?: string): 'love' | 'yum' {
    state.value.satiety = clamp(state.value.satiety + 22)
    state.value.energy = clamp(state.value.energy + 4)
    state.value.affinity = clamp(state.value.affinity + (item === '蛋糕' ? 10 : 5))
    addXp(8)
    persist()
    return state.value.affinity > 85 ? 'love' : 'yum'
  }

  function doPet() {
    state.value.affinity = clamp(state.value.affinity + 6)
    state.value.energy = clamp(state.value.energy + 2)
    addXp(4)
    persist()
  }

  function doPoke() {
    state.value.affinity = clamp(state.value.affinity - 3)
    addXp(1)
    persist()
  }

  function doScold() {
    state.value.affinity = clamp(state.value.affinity - 6)
    state.value.energy = clamp(state.value.energy - 2)
    persist()
  }

  function doStudyComplete() {
    addXp(20)
    state.value.energy = clamp(state.value.energy - 12)
    persist()
  }

  function doStudyMinute() {
    state.value.energy = clamp(state.value.energy - 0.5)
    persist()
  }

  function seed(xp: number, level: number) {
    if (xp > 0) state.value.xp = Math.max(state.value.xp, xp)
    if (level > 1) state.value.level = Math.max(state.value.level, level)
    persist()
  }

  function reset() {
    state.value = def()
    persist()
  }

  return {
    state,
    mood,
    satietyPct,
    energyPct,
    affinityPct,
    xpToNext,
    levelUpFlag,
    initPetState,
    doFeed,
    doPet,
    doPoke,
    doScold,
    doStudyComplete,
    doStudyMinute,
    seed,
    reset,
    gainXp: addXp,
    persist
  }
}

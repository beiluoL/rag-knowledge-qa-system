/**
 * useStudySession —— 轻量番茄钟 / 专注会话
 * ---------------------------------------------------------------
 * 真实的「学习事件源」，驱动桌面宠物（StudyPet）的监督动画：
 *   start()           -> pomodoro_start  (cheer -> 自动 focus)
 *   专注中切走页面/失焦/长时间无操作 -> distraction_detected (nowphone)
 *   回到页面/窗口     -> studying        (focus)
 *   专注计时结束      -> task_complete    (thumbsup) 进入休息
 *   休息结束/手动停止 -> study_stop       (idle)
 *
 * 配置读取自 petConfig.json 的 studyMonitor（专注 25 分 / 休息 5 分）。
 */
import { ref, computed, onUnmounted } from 'vue'
import { petBus } from '@/components/petBus'
// @ts-ignore - JSON 资源由 Vite 直接提供
import petConfig from '@/components/petConfig.json'

const monitor: any = (petConfig as any).studyMonitor || {}
const FOCUS_MIN = monitor.pomodoroDuration ?? 25
const BREAK_MIN = monitor.breakDuration ?? 5

// 离开多久算「分心」（给一点宽限，避免误报）
const LEAVE_GRACE_MS = 8000
// 专注时多久没有任何鼠标/键盘操作算「走神」
const INACTIVE_MS = 60000

export function useStudySession() {
  const phase = ref<'idle' | 'focus' | 'break'>('idle')
  const isRunning = ref(false)
  const remaining = ref(0)
  const distracted = ref(false)

  let timer: ReturnType<typeof setInterval> | null = null
  let leaveTimer: ReturnType<typeof setTimeout> | null = null
  let inactiveTimer: ReturnType<typeof setTimeout> | null = null
  let startGuard: ReturnType<typeof setTimeout> | null = null

  const totalSec = computed(() => (phase.value === 'break' ? BREAK_MIN : FOCUS_MIN) * 60)
  const displaySec = computed(() => (remaining.value > 0 ? remaining.value : totalSec.value))
  const mm = computed(() => String(Math.floor(displaySec.value / 60)).padStart(2, '0'))
  const ss = computed(() => String(displaySec.value % 60).padStart(2, '0'))

  const phaseLabel = computed(() =>
    phase.value === 'focus' ? '专注中' : phase.value === 'break' ? '休息中' : '未开始'
  )
  const focusTip = computed(() => {
    if (!isRunning.value) return '开启番茄钟，企鹅会陪你一起专注学习'
    if (distracted.value) return '企鹅发现你走神了，快回来学习吧！'
    if (phase.value === 'break') return '休息一下，喝口水再战～'
    return '专注学习中 · 切走页面企鹅会提醒你放下手机'
  })

  const emit = (e: string) => petBus.emit(e)

  /* ---------- 计时 ---------- */
  function stopTimer() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    if (startGuard) {
      clearTimeout(startGuard)
      startGuard = null
    }
  }
  function tick() {
    remaining.value--
    if (remaining.value <= 0) {
      if (phase.value === 'focus') {
        emit('task_complete') // thumbsup 庆祝
        phase.value = 'break'
        remaining.value = BREAK_MIN * 60
      } else {
        phase.value = 'idle'
        isRunning.value = false
        stopTimer()
        emit('study_stop') // 回到 idle
      }
    }
  }
  function startTimer() {
    if (timer) return
    timer = setInterval(tick, 1000)
  }

  /* ---------- 分心检测 ---------- */
  function resumeFocus() {
    if (phase.value !== 'focus') return
    distracted.value = false
    if (leaveTimer) { clearTimeout(leaveTimer); leaveTimer = null }
    scheduleInactive()
    if (isRunning.value) emit('studying') // 回到 focus 动画
  }
  function onLeave() {
    if (phase.value === 'focus' && isRunning.value && !distracted.value) {
      if (leaveTimer) clearTimeout(leaveTimer)
      leaveTimer = setTimeout(() => {
        distracted.value = true
        emit('distraction_detected') // nowphone
      }, LEAVE_GRACE_MS)
    }
  }
  function onEnter() {
    if (leaveTimer) { clearTimeout(leaveTimer); leaveTimer = null }
    if (distracted.value) resumeFocus()
  }
  function scheduleInactive() {
    if (inactiveTimer) clearTimeout(inactiveTimer)
    inactiveTimer = setTimeout(() => {
      if (phase.value === 'focus' && isRunning.value && !document.hidden) {
        distracted.value = true
        emit('distraction_detected')
      }
    }, INACTIVE_MS)
  }
  function markActive() {
    if (distracted.value) resumeFocus()
    else scheduleInactive()
  }

  function attachWatchers() {
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', onLeave)
    window.addEventListener('focus', onEnter)
    window.addEventListener('mousemove', markActive)
    window.addEventListener('keydown', markActive)
    scheduleInactive()
  }
  function detachWatchers() {
    document.removeEventListener('visibilitychange', onVisibility)
    window.removeEventListener('blur', onLeave)
    window.removeEventListener('focus', onEnter)
    window.removeEventListener('mousemove', markActive)
    window.removeEventListener('keydown', markActive)
    if (leaveTimer) { clearTimeout(leaveTimer); leaveTimer = null }
    if (inactiveTimer) { clearTimeout(inactiveTimer); inactiveTimer = null }
  }
  function onVisibility() {
    if (document.hidden) onLeave()
    else onEnter()
  }

  /* ---------- 控制 ---------- */
  function start() {
    if (isRunning.value) return
    phase.value = 'focus'
    remaining.value = FOCUS_MIN * 60
    isRunning.value = true
    distracted.value = false
    emit('pomodoro_start') // cheer -> 自动 transitionTo focus
    startTimer()
    attachWatchers()
    // 安全补发：若开始瞬间宠物正处于随机表情/睡眠等状态而错过了 cheer，
    // 1.2s 后强制进入 focus（studying.from = "*"），保证「正在学习」必定生效
    startGuard = setTimeout(() => {
      if (isRunning.value && phase.value === 'focus') emit('studying')
      startGuard = null
    }, 1200)
  }
  function pause() {
    isRunning.value = false
    stopTimer()
    detachWatchers()
    emit('study_stop')
  }
  function reset() {
    stopTimer()
    detachWatchers()
    phase.value = 'idle'
    isRunning.value = false
    distracted.value = false
    remaining.value = 0
    emit('study_stop')
  }

  onUnmounted(() => {
    stopTimer()
    detachWatchers()
  })

  return {
    phase,
    isRunning,
    remaining,
    distracted,
    mm,
    ss,
    phaseLabel,
    focusTip,
    start,
    pause,
    reset
  }
}

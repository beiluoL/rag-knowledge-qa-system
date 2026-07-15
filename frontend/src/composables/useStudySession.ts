/**
 * useStudySession —— 轻量番茄钟 / 专注会话 · 全量学习监督事件源
 * ---------------------------------------------------------------
 * 这是桌面宠物（StudyPet）唯一的「学习信号源」，向 petBus 发射全部
 * 学习监督事件，驱动精灵图状态机。事件名即 petConfig.json 中
 * stateMachine.transitions 的 key（也是各动画的 trigger 字段）：
 *
 *   开始专注        start()           -> pomodoro_start (cheer)  + 夜间 late_night_study (coffee)
 *   进入专注        startGuard/恢复   -> studying        (focus)
 *   专注中分心     切走页/失焦/无操作 -> distraction_detected (nowphone)
 *   频繁切应用     短时间内多次返回   -> app_switching   (confused)
 *   久学未停       连续专注 ≥45 分    -> study_too_long  (sleepy)
 *   连续犯困       第二次久学         -> double_sleepy   (wash)
 *   需要喝水       每 15 分钟专注      -> drink_reminder  (drink)
 *   番茄钟完成     计时归零            -> task_complete   (thumbsup)
 *   进入休息       番茄钟结束          -> study_break     (stretch)
 *   过度学习       当日累计 ≥120 分    -> over_study      (tired)
 *   目标达成       番茄数/时长达标     -> all_goals_complete (celebrate)
 *   连续达标       连续天数达里程碑    -> streak_complete  (proud)
 *   日终未达标     离开页且未达标      -> goal_missed     (sad)
 *   停止/休息结束   pause/reset/休息完 -> study_stop      (idle)
 *
 * 每日统计（localStorage）支撑目标/连续/过度等需要跨番茄钟累计的信号。
 */
import { ref, computed, onUnmounted } from 'vue'
import { petBus } from '@/components/petBus'
// @ts-ignore - JSON 资源由 Vite 直接提供
import petConfig from '@/components/petConfig.json'

const monitor: any = (petConfig as any).studyMonitor || {}
const FOCUS_MIN = monitor.pomodoroDuration ?? 25
const BREAK_MIN = monitor.breakDuration ?? 5
const SLEEPY_MIN = monitor.sleepyWarningMinutes ?? 45
const GOAL = monitor.dailyGoalCheck || { hours: 4, pomodoros: 8 }

// 离开多久算「分心」（给一点宽限，避免误报）
const LEAVE_GRACE_MS = 8000
// 专注时多久没有任何鼠标/键盘操作算「走神」
const INACTIVE_MS = 60000
// 每专注多少分钟提醒一次喝水
const DRINK_INTERVAL_MS = 15 * 60 * 1000
// 当日累计专注多少分钟算「过度学习」
const OVER_STUDY_MIN = 120
// 连续学习天数里程碑（达成即 proud）
const STREAK_MILESTONES = [3, 7, 30]

const STATS_KEY = 'pet-study-stats'

/* ---------------- 每日统计 ---------------- */
interface DailyStats {
  date: string
  focusMinutes: number
  pomodoros: number
  streak: number
  lastStudyDate: string
  goalMet: boolean
  overStudyFired: boolean
  goalMissedFired: boolean
  milestonesFired: number[]
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}
function dayStr(offset = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
function freshStats(): DailyStats {
  return {
    date: dayStr(),
    focusMinutes: 0,
    pomodoros: 0,
    streak: 0,
    lastStudyDate: '',
    goalMet: false,
    overStudyFired: false,
    goalMissedFired: false,
    milestonesFired: []
  }
}
function loadStats(): DailyStats {
  try {
    const raw = localStorage.getItem(STATS_KEY)
    if (raw) {
      const s = JSON.parse(raw) as DailyStats
      if (s.date === dayStr()) return s
      // 新的一天：清零当日累计，保留 streak / lastStudyDate
      return { ...s, date: dayStr(), focusMinutes: 0, pomodoros: 0, goalMet: false, overStudyFired: false, goalMissedFired: false, milestonesFired: [] }
    }
  } catch {
    /* ignore */
  }
  return freshStats()
}

export function useStudySession() {
  const phase = ref<'idle' | 'focus' | 'break'>('idle')
  const isRunning = ref(false)
  const remaining = ref(0)
  const distracted = ref(false)
  const stats = ref<DailyStats>(loadStats())

  let timer: ReturnType<typeof setInterval> | null = null
  let leaveTimer: ReturnType<typeof setTimeout> | null = null
  let inactiveTimer: ReturnType<typeof setTimeout> | null = null
  let startGuard: ReturnType<typeof setTimeout> | null = null
  let drinkTimer: ReturnType<typeof setInterval> | null = null

  // 连续专注分钟 / 久学计数（用于 sleepy / wash 循环）
  let continuousFocusMin = 0
  let sleepyCount = 0
  // 秒累计器（每 60 秒折算 1 分钟专注）
  let sessionSecElapsed = 0
  // 频繁切应用检测
  let switchTimes: number[] = []

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

  /* ---------------- 给 UI 展示的当日进度 ---------------- */
  const focusMinToday = computed(() => stats.value.focusMinutes)
  const pomodoroToday = computed(() => stats.value.pomodoros)
  const streakToday = computed(() => stats.value.streak)
  const goalText = computed(() => `目标：${GOAL.pomodoros} 番茄 / ${GOAL.hours} 小时`)
  const goalProgressPct = computed(() => {
    const byPomo = stats.value.pomodoros / GOAL.pomodoros
    const byHour = stats.value.focusMinutes / (GOAL.hours * 60)
    return Math.min(100, Math.round(Math.max(byPomo, byHour) * 100))
  })

  const emit = (e: string) => petBus.emit(e)
  const persist = () => {
    try {
      localStorage.setItem(STATS_KEY, JSON.stringify(stats.value))
    } catch {
      /* ignore */
    }
  }

  /* ---------------- 计时 ---------------- */
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
  function stopDrinkTimer() {
    if (drinkTimer) {
      clearInterval(drinkTimer)
      drinkTimer = null
    }
  }
  function tick() {
    remaining.value--
    sessionSecElapsed++
    if (sessionSecElapsed >= 60) {
      sessionSecElapsed -= 60
      addFocusMinute()
    }
    if (remaining.value <= 0) {
      if (phase.value === 'focus') {
        onPomodoroComplete()
        emit('task_complete') // thumbsup 庆祝
        phase.value = 'break'
        remaining.value = BREAK_MIN * 60
        emit('study_break') // stretch 拉伸
      } else {
        phase.value = 'idle'
        isRunning.value = false
        stopAllTimers()
        detachWatchers()
        emit('study_stop') // 回到 idle
        checkGoalMissed()
      }
    }
  }
  function startTimer() {
    if (timer) return
    timer = setInterval(tick, 1000)
  }
  function stopAllTimers() {
    stopTimer()
    stopDrinkTimer()
  }

  /* ---------------- 专注分钟累计 -> 各类监督信号 ---------------- */
  function addFocusMinute() {
    const s = stats.value
    s.focusMinutes += 1

    if (phase.value === 'focus') {
      continuousFocusMin++
      if (continuousFocusMin >= SLEEPY_MIN) {
        continuousFocusMin = 0
        sleepyCount++
        if (sleepyCount % 2 === 1) emit('study_too_long') // sleepy 犯困
        else emit('double_sleepy') // wash 洗脸清醒
      }
    }
    // 过度学习（当日累计）
    if (!s.overStudyFired && s.focusMinutes >= OVER_STUDY_MIN) {
      s.overStudyFired = true
      emit('over_study') // tired 疲惫
    }
    persist()
  }

  /* ---------------- 番茄钟完成 -> 目标 / 连续 ---------------- */
  function onPomodoroComplete() {
    const s = stats.value
    s.pomodoros += 1
    // 连续学习天数（首次完成当日番茄时更新）
    if (s.lastStudyDate !== dayStr()) {
      if (s.lastStudyDate === dayStr(-1)) s.streak += 1
      else s.streak = 1
      s.lastStudyDate = dayStr()
    }
    // 目标达成
    if (!s.goalMet && (s.pomodoros >= GOAL.pomodoros || s.focusMinutes >= GOAL.hours * 60)) {
      s.goalMet = true
      emit('all_goals_complete') // celebrate 终极庆祝
    }
    // 连续达标里程碑
    for (const m of STREAK_MILESTONES) {
      if (s.streak === m && !s.milestonesFired.includes(m)) {
        s.milestonesFired.push(m)
        emit('streak_complete') // proud 自豪
      }
    }
    persist()
  }

  /* ---------------- 分心 / 频繁切应用检测 ---------------- */
  function resumeFocus() {
    if (phase.value !== 'focus') return
    distracted.value = false
    if (leaveTimer) {
      clearTimeout(leaveTimer)
      leaveTimer = null
    }
    scheduleInactive()
    if (isRunning.value) emit('studying') // 回到 focus 动画
  }
  function onLeave() {
    if (phase.value === 'focus' && isRunning.value && !distracted.value) {
      if (leaveTimer) clearTimeout(leaveTimer)
      leaveTimer = setTimeout(() => {
        distracted.value = true
        emit('distraction_detected') // nowphone 放下手机
      }, LEAVE_GRACE_MS)
    }
  }
  function onEnter() {
    if (leaveTimer) {
      clearTimeout(leaveTimer)
      leaveTimer = null
    }
    // 频繁切应用 -> confused
    const now = Date.now()
    switchTimes.push(now)
    switchTimes = switchTimes.filter((t) => now - t < 60000)
    if (switchTimes.length >= 4) {
      switchTimes = []
      emit('app_switching')
    }
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
    if (leaveTimer) {
      clearTimeout(leaveTimer)
      leaveTimer = null
    }
    if (inactiveTimer) {
      clearTimeout(inactiveTimer)
      inactiveTimer = null
    }
  }
  function onVisibility() {
    if (document.hidden) onLeave()
    else onEnter()
  }

  function isNight() {
    const h = new Date().getHours()
    return h >= (monitor.nightModeStartHour ?? 23) || h < (monitor.nightModeEndHour ?? 7)
  }

  /* ---------------- 控制 ---------------- */
  function start() {
    if (isRunning.value) return
    const fresh = remaining.value <= 0
    isRunning.value = true
    distracted.value = false
    if (fresh) {
      phase.value = 'focus'
      remaining.value = FOCUS_MIN * 60
      continuousFocusMin = 0
      sleepyCount = 0
      sessionSecElapsed = 0
      emit('pomodoro_start') // cheer -> 自动 transitionTo focus
    }
    startTimer()
    attachWatchers()
    startDrinkTimer()
    if (phase.value === 'focus') {
      if (isNight()) emit('late_night_study') // coffee（深夜学习）
      // 安全补发：若开始瞬间宠物正处于随机表情/睡眠等状态而错过了 cheer，
      // 1.2s 后强制进入 focus（studying.from = "*"），保证「正在学习」必定生效
      startGuard = setTimeout(() => {
        if (isRunning.value && phase.value === 'focus') emit('studying')
        startGuard = null
      }, 1200)
    }
  }
  function startDrinkTimer() {
    if (drinkTimer) return
    drinkTimer = setInterval(() => {
      if (phase.value === 'focus' && isRunning.value) emit('drink_reminder') // drink 喝水
    }, DRINK_INTERVAL_MS)
  }
  function pause() {
    if (!isRunning.value) return
    // 真正暂停：冻结计时但保留进度，不发射 study_stop（恢复后继续）
    isRunning.value = false
    stopAllTimers()
    detachWatchers()
  }
  function reset() {
    stopAllTimers()
    detachWatchers()
    phase.value = 'idle'
    isRunning.value = false
    distracted.value = false
    remaining.value = 0
    continuousFocusMin = 0
    sleepyCount = 0
    sessionSecElapsed = 0
    emit('study_stop')
    checkGoalMissed()
  }

  /* ---------------- 日终未达标 ---------------- */
  function checkGoalMissed() {
    const s = stats.value
    const met = s.goalMet || s.pomodoros >= GOAL.pomodoros || s.focusMinutes >= GOAL.hours * 60
    const late = new Date().getHours() >= 21
    if (!met && late && !s.goalMissedFired) {
      s.goalMissedFired = true
      persist()
      emit('goal_missed') // sad 不达标失望
    }
  }

  /* ---------------- 调试 / 工具 ---------------- */
  function debugTrigger(event: string) {
    emit(event)
  }
  function clearToday() {
    stats.value = freshStats()
    persist()
  }

  // 页面卸载时检查「日终未达标」
  if (typeof window !== 'undefined') {
    window.addEventListener('beforeunload', checkGoalMissed)
  }

  onUnmounted(() => {
    stopAllTimers()
    detachWatchers()
    if (typeof window !== 'undefined') {
      window.removeEventListener('beforeunload', checkGoalMissed)
    }
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
    reset,
    // 当日进度（供 UI 展示）
    stats,
    focusMinToday,
    pomodoroToday,
    streakToday,
    goalText,
    goalProgressPct,
    // 调试
    debugTrigger,
    clearToday
  }
}

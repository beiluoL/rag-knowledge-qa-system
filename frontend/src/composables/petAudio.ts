import { ref } from 'vue'

/**
 * 宠物音效（Web Audio 合成，零音频素材文件）
 * - 首次用户交互后才会真正出声（浏览器自动播放策略）
 * - 静音开关持久化
 */

const KEY = 'pet-audio-v1'

const enabled = ref(localStorage.getItem(KEY) !== 'off')
let ctx: AudioContext | null = null

function ac(): AudioContext | null {
  try {
    if (!ctx) {
      const Ctor = window.AudioContext || (window as any).webkitAudioContext
      if (!Ctor) return null
      ctx = new Ctor()
    }
    if (ctx.state === 'suspended') ctx.resume()
    return ctx
  } catch {
    return null
  }
}

function tone(
  freq: number,
  dur: number,
  type: OscillatorType = 'sine',
  vol = 0.14,
  when = 0
) {
  if (!enabled.value) return
  const c = ac()
  if (!c) return
  const o = c.createOscillator()
  const g = c.createGain()
  o.type = type
  o.frequency.value = freq
  const t = c.currentTime + when
  g.gain.setValueAtTime(0.0001, t)
  g.gain.exponentialRampToValueAtTime(vol, t + 0.012)
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur)
  o.connect(g).connect(c.destination)
  o.start(t)
  o.stop(t + dur + 0.03)
}

export function usePetAudio() {
  const blip = () => tone(680, 0.08, 'triangle')
  const click = () => tone(520, 0.06, 'sine', 0.1)
  const cheer = () =>
    [523, 659, 784, 1046].forEach((f, i) => tone(f, 0.18, 'triangle', 0.16, i * 0.09))
  const munch = () => {
    tone(180, 0.06, 'square', 0.1)
    setTimeout(() => tone(140, 0.08, 'square', 0.09), 70)
  }
  const levelup = () =>
    [523, 659, 784, 1046, 1318].forEach((f, i) => tone(f, 0.2, 'sine', 0.16, i * 0.08))
  const sad = () =>
    [392, 330, 262].forEach((f, i) => tone(f, 0.26, 'sine', 0.13, i * 0.12))
  const surprise = () => {
    tone(900, 0.06, 'sawtooth', 0.1)
    setTimeout(() => tone(420, 0.1, 'sawtooth', 0.1), 60)
  }
  const poke = () => tone(300, 0.05, 'square', 0.1)
  const toggle = () => {
    enabled.value = !enabled.value
    try {
      localStorage.setItem(KEY, enabled.value ? 'on' : 'off')
    } catch {
      /* ignore */
    }
    if (enabled.value) blip()
  }
  return { enabled, blip, click, cheer, munch, levelup, sad, surprise, poke, toggle }
}

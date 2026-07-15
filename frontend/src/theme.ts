/**
 * 主题管理器：支持 light / dark / system 三态，
 * 持久化到 localStorage，并在 system 模式下跟随系统配色变化。
 */
export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'ragkb-theme'

function systemPrefersDark(): boolean {
  return typeof window !== 'undefined' &&
    window.matchMedia &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
}

function resolve(mode: ThemeMode): 'light' | 'dark' {
  if (mode === 'system') return systemPrefersDark() ? 'dark' : 'light'
  return mode
}

function apply(resolved: 'light' | 'dark') {
  const el = document.documentElement
  el.setAttribute('data-theme', resolved)
  el.classList.toggle('dark', resolved === 'dark')
}

let mediaBound = false

/** 应用启动时调用：读取持久化主题并应用，同时监听系统配色变化（仅 system 模式生效） */
export function initTheme() {
  const saved = (localStorage.getItem(STORAGE_KEY) as ThemeMode | null) || 'system'
  apply(resolve(saved))

  if (typeof window !== 'undefined' && window.matchMedia && !mediaBound) {
    mediaBound = true
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (e: MediaQueryListEvent) => {
      const current = (localStorage.getItem(STORAGE_KEY) as ThemeMode | null) || 'system'
      if (current === 'system') apply(e.matches ? 'dark' : 'light')
    }
    // 兼容旧版 Safari 的 addListener
    if (mq.addEventListener) mq.addEventListener('change', onChange)
    else if (mq.addListener) mq.addListener(onChange as any)
  }
}

export function getTheme(): ThemeMode {
  return (localStorage.getItem(STORAGE_KEY) as ThemeMode | null) || 'system'
}

export function setTheme(mode: ThemeMode) {
  localStorage.setItem(STORAGE_KEY, mode)
  apply(resolve(mode))
}

export function isDark(): boolean {
  return document.documentElement.getAttribute('data-theme') === 'dark'
}

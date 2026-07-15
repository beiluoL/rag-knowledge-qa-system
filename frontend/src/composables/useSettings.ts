import { ref } from 'vue'

/**
 * 全局设置弹窗状态（模块级单例，任意组件 import 共享同一个 visible）。
 * 由 ChatView 用户菜单「系统设置」打开，SettingsModal 全局挂载于 App.vue。
 */
const visible = ref(false)

export function useSettings() {
  return {
    visible,
    open: () => { visible.value = true },
    close: () => { visible.value = false }
  }
}

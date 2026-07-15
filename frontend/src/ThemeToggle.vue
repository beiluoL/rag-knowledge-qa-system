<template>
  <div
    class="theme-toggle"
    :class="{ 'theme-toggle--floating': floating }"
    role="group"
    aria-label="主题切换"
  >
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="theme-opt"
      :class="{ active: current === opt.value }"
      :aria-pressed="current === opt.value"
      :aria-label="opt.label"
      :title="opt.label"
      @click="select(opt.value)"
    >
      <component :is="opt.icon" class="icon-md" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Sun, Moon, Monitor } from 'lucide-vue-next'
import { getTheme, setTheme, type ThemeMode } from '@/theme'

defineProps<{ floating?: boolean }>()

const options = [
  { value: 'light' as ThemeMode, label: '浅色', icon: Sun },
  { value: 'dark' as ThemeMode, label: '深色', icon: Moon },
  { value: 'system' as ThemeMode, label: '跟随系统', icon: Monitor }
]

const current = ref<ThemeMode>('system')

onMounted(() => {
  current.value = getTheme()
})

function select(mode: ThemeMode) {
  current.value = mode
  setTheme(mode)
}
</script>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-full);
  box-shadow: var(--shadow-sm);
}

.theme-opt {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-muted);
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.theme-opt:hover {
  background: var(--surface-2);
  color: var(--text-secondary);
}
.theme-opt.active {
  background: var(--primary-600);
  color: #fff;
  box-shadow: var(--shadow-colored);
}
.theme-opt:focus-visible {
  outline: 2px solid var(--primary-400);
  outline-offset: 2px;
}

/* 全局浮动模式：固定在左下角，所有页面可用 */
.theme-toggle--floating {
  position: fixed;
  left: 16px;
  bottom: 16px;
  z-index: var(--z-tooltip);
}
</style>

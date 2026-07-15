<template>
  <el-dialog
    v-model="visible"
    class="settings-dialog"
    :width="'min(880px, 94vw)'"
    top="8vh"
    append-to-body
    :close-on-click-modal="true"
    @open="syncTheme"
  >
    <template #header>
      <div class="settings-header">
        <span class="settings-header-icon"><Settings class="icon-md" /></span>
        <div>
          <h2 class="settings-title">设置</h2>
          <p class="settings-subtitle">个性化你的使用体验</p>
        </div>
      </div>
    </template>

    <div class="settings-body">
      <!-- 左侧导航 -->
      <nav class="settings-tabs" aria-label="设置分类">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="settings-tab"
          :class="{ active: active === tab.key }"
          :aria-current="active === tab.key"
          @click="active = tab.key"
        >
          <component :is="tab.icon" class="icon-sm" />
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <!-- 右侧内容 -->
      <div class="settings-content">
        <!-- 通用 -->
        <section v-if="active === 'general'" class="settings-pane">
          <h3 class="pane-title">通用</h3>
          <p class="pane-desc">通用设置将在后续版本开放（如默认知识库、消息历史保留等）。</p>
          <div class="pane-placeholder">
            <SlidersHorizontal class="placeholder-icon" />
            <span>敬请期待</span>
          </div>
        </section>

        <!-- 外观 -->
        <section v-else-if="active === 'appearance'" class="settings-pane">
          <h3 class="pane-title">外观</h3>
          <p class="pane-desc">选择界面主题，设置将立即生效并自动保存。</p>

          <div class="theme-grid" role="radiogroup" aria-label="主题选择">
            <button
              v-for="opt in themeOptions"
              :key="opt.value"
              type="button"
              class="theme-card"
              :class="{ active: current === opt.value }"
              role="radio"
              :aria-checked="current === opt.value"
              @click="selectTheme(opt.value)"
            >
              <div class="theme-preview" :class="'preview-' + opt.value" aria-hidden="true">
                <div class="pv-bar"></div>
                <div class="pv-row pv-row-1"></div>
                <div class="pv-row pv-row-2"></div>
                <div class="pv-row pv-row-3"></div>
                <div class="pv-chip"></div>
              </div>
              <div class="theme-card-label">
                <component :is="opt.icon" class="icon-sm" />
                <span>{{ opt.label }}</span>
              </div>
              <span v-if="current === opt.value" class="theme-check">
                <Check class="icon-sm" />
              </span>
            </button>
          </div>

          <div class="lang-row">
            <span class="lang-label">语言</span>
            <el-select v-model="language" size="default" class="lang-select" placeholder="选择语言">
              <el-option label="简体中文" value="zh-CN" />
              <el-option label="English" value="en-US" :disabled="true" />
            </el-select>
          </div>
        </section>

        <!-- 关于 -->
        <section v-else-if="active === 'about'" class="settings-pane">
          <h3 class="pane-title">关于</h3>
          <div class="about-list">
            <div class="about-item">
              <span class="about-key">产品</span>
              <span class="about-value">智能知识库 RAG 系统</span>
            </div>
            <div class="about-item">
              <span class="about-key">版本</span>
              <span class="about-value">v1.0.0</span>
            </div>
            <div class="about-item">
              <span class="about-key">技术栈</span>
              <span class="about-value">Vue 3 · Spring Boot · PostgreSQL / pgvector</span>
            </div>
          </div>
          <p class="pane-desc about-foot">
            基于检索增强生成（RAG）的企业级知识问答平台，支持混合检索与过程可视化。
          </p>
        </section>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useSettings } from '@/composables/useSettings'
import { getTheme, setTheme, type ThemeMode } from '@/theme'
import {
  Settings, SlidersHorizontal, Palette, Info,
  Sun, Moon, Monitor, Check
} from 'lucide-vue-next'

const { visible } = useSettings()

const tabs = [
  { key: 'general', label: '通用', icon: SlidersHorizontal },
  { key: 'appearance', label: '外观', icon: Palette },
  { key: 'about', label: '关于', icon: Info }
]
const active = ref<'general' | 'appearance' | 'about'>('appearance')

const themeOptions = [
  { value: 'light' as ThemeMode, label: '浅色', icon: Sun },
  { value: 'dark' as ThemeMode, label: '深色', icon: Moon },
  { value: 'system' as ThemeMode, label: '跟随系统', icon: Monitor }
]

const current = ref<ThemeMode>('system')
const language = ref('zh-CN')

function syncTheme() {
  current.value = getTheme()
}

function selectTheme(mode: ThemeMode) {
  current.value = mode
  setTheme(mode)
}
</script>

<style scoped>
.settings-header {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.settings-header-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--primary-50);
  color: var(--primary-600);
}
.settings-title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
}
.settings-subtitle {
  margin: 2px 0 0;
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.settings-body {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: var(--space-xl);
  min-height: 360px;
}

.settings-tabs {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-right: 1px solid var(--border);
  padding-right: var(--space-md);
}
.settings-tab {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 12px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 0.875rem;
  text-align: left;
  transition: background var(--duration-fast), color var(--duration-fast);
}
.settings-tab:hover {
  background: var(--surface-2);
  color: var(--text-primary);
}
.settings-tab.active {
  background: var(--primary-50);
  color: var(--primary-700);
  font-weight: 600;
}
.settings-tab:focus-visible {
  outline: 2px solid var(--primary-400);
  outline-offset: 2px;
}

.settings-content { min-width: 0; }
.pane-title {
  margin: 0 0 4px;
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
}
.pane-desc {
  margin: 0 0 var(--space-lg);
  font-size: 0.8125rem;
  color: var(--text-secondary);
  line-height: 1.6;
}
.pane-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-2xl) 0;
  color: var(--text-muted);
}
.placeholder-icon { width: 32px; height: 32px; }

/* 主题卡片 */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-md);
}
.theme-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-sm);
  border: 1.5px solid var(--border);
  background: var(--surface);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: border-color var(--duration-fast), box-shadow var(--duration-fast), transform var(--duration-fast);
}
.theme-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.theme-card.active {
  border-color: var(--primary-500);
  box-shadow: 0 0 0 3px var(--primary-100);
}
.theme-card:focus-visible {
  outline: 2px solid var(--primary-400);
  outline-offset: 2px;
}
.theme-check {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary-600);
  color: #fff;
}
.theme-card-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
}

/* 预览缩略图（纯 CSS 模拟界面） */
.theme-preview {
  height: 84px;
  border-radius: var(--radius-md);
  padding: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid rgba(0, 0, 0, 0.06);
}
.preview-light { background: #f8fafc; }
.preview-dark { background: #0b1120; }
.preview-system { background: #f8fafc; }
.pv-bar {
  height: 10px;
  width: 60%;
  border-radius: 4px;
}
.pv-row {
  height: 6px;
  border-radius: 3px;
  opacity: 0.7;
}
.pv-row-1 { width: 90%; }
.pv-row-2 { width: 75%; }
.pv-row-3 { width: 82%; }
.pv-chip {
  margin-top: auto;
  width: 40px;
  height: 16px;
  border-radius: 8px;
}
.preview-light .pv-bar { background: #2563eb; }
.preview-light .pv-row { background: #cbd5e1; }
.preview-light .pv-chip { background: #93c5fd; }
.preview-dark .pv-bar { background: #60a5fa; }
.preview-dark .pv-row { background: #334155; }
.preview-dark .pv-chip { background: #1d4ed8; }
/* 跟随系统：左亮右暗渐变，表达「跟随系统」语义 */
.preview-system {
  background: linear-gradient(100deg, #f8fafc 0%, #f8fafc 49%, #0b1120 51%, #0b1120 100%);
}
.preview-system .pv-bar { background: linear-gradient(90deg, #2563eb, #60a5fa); }
.preview-system .pv-row { background: linear-gradient(90deg, #cbd5e1, #334155); }
.preview-system .pv-chip { background: linear-gradient(90deg, #93c5fd, #1d4ed8); }

/* 语言 */
.lang-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-top: var(--space-xl);
}
.lang-label {
  font-size: 0.875rem;
  color: var(--text-primary);
  font-weight: 500;
}
.lang-select { width: 200px; }

/* 关于 */
.about-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.about-item {
  display: flex;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-sm) 0;
  border-bottom: 1px solid var(--border);
  font-size: 0.875rem;
}
.about-key { color: var(--text-secondary); }
.about-value { color: var(--text-primary); font-weight: 500; }
.about-foot { margin-top: var(--space-lg); }

@media (max-width: 640px) {
  .settings-body {
    grid-template-columns: 1fr;
    gap: var(--space-md);
  }
  .settings-tabs {
    flex-direction: row;
    border-right: none;
    border-bottom: 1px solid var(--border);
    padding-right: 0;
    padding-bottom: var(--space-sm);
    overflow-x: auto;
  }
  .theme-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>

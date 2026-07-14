<template>
  <main class="not-found">
    <section class="not-found-card" aria-labelledby="nf-title">
      <div class="not-found-icon" aria-hidden="true">
        <el-icon class="icon-xl"><Compass /></el-icon>
      </div>
      <p class="not-found-code">404</p>
      <h1 id="nf-title" class="not-found-title">页面不存在</h1>
      <p class="not-found-subtitle">抱歉，你访问的页面已被移除或从未存在。</p>
      <el-button type="primary" size="large" @click="$router.push('/chat')">
        返回首页
      </el-button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Compass } from 'lucide-vue-next'
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  padding: var(--space-2xl);
  box-sizing: border-box;
}

.not-found-card {
  width: 100%;
  max-width: 420px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-5xl) var(--space-3xl);
  box-shadow: var(--shadow-sm);
  text-align: center;
}

.not-found-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto var(--space-xl);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-50);
  color: var(--primary-600);
}

.not-found-code {
  font-size: 3rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
  margin: 0 0 var(--space-sm);
}

.not-found-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--space-xs);
}

.not-found-subtitle {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin: 0 0 var(--space-2xl);
  line-height: 1.6;
}

.not-found-card :deep(.el-button) {
  min-width: 160px;
}

@media (max-width: 480px) {
  .not-found-card {
    padding: var(--space-3xl) var(--space-2xl);
  }
  .not-found-card :deep(.el-button) {
    width: 100%;
  }
}
</style>

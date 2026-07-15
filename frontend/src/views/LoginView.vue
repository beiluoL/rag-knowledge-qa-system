<template>
  <main class="login">
    <section class="login-card" aria-labelledby="login-title">
      <header class="brand">
        <BookOpen class="brand-logo" />
        <span class="brand-name">智能知识库</span>
      </header>

      <h1 id="login-title" class="title">登录</h1>
      <p class="subtitle">登录以继续使用知识库与学习系统</p>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            aria-label="用户名"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            aria-label="密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            class="submit"
            type="primary"
            :loading="loading"
            @click="handleLogin"
          >登 录</el-button>
        </el-form-item>
      </el-form>

      <div class="hint" role="note">
        <el-icon class="hint-icon"><Info /></el-icon>
        <span class="hint-text">默认管理员账号 <b>admin</b> / <b>123456</b></span>
        <button
          class="hint-copy"
          type="button"
          :title="copied ? '已复制' : '复制账号'"
          :aria-label="copied ? '已复制' : '复制账号'"
          @click="copyCredentials"
        >
          <el-icon><Copy /></el-icon>
        </button>
      </div>
      <p class="alt">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </p>
    </section>
    <p class="footer">© 2026 智能知识库 · RAG 驱动的企业级知识平台</p>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Info, BookOpen, Copy } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const copied = ref(false)
async function copyCredentials() {
  const text = 'admin / 123456'
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    ElMessage.success('已复制默认账号')
    setTimeout(() => (copied.value = false), 1800)
  } catch {
    ElMessage.error('复制失败')
  }
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    router.push('/chat')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2xl);
  background: var(--bg);
  padding: var(--space-2xl);
}

.login-card {
  width: 100%;
  max-width: 440px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  padding: var(--space-5xl) var(--space-4xl);
  box-shadow: var(--shadow-md);
  transition: box-shadow var(--duration-base);

  &:hover {
    box-shadow: var(--shadow-lg);
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-3xl);
}

.brand-logo {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--primary-50);
  padding: 6px;
  color: var(--primary-600);
  box-shadow: var(--shadow-sm);
}

.brand-name {
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0.2px;
}

.title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 var(--space-xs);
  line-height: 1.3;
}

.subtitle {
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin: 0 0 var(--space-3xl);
}

.submit {
  width: 100%;
  height: 48px;
  font-size: 0.9375rem;
  font-weight: 600;
  border-radius: var(--radius-md);
}

.hint {
  margin-top: var(--space-lg);
  font-size: 0.8125rem;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  background: var(--primary-50);
  border: 1px solid var(--primary-100);
  padding: var(--space-md) var(--space-lg);
  border-radius: var(--radius-md);

  :deep(.el-icon) {
    color: var(--primary-600);
    flex-shrink: 0;
  }

  b {
    color: var(--text-primary);
  }
}

.hint-icon {
  flex-shrink: 0;
}

.hint-text {
  flex: 1;
  min-width: 0;
}

.hint-copy {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--primary-600);
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast), transform var(--duration-fast);
}
.hint-copy:hover {
  background: var(--primary-100);
}
.hint-copy:active {
  transform: scale(0.92);
}
.hint-copy:focus-visible {
  outline: 2px solid var(--primary-400);
  outline-offset: 1px;
}

.alt {
  text-align: center;
  margin-top: var(--space-lg);
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.alt a {
  color: var(--primary-600);
  font-weight: 500;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

.footer {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin: 0;
  text-align: center;
}

@media (max-width: 768px) {
  .login-card {
    padding: var(--space-3xl) var(--space-2xl);
  }
}

@media (max-width: 480px) {
  .login {
    padding: var(--space-lg);
  }
  .login-card {
    padding: var(--space-2xl) var(--space-lg);
  }
}
</style>

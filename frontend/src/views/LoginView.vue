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
        <el-icon><Info /></el-icon>
        <span>默认管理员账号：<b>admin</b> / <b>123456</b></span>
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
import { User, Lock, Info, BookOpen } from 'lucide-vue-next'
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
  max-width: 400px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-4xl) var(--space-3xl);
  box-shadow: var(--shadow-sm);
  transition: box-shadow var(--duration-base);

  &:hover {
    box-shadow: var(--shadow-md);
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

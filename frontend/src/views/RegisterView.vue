<template>
  <main class="register">
    <section class="register-card" aria-labelledby="register-title">
      <header class="brand">
        <BookOpen class="brand-logo" />
        <span class="brand-name">智能知识库</span>
      </header>

      <h1 id="register-title" class="title">创建账号</h1>
      <p class="subtitle">填写以下信息，开启你的知识库之旅</p>

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
            placeholder="密码（至少4位）"
            aria-label="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            aria-label="确认密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            class="submit"
            type="primary"
            :loading="loading"
            @click="handleRegister"
          >注 册</el-button>
        </el-form-item>
      </el-form>

      <p class="alt">
        已有账号？<router-link to="/login">立即登录</router-link>
      </p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, BookOpen } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 4, message: '密码至少4位', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.register({ username: form.username, password: form.password })
    ElMessage.success('注册成功')
    router.push('/chat')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  padding: var(--space-2xl);
  box-sizing: border-box;
}

.register-card {
  width: 100%;
  max-width: 440px;
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

@media (max-width: 768px) {
  .register-card {
    padding: var(--space-3xl) var(--space-2xl);
  }
}

@media (max-width: 480px) {
  .register {
    padding: var(--space-lg);
  }
  .register-card {
    padding: var(--space-2xl) var(--space-lg);
  }
}
</style>

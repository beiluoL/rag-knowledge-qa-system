<template>
  <div class="profile-page">
    <div class="profile-header">
      <div>
        <h1 class="page-title">个人中心</h1>
        <p class="page-subtitle">管理你的账号资料与安全设置</p>
      </div>
      <el-button :icon="ArrowLeft" @click="router.push('/chat')">返回对话</el-button>
    </div>

    <el-card class="profile-card">
      <template #header><h2 class="profile-card-title">账号信息</h2></template>

      <!-- 头像区 -->
      <div class="avatar-section">
        <div class="avatar-wrap" @click="triggerAvatarInput" role="button" tabindex="0"
             aria-label="更换头像" @keydown.enter="triggerAvatarInput">
          <el-avatar :size="96" :src="avatarDisplaySrc" class="avatar-img">
            <User />
          </el-avatar>
          <div class="avatar-overlay">
            <el-icon><Camera /></el-icon>
            <span>更换头像</span>
          </div>
        </div>
        <input ref="fileInputRef" type="file" accept="image/png,image/jpeg,image/webp,image/gif"
               hidden @change="onAvatarChange" />
        <p class="avatar-tip">支持 PNG / JPG / WEBP / GIF，大小 ≤ 2MB</p>
      </div>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-if="userInfo.role === 'ADMIN'" class="role-badge">管理员</el-tag>
          <el-tag v-else class="role-badge role-user">{{ userInfo.role }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ formatDateTime(userInfo.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 用户资料编辑区域 -->
      <el-divider />
      <h3 class="section-h">编辑资料</h3>
      <el-form :model="profileForm" label-width="100px" style="max-width:400px">
        <el-form-item label="昵称">
          <el-input v-model="profileForm.nickname" placeholder="输入昵称（可选）" maxlength="50" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="profileForm.email" placeholder="输入邮箱（可选）" maxlength="100" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="profileSaving" @click="handleUpdateProfile">保存资料</el-button>
        </el-form-item>
      </el-form>

      <!-- 修改密码区域 -->
      <el-divider />
      <h3 class="section-h">修改密码</h3>
      <el-form :model="passwordForm" :rules="rules" ref="formRef" label-width="100px" style="max-width:400px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleChangePassword">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { ArrowLeft, User, Camera } from 'lucide-vue-next'
import { getUserInfo, changePassword, updateProfile, uploadAvatar, resolveFileUrl } from '@/api/user'

const router = useRouter()
const authStore = useAuthStore()
const saving = ref(false)
const profileSaving = ref(false)
const formRef = ref()
const fileInputRef = ref<HTMLInputElement>()
const uploadingAvatar = ref(false)
const userInfo = reactive({ username: '', email: '', nickname: '', avatar: '', role: '', createdAt: '' })

/** 头像完整可访问 URL */
const avatarDisplaySrc = computed(() => resolveFileUrl(userInfo.avatar))

function triggerAvatarInput() {
  fileInputRef.value?.click()
}

async function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // 允许重复选择同一文件
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像图片不能超过 2MB')
    return
  }
  uploadingAvatar.value = true
  try {
    const { data } = await uploadAvatar(file)
    userInfo.avatar = data.avatar
    if (authStore.user) authStore.user.avatar = data.avatar
    ElMessage.success('头像已更新')
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '头像上传失败')
  } finally {
    uploadingAvatar.value = false
  }
}

/** 资料编辑表单 */
const profileForm = reactive({ nickname: '', email: '' })

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== passwordForm.newPassword) callback(new Error('两次密码不一致'))
  else callback()
}
const rules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 4, message: '密码至少4位', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

/** 格式化注册时间：ISO 字符串 → YYYY-MM-DD HH:mm */
function formatDateTime(iso?: string) {
  if (!iso) return '-'
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function handleChangePassword() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    localStorage.clear()
    router.push('/login')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '修改失败')
  } finally {
    saving.value = false
  }
}

/** 保存资料修改（昵称、邮箱） */
async function handleUpdateProfile() {
  profileSaving.value = true
  try {
    const { data } = await updateProfile({
      nickname: profileForm.nickname || undefined,
      email: profileForm.email || undefined
    })
    Object.assign(userInfo, data)
    ElMessage.success('资料更新成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '更新失败')
  } finally {
    profileSaving.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await getUserInfo()
    Object.assign(userInfo, data)
    // 将后端返回的资料填入编辑表单
    profileForm.nickname = data.nickname || ''
    profileForm.email = data.email || ''
  } catch { /* ignore */ }
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: var(--bg);
  padding: var(--space-3xl) var(--space-2xl);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.profile-header {
  width: 100%;
  max-width: 760px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--space-3xl);
  flex-wrap: wrap;
}
.profile-card {
  width: 100%;
  max-width: 760px;
}
.profile-card-title { font-size: 1.0625rem; font-weight: 700; color: var(--text-primary); }

/* 头像区 */
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: var(--space-2xl);
}
.avatar-wrap {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  cursor: pointer;
  outline: none;
  transition: transform var(--duration-fast);
}
.avatar-wrap:hover { transform: scale(1.03); }
.avatar-wrap:focus-visible { box-shadow: 0 0 0 3px var(--primary-200); }
.avatar-img {
  border: 3px solid var(--surface);
  box-shadow: var(--shadow-md);
  background: var(--surface-3);
}
.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.55);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 0.75rem;
  opacity: 0;
  transition: opacity var(--duration-fast);
}
.avatar-wrap:hover .avatar-overlay,
.avatar-wrap:focus-visible .avatar-overlay { opacity: 1; }
.avatar-tip { font-size: var(--text-xs); color: var(--text-muted); margin: 0; }
.section-h {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--space-lg);
}
/* ADMIN 角色徽章：主色 calm 风格（不再用 danger 红） */
.role-badge {
  background: var(--primary-50);
  color: var(--primary-700);
  border-color: var(--primary-200);
  font-weight: 600;
}
.role-user {
  background: var(--surface-3);
  color: var(--text-secondary);
  border-color: var(--border);
}

@media (max-width: 480px) {
  .profile-page { padding: var(--space-lg) var(--space-md); }
  .profile-header { flex-direction: column; }
}
</style>

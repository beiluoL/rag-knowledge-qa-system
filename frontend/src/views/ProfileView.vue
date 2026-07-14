<template>
  <div class="chat-layout">
    <aside class="sidebar" :style="{ width: '280px' }">
      <div class="sidebar-header">
        <h3>💬 会话列表</h3>
      </div>
      <div class="sidebar-footer">
        <el-button @click="router.push('/chat')" style="width:100%">← 返回对话</el-button>
      </div>
    </aside>
    <main class="profile-main">
      <el-card class="profile-card">
        <template #header><h2>个人中心</h2></template>
        <el-descriptions title="账号信息" :column="1" border>
          <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag :type="userInfo.role === 'ADMIN' ? 'danger' : 'primary'">{{ userInfo.role }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ userInfo.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <!-- 用户资料编辑区域 -->
        <el-divider />
        <h3 style="margin-bottom:16px">编辑资料</h3>
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
        <h3 style="margin-bottom:16px">修改密码</h3>
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
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserInfo, changePassword, updateProfile } from '@/api/user'

const router = useRouter()
const saving = ref(false)
const profileSaving = ref(false)
const formRef = ref()
const userInfo = reactive({ username: '', email: '', nickname: '', role: '', createdAt: '' })

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
.chat-layout { height: 100vh; display: flex; }
.sidebar { background: #fff; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column; }
.sidebar-header { padding: 16px; border-bottom: 1px solid #ebeef5; }
.sidebar-footer { padding: 16px; border-top: 1px solid #ebeef5; margin-top: auto; }
.profile-main { flex: 1; padding: 40px; overflow-y: auto; background: #f5f7fa; }
.profile-card { max-width: 700px; }
</style>

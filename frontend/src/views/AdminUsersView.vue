<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">管理平台用户，调整角色与启用状态。</p>
      </div>
      <el-button :icon="MessageCircle" @click="router.push('/chat')">返回对话</el-button>
    </div>

    <el-card>
      <el-table :data="users" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="nickname" label="昵称" width="150">
          <template #default="{ row }">
            {{ row.nickname || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200">
          <template #default="{ row }">
            {{ row.email || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-select v-model="row.role" size="small" @change="(val: string) => handleRoleChange(row, val)">
              <el-option label="管理员" value="ADMIN" />
              <el-option label="普通用户" value="USER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              :type="row.enabled ? 'danger' : 'success'"
              size="small"
              @click="handleToggle(row)"
            >
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MessageCircle } from 'lucide-vue-next'
import { getAllUsers, toggleUser, updateUserRole, type AdminUser } from '@/api/admin'

const router = useRouter()
const loading = ref(false)
const users = ref<AdminUser[]>([])

/** 加载用户列表 */
async function loadUsers() {
  loading.value = true
  try {
    const { data } = await getAllUsers()
    users.value = data
  } catch (e: any) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

/** 切换用户启用/禁用 */
async function handleToggle(row: AdminUser) {
  const action = row.enabled ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户 "${row.username}" 吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await toggleUser(row.id)
    row.enabled = !row.enabled
    ElMessage.success(`${action}成功`)
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '操作失败')
    }
  }
}

/** 修改用户角色 */
async function handleRoleChange(row: AdminUser, newRole: string) {
  try {
    await updateUserRole(row.id, newRole)
    ElMessage.success('角色修改成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '角色修改失败')
    // 恢复原值
    loadUsers()
  }
}

onMounted(loadUsers)
</script>

<style scoped>
/* 表格在窄屏自动横向滚动；卡片与工具类由 design system 提供 */
.el-card { background: var(--surface); }

@media (max-width: 480px) {
  /* 触摸目标 ≥44px */
  .el-button { min-height: 44px; }
}
</style>

<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>用户管理</h2>
        <p class="header-desc">管理系统用户账户与权限</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增用户
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.username" placeholder="搜索用户名" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </div>
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="120">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ row.username?.charAt(0)?.toUpperCase() }}</div>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="realName" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" active-value="active" inactive-value="inactive" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="time-cell">{{ row.createdAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleRole(row)">配置角色</el-button>
            <el-button type="primary" link @click="handleCaller(row)">关联调用方</el-button>
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="500px" class="form-dialog">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.realName" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色配置弹窗 -->
    <el-dialog v-model="roleVisible" title="配置角色" width="800px" class="config-dialog">
      <div class="config-container">
        <el-transfer
          v-model="selectedRoles"
          :data="roleList"
          :titles="['可选角色', '已授角色']"
          :props="{ key: 'id', label: 'roleName' }"
          class="custom-transfer"
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="roleVisible = false" size="large">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSaveRoles" size="large">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 调用方关联弹窗 -->
    <el-dialog v-model="callerVisible" title="关联调用方" width="800px" class="config-dialog">
      <div class="config-container">
        <el-transfer
          v-model="selectedCallers"
          :data="callerList"
          :titles="['可选调用方', '已关联调用方']"
          :props="{ key: 'id', label: 'callerName' }"
          class="custom-transfer"
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="callerVisible = false" size="large">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSaveCallers" size="large">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, createUser, updateUser, deleteUser, updateUserStatus, getUserRoles, assignUserRoles, getRoleList, getUserCallers, assignUserCallers, getCallerList } from '@/api/user'
import type { UserDTO } from '@/types'
import { COMMON_STATUS } from '@/constants/status'

interface Role { id: number; roleCode: string; roleName: string }
interface Caller { id: number; callerCode: string; callerName: string }

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<UserDTO[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })
const searchForm = reactive({ username: '', status: '' })
const dialogVisible = ref(false)
const form = reactive({ id: null as number | null, username: '', realName: '', email: '', phone: '', status: COMMON_STATUS.ACTIVE })

const roleVisible = ref(false)
const roleList = ref<Role[]>([])
const selectedRoles = ref<number[]>([])
const currentUserId = ref<number | null>(null)

const callerVisible = ref(false)
const callerList = ref<Caller[]>([])
const selectedCallers = ref<number[]>([])

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getUserList({ page: pagination.currentPage, pageSize: pagination.pageSize, ...searchForm })
    tableData.value = (res as any)?.data?.data?.records || (res as any)?.data?.data || (res as any)?.data || []
    total.value = (res as any)?.data?.total || 0
  } catch (e: unknown) {
    console.error('加载用户列表失败:', e)
    tableData.value = []
    total.value = 0
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.currentPage = 1; fetchList() }
const handleReset = () => { searchForm.username = ''; searchForm.status = ''; pagination.currentPage = 1; fetchList() }

const handleAdd = () => { Object.assign(form, { id: null, username: '', realName: '', email: '', phone: '', status: 'active' }); dialogVisible.value = true }
const handleEdit = (row: UserDTO) => { Object.assign(form, { ...row }); dialogVisible.value = true }

const handleDelete = async (row: UserDTO) => {
  try {
    await ElMessageBox.confirm('确定要删除该用户吗？', '提示', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!form.username) {
    ElMessage.warning('请填写用户名')
    return
  }
  submitting.value = true
  try {
    const payload = { ...form, id: form.id ?? undefined }
    if (form.id) {
      await updateUser(form.id, payload)
    } else {
      await createUser(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchList()
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const handleStatusChange = async (row: UserDTO) => {
  try {
    await updateUserStatus(row.id, row.status)
    ElMessage.success(row.status === COMMON_STATUS.ACTIVE ? '已启用' : '已禁用')
  } catch (error) {
    row.status = row.status === COMMON_STATUS.ACTIVE ? COMMON_STATUS.INACTIVE : COMMON_STATUS.ACTIVE
    ElMessage.error('状态更新失败')
  }
}

const handleRole = async (row: UserDTO) => {
  currentUserId.value = row.id
  try {
    const [rolesRes, userRolesRes] = await Promise.all([
      getRoleList({ page: 1, pageSize: 100 }),
      getUserRoles(row.id)
    ])
    roleList.value = (rolesRes as any)?.data?.data?.records || (rolesRes as any)?.data?.data || []
    selectedRoles.value = (userRolesRes as any)?.data?.data || []
    roleVisible.value = true
  } catch (error) {
    ElMessage.error('加载角色数据失败')
  }
}

const handleSaveRoles = async () => {
  if (!currentUserId.value) return
  submitting.value = true
  try {
    await assignUserRoles(currentUserId.value, selectedRoles.value)
    ElMessage.success('角色配置成功')
    roleVisible.value = false
  } catch (error) {
    ElMessage.error('角色配置失败')
  } finally {
    submitting.value = false
  }
}

const handleCaller = async (row: UserDTO) => {
  currentUserId.value = row.id
  try {
    const [callersRes, userCallersRes] = await Promise.all([
      getCallerList({ page: 1, pageSize: 100 }),
      getUserCallers(row.id)
    ])
    callerList.value = (callersRes as any)?.data?.data?.records || (callersRes as any)?.data?.data || []
    selectedCallers.value = (userCallersRes as any)?.data?.data || []
    callerVisible.value = true
  } catch (error) {
    ElMessage.error('加载调用方数据失败')
  }
}

const handleSaveCallers = async () => {
  if (!currentUserId.value) return
  submitting.value = true
  try {
    await assignUserCallers(currentUserId.value, selectedCallers.value)
    ElMessage.success('调用方关联成功')
    callerVisible.value = false
  } catch (error) {
    ElMessage.error('调用方关联失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => { fetchList() })
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }
.page-header .el-button { display: flex; align-items: center; gap: 8px; }
.page-header .el-button svg { width: 18px; height: 18px; }
.search-card { margin-bottom: 20px; }
.search-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.search-inputs { display: flex; gap: 12px; flex: 1; }
.search-input { width: 280px; }
.search-select { width: 160px; }
.search-btn-group { display: flex; gap: 10px; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.user-avatar { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, var(--color-primary), #6366F1); border-radius: 8px; color: #0A1628; font-weight: 600; font-size: 14px; }
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }

.config-dialog :deep(.el-dialog__header) {
  padding: 24px 24px 16px;
  border-bottom: 1px solid var(--color-border);
}

.config-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.config-container {
  padding: 8px 0;
}

.custom-transfer {
  --el-transfer-panel-width: 300px;
}

.custom-transfer :deep(.el-transfer-panel) {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.custom-transfer :deep(.el-transfer-panel__header) {
  background: linear-gradient(135deg, var(--color-primary-light) 0%, var(--color-primary) 100%);
  padding: 16px 20px;
}

.custom-transfer :deep(.el-transfer-panel__header .el-checkbox__label) {
  color: white;
  font-weight: 600;
  font-size: 14px;
}

.custom-transfer :deep(.el-transfer-panel__header .el-checkbox__inner) {
  border-color: white;
}

.custom-transfer :deep(.el-transfer-panel__body) {
  height: 400px;
}



.custom-transfer :deep(.el-transfer-panel__item) {
  border-radius: 8px;
}

.custom-transfer :deep(.el-transfer__buttons) {
  padding: 0 24px;
}

.custom-transfer :deep(.el-transfer__button) {
  border-radius: 10px;
  width: 44px;
  height: 44px;
}

.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>

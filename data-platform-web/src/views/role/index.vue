<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <PageHeader title="角色管理" description="管理系统角色与权限配置">
      <template #action>
        <el-button type="primary" @click="handleAdd">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          新增角色
        </el-button>
      </template>
    </PageHeader>

    <!-- 搜索区域 -->
    <SearchBar @search="handleSearch" @reset="handleReset">
      <el-input v-model="searchForm.roleName" placeholder="搜索角色名称" clearable class="search-input" @keyup.enter="handleSearch" />
      <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
        <el-option :label="STATUS_LABELS[COMMON_STATUS.ACTIVE]" :value="COMMON_STATUS.ACTIVE" />
        <el-option :label="STATUS_LABELS[COMMON_STATUS.INACTIVE]" :value="COMMON_STATUS.INACTIVE" />
      </el-select>
    </SearchBar>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleCode" label="角色编码" width="150">
          <template #default="{ row }">
            <span class="code-tag">{{ row.roleCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="COMMON_STATUS.ACTIVE" :inactive-value="COMMON_STATUS.INACTIVE" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="time-cell">{{ row.createdAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePermission(row)">配置权限</el-button>
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
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="500px" class="form-dialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.roleCode" placeholder="如: ADMIN, TENANT_ADMIN" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
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

    <!-- 权限配置弹窗 -->
    <el-dialog v-model="permissionVisible" title="配置权限" width="800px" class="permission-dialog">
      <div class="permission-config-container">
        <el-transfer
          v-model="selectedPermissions"
          :data="permissionList"
          :titles="['可选权限', '已授权限']"
          :props="{ key: 'id', label: 'permissionName' }"
          class="custom-transfer"
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="permissionVisible = false" size="large">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSavePermissions" size="large">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/utils/request'
import PageHeader from '@/components/PageHeader.vue'
import SearchBar from '@/components/SearchBar.vue'
import { COMMON_STATUS, STATUS_LABELS } from '@/constants'

interface Role { id: number; roleCode: string; roleName: string; description: string; status: string; createdAt: string }
interface Permission { id: number; permissionCode: string; permissionName: string }

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<Role[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })
const searchForm = reactive({ roleName: '', status: '' })
const dialogVisible = ref(false)
const form = reactive({ id: null as number | null, roleCode: '', roleName: '', description: '', status: COMMON_STATUS.ACTIVE })

const permissionVisible = ref(false)
const permissionList = ref<Permission[]>([])
const selectedPermissions = ref<number[]>([])
const currentRoleId = ref<number | null>(null)

interface RoleListResponse {
  data?: { records?: Role[]; total?: number } | Role[]
  total?: number
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get<RoleListResponse>('/role/list', { params: { page: pagination.currentPage, pageSize: pagination.pageSize, ...searchForm } })
    const data = res.data
    if (data && 'records' in data && Array.isArray(data.records)) {
      tableData.value = data.records
      total.value = data.total || 0
    } else if (Array.isArray(data)) {
      tableData.value = data
      total.value = res.total || 0
    }
  } catch {
    tableData.value = []
    total.value = 0
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.currentPage = 1; fetchList() }
const handleReset = () => { searchForm.roleName = ''; searchForm.status = ''; pagination.currentPage = 1; fetchList() }
const handleAdd = () => { Object.assign(form, { id: null, roleCode: '', roleName: '', description: '', status: 'active' }); dialogVisible.value = true }
const handleEdit = (row: Role) => { Object.assign(form, { ...row }); dialogVisible.value = true }

const handleDelete = async (row: Role) => {
  try {
    await ElMessageBox.confirm(`确定要删除角色"${row.roleName}"吗？`, '提示', { type: 'warning' })
    await request.delete(`/role/${row.id}`)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!form.roleCode || !form.roleName) {
    ElMessage.warning('请填写角色编码和角色名称')
    return
  }
  submitting.value = true
  try {
    if (form.id) {
      await request.put(`/role/${form.id}`, form)
    } else {
      await request.post('/role', form)
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

const handleStatusChange = async (row: Role) => {
  try {
    await request.patch(`/role/${row.id}/status`, { status: row.status })
    ElMessage.success(row.status === COMMON_STATUS.ACTIVE ? '已启用' : '已禁用')
  } catch (error) {
    row.status = row.status === COMMON_STATUS.ACTIVE ? COMMON_STATUS.INACTIVE : COMMON_STATUS.ACTIVE
    ElMessage.error('状态更新失败')
  }
}

const handlePermission = async (row: Role) => {
  currentRoleId.value = row.id
  try {
    const [permsRes, rolePermsRes] = await Promise.all([
      request.get<{ data: Permission[] }>('/permission/all'),
      request.get<{ data: number[] }>(`/role/${row.id}/permissionIds`)
    ])
    permissionList.value = permsRes.data || []
    selectedPermissions.value = rolePermsRes.data || []
    permissionVisible.value = true
  } catch (error) {
    ElMessage.error('加载权限数据失败')
  }
}

const handleSavePermissions = async () => {
  if (!currentRoleId.value) return
  submitting.value = true
  try {
    await request.post(`/role/${currentRoleId.value}/permissions`, selectedPermissions.value)
    ElMessage.success('权限配置成功')
    permissionVisible.value = false
  } catch (error) {
    ElMessage.error('权限配置失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => { fetchList() })
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }
.page-header .el-button { display: flex; align-items: center; gap: 8px; }
.page-header .el-button svg { width: 18px; height: 18px; }
.search-input { width: 280px; }
.search-select { width: 160px; }
.code-tag { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 4px 10px; border-radius: 6px; }
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }

.permission-dialog :deep(.el-dialog__header) {
  padding: 24px 24px 16px;
  border-bottom: 1px solid var(--color-border);
}

.permission-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.permission-config-container {
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

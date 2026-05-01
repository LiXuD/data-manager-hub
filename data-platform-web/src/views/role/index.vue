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
        <el-option :label="STATUS_LABELS[USER_STATUS.ACTIVE]" :value="USER_STATUS.ACTIVE" />
        <el-option :label="STATUS_LABELS[USER_STATUS.INACTIVE]" :value="USER_STATUS.INACTIVE" />
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
            <el-switch v-model="row.status" :active-value="USER_STATUS.ACTIVE" :inactive-value="USER_STATUS.INACTIVE" @change="handleStatusChange(row)" />
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
        <el-button type="primary" @click="handleSubmit">确定</el-button>
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
import { USER_STATUS, STATUS_LABELS } from '@/constants'

interface Role { id: number; roleCode: string; roleName: string; description: string; status: string; createdAt: string }

const loading = ref(false)
const tableData = ref<Role[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })
const searchForm = reactive({ roleName: '', status: '' })
const dialogVisible = ref(false)
const form = reactive({ id: null as number | null, roleCode: '', roleName: '', description: '', status: 'active' })

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
const handleDelete = async (row: Role) => { await ElMessageBox.confirm(`确定要删除角色"${row.roleName}"吗？`, '提示', { type: 'warning' }); ElMessage.success('删除成功'); fetchList() }
const handleSubmit = async () => { if (!form.roleCode || !form.roleName) { ElMessage.warning('请填写角色编码和角色名称'); return } ElMessage.success('保存成功'); dialogVisible.value = false; fetchList() }
const handlePermission = (row: Role) => { ElMessage.info(`配置角色"${row.roleName}"的权限`) }
const handleStatusChange = (row: Role) => { ElMessage.success(row.status === 'active' ? '已启用' : '已禁用') }

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
</style>
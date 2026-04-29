<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>租户管理</h2>
        <p class="header-desc">管理多租户配置与数据隔离</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增租户
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索租户编码或名称"
            clearable
            class="search-input"
            @keyup.enter="handleSearch"
          />
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option label="启用" value="enabled" />
            <el-option label="禁用" value="disabled" />
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
        <el-table-column prop="tenantCode" label="编码" width="140">
          <template #default="{ row }">
            <span class="code-tag">{{ row.tenantCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tenantName" label="名称" min-width="160" />
        <el-table-column prop="tenantType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.tenantType === 'enterprise' ? 'success' : 'info'" size="small">
              {{ row.tenantType === 'enterprise' ? '企业' : '个人' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="active"
              inactive-value="disabled"
              @change="handleToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactEmail" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column prop="maxApiKeys" label="API Key配额" width="110" align="center" />
        <el-table-column prop="maxCallers" label="Caller配额" width="100" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="time-cell">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="550px" class="form-dialog">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="租户编码" prop="tenantCode">
          <el-input v-model="formData.tenantCode" placeholder="请输入租户编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="formData.tenantName" placeholder="请输入租户名称" />
        </el-form-item>
        <el-form-item label="租户类型" prop="tenantType">
          <el-select v-model="formData.tenantType" placeholder="请选择" style="width: 100%">
            <el-option label="企业" value="enterprise" />
            <el-option label="个人" value="personal" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="formData.contactPerson" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="邮箱" prop="contactEmail">
          <el-input v-model="formData.contactEmail" placeholder="请输入邮箱" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="API Key配额" prop="maxApiKeys">
              <el-input-number v-model="formData.maxApiKeys" :min="1" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Caller配额" prop="maxCallers">
              <el-input-number v-model="formData.maxCallers" :min="1" :max="10000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTenantList, createTenant, updateTenant, deleteTenant } from '@/api/tenant'
import type { Tenant } from '@/types'

const tableData = ref<Tenant[]>([])
const loading = ref(false)
const searchForm = reactive({ keyword: '', status: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const formData = reactive<Partial<Tenant>>({
  id: undefined, tenantCode: '', tenantName: '', tenantType: 'enterprise',
  status: 'active', contactPerson: '', contactEmail: '', maxApiKeys: 10, maxCallers: 50
})
const formRules = {
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  tenantType: [{ required: true, message: '请选择租户类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getTenantList({
      page: pagination.page, pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined, status: searchForm.status as 'active' | 'disabled' | undefined
    })
    tableData.value = res.data || []
    pagination.total = res.total || 0
  } catch {
    tableData.value = []
  }
  finally { loading.value = false }
}

const handleSearch = () => { pagination.page = 1; fetchData() }
const handleReset = () => { searchForm.keyword = ''; searchForm.status = ''; pagination.page = 1; fetchData() }

const handleAdd = () => {
  isEdit.value = false; dialogTitle.value = '新增租户'
  Object.assign(formData, { id: null, tenantCode: '', tenantName: '', tenantType: 'enterprise', status: 'active', contactPerson: '', contactEmail: '', maxApiKeys: 10, maxCallers: 50 })
  dialogVisible.value = true
}

const handleEdit = (row: Tenant) => {
  isEdit.value = true; dialogTitle.value = '编辑租户'
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

const handleDelete = async (row: Tenant) => {
  await ElMessageBox.confirm(`确定要删除租户 "${row.tenantName}" 吗？`, '警告', { type: 'warning' })
  try {
    await deleteTenant(String(row.id))
    ElMessage.success('删除成功')
    fetchData()
  } catch { /* 错误已在拦截器中处理 */ }
}

const handleToggleStatus = async (row: Tenant) => {
  const newStatus = row.status === 'active' ? 'disabled' : 'active'
  try {
    await updateTenant(String(row.id), { ...row, status: newStatus })
    ElMessage.success(newStatus === 'active' ? '已启用' : '已禁用')
  } catch { row.status = row.status === 'active' ? 'disabled' : 'active' }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateTenant(formData.id!, formData)
    } else {
      await createTenant(formData)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    fetchData()
  } catch { /* 错误已在拦截器中处理 */ }
  finally { submitting.value = false }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => { fetchData() })
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
.code-tag { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 4px 10px; border-radius: 6px; }
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
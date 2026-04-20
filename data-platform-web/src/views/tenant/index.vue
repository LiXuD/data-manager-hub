<template>
  <div class="page-container">
    <div class="card">
      <!-- 搜索栏 -->
      <div class="table-toolbar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索租户编码或名称"
          clearable
          style="width: 200px; margin-right: 10px;"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 120px; margin-right: 10px;">
          <el-option label="启用" value="enabled" />
          <el-option label="禁用" value="disabled" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" style="margin-left: auto;" @click="handleAdd">新增租户</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="tenantCode" label="编码" width="120" />
        <el-table-column prop="tenantName" label="名称" width="150" />
        <el-table-column prop="tenantType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.tenantType === 'enterprise'">企业</el-tag>
            <el-tag v-else type="info">个人</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactEmail" label="邮箱" min-width="150" />
        <el-table-column prop="maxApiKeys" label="API Key配额" width="100" align="center" />
        <el-table-column prop="maxCallers" label="Caller配额" width="100" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'active' ? 'danger' : 'success'" @click="handleToggleStatus(row)">
              {{ row.status === 'active' ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="租户编码" prop="tenantCode">
          <el-input v-model="formData.tenantCode" placeholder="请输入租户编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="formData.tenantName" placeholder="请输入租户名称" />
        </el-form-item>
        <el-form-item label="租户类型" prop="tenantType">
          <el-select v-model="formData.tenantType" placeholder="请选择">
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
        <el-form-item label="API Key配额" prop="maxApiKeys">
          <el-input-number v-model="formData.maxApiKeys" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="Caller配额" prop="maxCallers">
          <el-input-number v-model="formData.maxCallers" :min="1" :max="10000" />
        </el-form-item>
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

// 数据
const tableData = ref<any[]>([])
const loading = ref(false)

// 搜索表单
const searchForm = reactive({
  keyword: '',
  status: ''
})

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const formData = reactive({
  id: null as number | null,
  tenantCode: '',
  tenantName: '',
  tenantType: 'enterprise',
  status: 'active',
  contactPerson: '',
  contactEmail: '',
  maxApiKeys: 10,
  maxCallers: 50
})

const formRules = {
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  tenantType: [{ required: true, message: '请选择租户类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

// 加载数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getTenantList({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status || undefined
    })
    if (res.code === 0 || res.code === null || res.code === undefined) { // Use res directly, not res.data
      tableData.value = Array.isArray(res.data) ? res.data : (res.data?.list || [])
      pagination.total = res.total || res.data?.total || 0
    } else {
      ElMessage.error(res.data.message || '加载失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '请求失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.page = 1
  fetchData()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.status = ''
  pagination.page = 1
  fetchData()
}

// 新增
const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增租户'
  Object.assign(formData, {
    id: null,
    tenantCode: '',
    tenantName: '',
    tenantType: 'enterprise',
    status: 'active',
    contactPerson: '',
    contactEmail: '',
    maxApiKeys: 10,
    maxCallers: 50
  })
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: any) => {
  isEdit.value = true
  dialogTitle.value = '编辑租户'
  Object.assign(formData, {
    id: row.id,
    tenantCode: row.tenantCode,
    tenantName: row.tenantName,
    tenantType: row.tenantType,
    status: row.status,
    contactPerson: row.contactPerson,
    contactEmail: row.contactEmail,
    maxApiKeys: row.maxApiKeys,
    maxCallers: row.maxCallers
  })
  dialogVisible.value = true
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  
  submitting.value = true
  try {
    const api = isEdit.value ? updateTenant : createTenant
    const params = isEdit.value ? [formData.id, formData] : [formData]
    const res = await api(...params)
    
    if (res.code === 0 || res.code === null || res.code === undefined) { // Use res directly, not res.data
      ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
      dialogVisible.value = false
      fetchData()
    } else {
      ElMessage.error(res.data.message || '操作失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// 删除
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定要删除租户 "${row.tenantName}" 吗？`, '警告', {
    type: 'warning'
  }).then(async () => {
    try {
      const res = await deleteTenant(String(row.id))
      if (res.code === 0 || res.code === null || res.code === undefined) { // Use res directly, not res.data
        ElMessage.success('删除成功')
        fetchData()
      } else {
        ElMessage.error(res.data.message || '删除失败')
      }
    } catch (error: any) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

// 切换状态
const handleToggleStatus = async (row: any) => {
  const newStatus = row.status === 'active' ? 'disabled' : 'active'
  const action = newStatus === 'active' ? '启用' : '禁用'
  try {
    // 调用更新API来切换状态
    const res = await updateTenant(String(row.id), { ...row, status: newStatus })
    if (res.code === 0 || res.code === null || res.code === undefined) { // Use res directly, not res.data
      ElMessage.success(`${action}成功`)
      fetchData()
    } else {
      ElMessage.error(res.data.message || `${action}失败`)
    }
  } catch (error: any) {
    ElMessage.error(error.message || `${action}失败`)
  }
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.card {
  background: #fff;
  border-radius: 4px;
  padding: 20px;
}
.table-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 10px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
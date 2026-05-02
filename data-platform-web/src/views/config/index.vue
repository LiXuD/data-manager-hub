<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>配置中心</h2>
        <p class="header-desc">管理第三方厂商API配置参数</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增配置
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-select v-model="searchForm.vendorId" placeholder="选择厂商" clearable class="search-select">
            <el-option v-for="item in vendorList" :key="item.id" :label="item.vendorName" :value="item.id" />
          </el-select>
          <el-input v-model="searchForm.configKey" placeholder="搜索配置Key" clearable class="search-input" @keyup.enter="handleSearch" />
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
        <el-table-column prop="vendorName" label="厂商" width="120" />
        <el-table-column prop="configKey" label="配置Key" width="180">
          <template #default="{ row }">
            <span class="config-key" :class="{ encrypted: row.isEncrypted }">
              <svg v-if="row.isEncrypted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="key-icon">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              {{ row.configKey }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" label="配置值" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="config-value">{{ row.isEncrypted ? '••••••••' : row.configValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.configType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="desc-cell">{{ row.description }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.isActive" @change="handleToggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">
            <span class="time-cell">{{ row.updatedAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="form-dialog">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="厂商" required>
          <el-select v-model="formData.vendorId" style="width: 100%">
            <el-option v-for="item in vendorList" :key="item.id" :label="item.vendorName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置Key" required>
          <el-input v-model="formData.configKey" placeholder="如: api_timeout" />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="formData.configValue" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="formData.configType" style="width: 100%">
            <el-option v-for="item in configTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="加密存储">
          <el-switch v-model="formData.isEncrypted" />
          <span class="form-tip">开启后配置值将加密存储</span>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="formData.isActive" />
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
import { getConfigList, createConfig, updateConfig, deleteConfig } from '@/api/config'

interface VendorConfig {
  id: number
  vendorId: number
  vendorName: string
  configKey: string
  configValue: string
  configType: string
  description: string
  isEncrypted: boolean
  isActive: boolean
  createdAt: string
  updatedAt: string
}

interface Vendor {
  id: number
  vendorName: string
}

const loading = ref(false)
const tableData = ref<VendorConfig[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })

const searchForm = reactive({
  vendorId: null as number | null,
  configKey: ''
})

const vendorList = ref<Vendor[]>([
  { id: 1, vendorName: '企查查' },
  { id: 2, vendorName: '天眼查' },
  { id: 3, vendorName: '启信宝' }
])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formData = reactive<VendorConfig>({
  id: 0,
  vendorId: 0,
  vendorName: '',
  configKey: '',
  configValue: '',
  configType: 'string',
  description: '',
  isEncrypted: false,
  isActive: true,
  createdAt: '',
  updatedAt: ''
})

const configTypeOptions = [
  { label: '字符串', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔值', value: 'boolean' },
  { label: 'JSON', value: 'json' },
  { label: '密码', value: 'password' }
]

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getConfigList({
      page: pagination.currentPage,
      pageSize: pagination.pageSize,
      vendorId: searchForm.vendorId || undefined,
      keyword: searchForm.configKey || undefined
    })
    tableData.value = res.data?.data?.records || res.data?.data || res.data || []
    total.value = res.data?.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.currentPage = 1; fetchList() }
const handleReset = () => { searchForm.vendorId = null; searchForm.configKey = ''; pagination.currentPage = 1; fetchList() }

const handleAdd = () => {
  dialogTitle.value = '新增配置'
  Object.assign(formData, { id: 0, vendorId: 0, vendorName: '', configKey: '', configValue: '', configType: 'string', description: '', isEncrypted: false, isActive: true, createdAt: '', updatedAt: '' })
  dialogVisible.value = true
}

const handleEdit = (row: VendorConfig) => {
  dialogTitle.value = '编辑配置'
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

const handleDelete = async (row: VendorConfig) => {
  try {
    await ElMessageBox.confirm(`确定要删除配置"${row.configKey}"吗？`, '提示', { type: 'warning' })
    await deleteConfig(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    ElMessage.error('删除失败')
  }
}

const handleSubmit = async () => {
  if (!formData.vendorId || !formData.configKey) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    if (formData.id) {
      await updateConfig(formData.id, formData)
      ElMessage.success('更新成功')
    } else {
      await createConfig(formData)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    ElMessage.error('保存失败')
  }
}

const handleToggleStatus = (row: VendorConfig) => {
  row.isActive = !row.isActive
  ElMessage.success(row.isActive ? '已启用' : '已禁用')
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
.search-inputs { display: flex; gap: 12px; flex: 1; flex-wrap: wrap; }
.search-input { width: 200px; }
.search-select { width: 160px; }
.search-btn-group { display: flex; gap: 10px; }

.config-key { display: flex; align-items: center; gap: 6px; font-family: var(--font-mono); font-size: 13px; }
.config-key.encrypted { color: #E6A23C; }
.key-icon { width: 14px; height: 14px; }
.config-value { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); }
.desc-cell { color: var(--color-text-tertiary); font-size: 13px; }
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }

.form-tip { margin-left: 10px; color: var(--color-text-tertiary); font-size: 12px; }

.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>

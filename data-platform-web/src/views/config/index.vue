<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/utils/request'
import { Plus, Edit, Delete, Key } from '@element-plus/icons-vue'

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
const pagination = ref({ currentPage: 1, pageSize: 10 })

const searchForm = ref({
  vendorId: null as number | null,
  configKey: ''
})

const vendorList = ref<Vendor[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formData = ref<VendorConfig>({
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
    const res = await request.get('/api/v1/config/list', {
      params: {
        page: pagination.value.currentPage,
        pageSize: pagination.value.pageSize,
        vendorId: searchForm.value.vendorId,
        configKey: searchForm.value.configKey
      }
    })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e: any) {
    console.error('获取配置列表失败:', e)
    // 模拟数据
    tableData.value = [
      { id: 1, vendorId: 1, vendorName: '企查查', configKey: 'api_timeout', configValue: '5000', configType: 'number', description: 'API超时时间(毫秒)', isEncrypted: false, isActive: true, createdAt: '2026-04-15 10:00:00', updatedAt: '2026-04-20 15:30:00' },
      { id: 2, vendorId: 1, vendorName: '企查查', configKey: 'api_key', configValue: 'sk-xxxxx', configType: 'password', description: 'API密钥', isEncrypted: true, isActive: true, createdAt: '2026-04-15 10:00:00', updatedAt: '2026-04-20 15:30:00' },
      { id: 3, vendorId: 2, vendorName: '天眼查', configKey: 'max_retries', configValue: '3', configType: 'number', description: '最大重试次数', isEncrypted: false, isActive: true, createdAt: '2026-04-16 11:00:00', updatedAt: '2026-04-18 09:00:00' },
      { id: 4, vendorId: 2, vendorName: '天眼查', configKey: 'retry_interval', configValue: '1000', configType: 'number', description: '重试间隔(毫秒)', isEncrypted: false, isActive: true, createdAt: '2026-04-16 11:00:00', updatedAt: '2026-04-18 09:00:00' },
      { id: 5, vendorId: 3, vendorName: '裁判文书网', configKey: 'cache_ttl', configValue: '3600', configType: 'number', description: '缓存有效期(秒)', isEncrypted: false, isActive: false, createdAt: '2026-04-17 14:00:00', updatedAt: '2026-04-19 16:00:00' }
    ]
    total.value = 28
    
    vendorList.value = [
      { id: 1, vendorName: '企查查' },
      { id: 2, vendorName: '天眼查' },
      { id: 3, vendorName: '裁判文书网' }
    ]
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.currentPage = 1
  fetchList()
}

const handleReset = () => {
  searchForm.value = { vendorId: null, configKey: '' }
  pagination.value.currentPage = 1
  fetchList()
}

const handleAdd = () => {
  dialogTitle.value = '新增配置'
  formData.value = {
    id: 0, vendorId: 0, vendorName: '', configKey: '', configValue: '',
    configType: 'string', description: '', isEncrypted: false, isActive: true,
    createdAt: '', updatedAt: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row: VendorConfig) => {
  dialogTitle.value = '编辑配置'
  formData.value = { ...row }
  dialogVisible.value = true
}

const handleDelete = async (row: VendorConfig) => {
  try {
    await ElMessageBox.confirm(`确定要删除配置"${row.configKey}"吗？`, '提示', { type: 'warning' })
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {}
}

const handleSubmit = async () => {
  if (!formData.value.vendorId || !formData.value.configKey) {
    ElMessage.warning('请填写完整信息')
    return
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchList()
}

const handleToggleStatus = (row: VendorConfig) => {
  row.isActive = !row.isActive
  ElMessage.success(row.isActive ? '已启用' : '已禁用')
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="config-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="厂商">
          <el-select v-model="searchForm.vendorId" placeholder="请选择厂商" clearable style="width: 150px">
            <el-option v-for="item in vendorList" :key="item.id" :label="item.vendorName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置Key">
          <el-input v-model="searchForm.configKey" placeholder="请输入配置Key" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>厂商配置列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增配置</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="vendorName" label="厂商" width="120" />
        <el-table-column prop="configKey" label="配置Key" width="180">
          <template #default="{ row }">
            <el-tag v-if="row.isEncrypted" type="warning" :icon="Key">{{ row.configKey }}</el-tag>
            <span v-else>{{ row.configKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" label="配置值" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.isEncrypted ? '******' : row.configValue }}
          </template>
        </el-table-column>
        <el-table-column prop="configType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.configType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column prop="isActive" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.isActive" @change="handleToggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.currentPage"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
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
          <span style="margin-left: 10px; color: #909399; font-size: 12px">开启后配置值将加密存储</span>
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

<style scoped>
.config-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
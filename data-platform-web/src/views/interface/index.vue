<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>接口管理</h2>
        <p class="header-desc">管理API接口定义与配置</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增接口
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-select v-model="searchForm.vendorId" placeholder="厂商" clearable class="search-select">
            <el-option
              v-for="vendor in vendorOptions"
              :key="vendor.id"
              :label="vendor.vendorName"
              :value="vendor.id"
            />
          </el-select>

          <el-select v-model="searchForm.dataTypeId" placeholder="数据类型" clearable class="search-select">
            <el-option
              v-for="dt in dataTypeOptions"
              :key="dt.id"
              :label="dt.dataTypeName"
              :value="dt.id"
            />
          </el-select>

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
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        class="interface-table"
      >
        <el-table-column prop="interfaceCode" label="接口编码" width="140">
          <template #default="{ row }">
            <span class="interface-code">{{ row.interfaceCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="interfaceName" label="接口名称" min-width="180">
          <template #default="{ row }">
            <div class="interface-info">
              <span class="interface-name">{{ row.interfaceName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="接口路径" min-width="200">
          <template #default="{ row }">
            <span class="path-cell">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="vendorName" label="所属厂商" width="120">
          <template #default="{ row }">
            <span>{{ row.vendorName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dataTypeName" label="数据类型" width="120">
          <template #default="{ row }">
            <span>{{ row.dataTypeName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="active"
              inactive-value="inactive"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button type="warning" link @click="handleStats(row)">统计</el-button>
              <el-button type="success" link @click="handleConfig(row)">配置</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </div>
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
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <InterfaceForm
      v-model="formVisible"
      :form-data="currentRow"
      :mode="formMode"
      :vendor-options="vendorOptions"
      :datatype-options="dataTypeOptions"
      @success="handleFormSuccess"
    />

    <!-- 厂商接口配置弹窗 -->
    <VendorInterfaceConfig
      v-model="configVisible"
      :interface-data="currentRow"
      @success="handleConfigSuccess"
    />

    <!-- 统计弹窗 -->
    <InterfaceStats
      v-model="statsVisible"
      :interface-data="currentRow"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getInterfaceList,
  deleteInterface,
  updateInterfaceStatus
} from '@/api/interface'
import { useCacheStore } from '@/stores'
import type { ApiInterface } from '@/types'
import InterfaceForm from './components/InterfaceForm.vue'
import VendorInterfaceConfig from './components/VendorInterfaceConfig.vue'
import InterfaceStats from './components/InterfaceStats.vue'
import { COMMON_STATUS } from '@/constants'

const cacheStore = useCacheStore()

// 搜索表单
const searchForm = reactive({
  vendorId: undefined as number | undefined,
  dataTypeId: undefined as number | undefined,
  status: ''
})

// 表格数据
const tableData = ref<ApiInterface[]>([])
const loading = ref(false)

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

// 从缓存获取下拉选项
const vendorOptions = cacheStore.vendorOptions
const dataTypeOptions = cacheStore.dataTypeOptions

// 表单
const formVisible = ref(false)
const formMode = ref<'add' | 'edit'>('add')
const currentRow = ref<ApiInterface | null>(null)

// 配置弹窗
const configVisible = ref(false)

// 统计弹窗
const statsVisible = ref(false)

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      vendorId: searchForm.vendorId,
      dataTypeId: searchForm.dataTypeId,
      status: searchForm.status as typeof COMMON_STATUS.ACTIVE | typeof COMMON_STATUS.INACTIVE | undefined
    }
    const res = await getInterfaceList(params)
    tableData.value = res.data || []
    pagination.total = res.total || 0
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.page = 1
  loadData()
}

// 重置
const handleReset = () => {
  searchForm.vendorId = undefined
  searchForm.dataTypeId = undefined
  searchForm.status = ''
  pagination.page = 1
  loadData()
}

// 新增
const handleAdd = () => {
  currentRow.value = null
  formMode.value = 'add'
  formVisible.value = true
}

// 编辑
const handleEdit = (row: ApiInterface) => {
  currentRow.value = { ...row }
  formMode.value = 'edit'
  formVisible.value = true
}

// 配置
const handleConfig = (row: ApiInterface) => {
  currentRow.value = { ...row }
  configVisible.value = true
}

// 统计
const handleStats = (row: ApiInterface) => {
  currentRow.value = { ...row }
  statsVisible.value = true
}

// 删除
const handleDelete = async (row: ApiInterface) => {
  try {
    await ElMessageBox.confirm(
      `确认删除接口"${row.interfaceName}"吗？`,
      '提示',
      { type: 'warning' }
    )
    await deleteInterface(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

// 状态切换
const handleStatusChange = async (row: ApiInterface) => {
  try {
    await updateInterfaceStatus(row.id, row.status as typeof COMMON_STATUS.ACTIVE | typeof COMMON_STATUS.INACTIVE)
    ElMessage.success(row.status === COMMON_STATUS.ACTIVE ? '已启用' : '已禁用')
  } catch (error) {
    row.status = row.status === COMMON_STATUS.ACTIVE ? COMMON_STATUS.INACTIVE : COMMON_STATUS.ACTIVE
    ElMessage.error('状态更新失败，请稍后重试')
  }
}

// 表单提交成功
const handleFormSuccess = () => {
  loadData()
}

// 配置保存成功
const handleConfigSuccess = () => {
  loadData()
}

onMounted(async () => {
  await Promise.all([
    cacheStore.loadAll(),
    loadData()
  ])
})
</script>

<style scoped>
.page-container {
  max-width: 1600px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px;
  letter-spacing: -0.02em;
}

.header-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
}

.page-header .el-button {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-header .el-button svg {
  width: 18px;
  height: 18px;
}

/* 搜索区域 */
.search-card {
  margin-bottom: 20px;
}

.search-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.search-inputs {
  display: flex;
  gap: 12px;
  flex: 1;
}

.search-input {
  width: 260px;
}

.search-select {
  width: 140px;
}

.search-icon {
  width: 16px;
  height: 16px;
}

.search-btn-group {
  display: flex;
  gap: 10px;
}

.search-btn-group .el-button {
  display: flex;
  align-items: center;
  gap: 6px;
}

.search-btn-group .el-button svg {
  width: 16px;
  height: 16px;
}

/* 表格卡片 */
.table-card {
  margin-bottom: 20px;
}

/* 表格样式 */
.interface-table {
  --el-table-bg-color: transparent !important;
}

.interface-code {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-text-secondary);
  background: var(--color-bg-light);
  padding: 4px 10px;
  border-radius: 6px;
}

.interface-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.interface-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.path-cell {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-secondary);
}

.table-actions {
  display: flex;
  gap: 8px;
}

/* 分页 */
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 响应式 */
@media (max-width: 768px) {
  .search-inputs {
    flex-direction: column;
    width: 100%;
  }

  .search-input,
  .search-select {
    width: 100%;
  }

  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-btn-group {
    justify-content: flex-end;
  }
}
</style>

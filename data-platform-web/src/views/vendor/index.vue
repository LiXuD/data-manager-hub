<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>厂商管理</h2>
        <p class="header-desc">管理外部数据供应商信息与接口配置</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增厂商
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索厂商名称/编码"
            clearable
            class="search-input"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="search-icon">
                <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
              </svg>
            </template>
          </el-input>

          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>

          <el-select v-model="searchForm.vendorType" placeholder="厂商类型" clearable class="search-select">
            <el-option label="工商信息" value="BUSINESS" />
            <el-option label="个人征信" value="PERSONAL" />
            <el-option label="信用评分" value="CREDIT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </div>

        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
            </svg>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
              <path d="M21 3v5h-5"/>
              <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
              <path d="M8 16H3v5"/>
            </svg>
            重置
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        class="vendor-table"
      >
        <el-table-column prop="vendorCode" label="厂商编码" width="140">
          <template #default="{ row }">
            <span class="vendor-code">{{ row.vendorCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="vendorName" label="厂商名称" min-width="180">
          <template #default="{ row }">
            <div class="vendor-info">
              <span class="vendor-name">{{ row.vendorName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="vendorType" label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="getTypeTag(row.vendorType)" size="small">
              {{ getTypeLabel(row.vendorType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="contactEmail" label="邮箱" min-width="180" show-overflow-tooltip />
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
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button type="primary" link @click="handleView(row)">详情</el-button>
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
    <VendorForm
      v-model="formVisible"
      :form-data="currentRow"
      :mode="formMode"
      @success="handleFormSuccess"
    />

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="厂商详情"
      width="600px"
      class="detail-dialog"
    >
      <div class="detail-container">
        <div class="detail-grid">
          <div class="detail-item">
            <span class="label">厂商编码</span>
            <span class="value">{{ currentRow?.vendorCode }}</span>
          </div>
          <div class="detail-item">
            <span class="label">厂商名称</span>
            <span class="value">{{ currentRow?.vendorName }}</span>
          </div>
          <div class="detail-item">
            <span class="label">厂商类型</span>
            <el-tag :type="getTypeTag(currentRow?.vendorType)" size="small">
              {{ getTypeLabel(currentRow?.vendorType) }}
            </el-tag>
          </div>
          <div class="detail-item">
            <span class="label">联系人</span>
            <span class="value">{{ currentRow?.contactPerson || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="label">联系电话</span>
            <span class="value">{{ currentRow?.contactPhone || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="label">联系邮箱</span>
            <span class="value">{{ currentRow?.contactEmail || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="label">状态</span>
            <el-tag :type="currentRow?.status === 'active' ? 'success' : 'danger'" size="small">
              {{ currentRow?.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </div>
          <div class="detail-item">
            <span class="label">创建时间</span>
            <span class="value">{{ currentRow?.createdAt || '-' }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getVendorList, updateVendorStatus, deleteVendor } from '@/api/vendor'
import type { Vendor } from '@/types'
import VendorForm from './components/VendorForm.vue'

// 搜索表单
const searchForm = reactive({
  keyword: '',
  status: '',
  vendorType: ''
})

// 表格数据
const tableData = ref<Vendor[]>([])
const loading = ref(false)

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

// 表单
const formVisible = ref(false)
const formMode = ref<'add' | 'edit'>('add')
const currentRow = ref<Vendor | null>(null)

// 详情
const detailVisible = ref(false)

// 厂商类型映射
const getTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    BUSINESS: '工商信息',
    PERSONAL: '个人征信',
    CREDIT: '信用评分',
    OTHER: '其他'
  }
  return map[type || ''] || type || '-'
}

const getTypeTag = (type?: string) => {
  const map: Record<string, string> = {
    BUSINESS: 'success',
    PERSONAL: 'warning',
    CREDIT: 'info',
    OTHER: ''
  }
  return map[type || ''] || 'info'
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status || undefined,
      vendorType: searchForm.vendorType || undefined
    }
    const res = await getVendorList(params)
    tableData.value = Array.isArray(res.data) ? res.data : (res.data?.list || [])
    pagination.total = res.total || res.data?.total || 0
  } catch (error) {
    console.error('加载失败:', error)
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
  searchForm.keyword = ''
  searchForm.status = ''
  searchForm.vendorType = ''
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
const handleEdit = (row: Vendor) => {
  currentRow.value = { ...row }
  formMode.value = 'edit'
  formVisible.value = true
}

// 详情
const handleView = (row: Vendor) => {
  currentRow.value = { ...row }
  detailVisible.value = true
}

// 删除
const handleDelete = async (row: Vendor) => {
  try {
    await ElMessageBox.confirm(
      `确认删除厂商"${row.vendorName}"吗？`,
      '提示',
      { type: 'warning' }
    )
    await deleteVendor(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

// 状态切换
const handleStatusChange = async (row: Vendor) => {
  try {
    await updateVendorStatus(row.id, row.status)
    ElMessage.success(row.status === 'active' ? '已启用' : '已禁用')
  } catch (error) {
    row.status = row.status === 'active' ? 'inactive' : 'active'
  }
}

// 表单提交成功
const handleFormSuccess = () => {
  loadData()
}

onMounted(() => {
  loadData()
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
  width: 280px;
}

.search-select {
  width: 160px;
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
.vendor-table {
  --el-table-bg-color: transparent !important;
}

.vendor-code {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-text-secondary);
  background: var(--color-bg-light);
  padding: 4px 10px;
  border-radius: 6px;
}

.vendor-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.vendor-name {
  font-weight: 500;
  color: var(--color-text-primary);
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

/* 详情弹窗 */
.detail-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.detail-container {
  background: var(--color-bg-light);
  border-radius: 12px;
  padding: 20px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-item .label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.detail-item .value {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
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

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
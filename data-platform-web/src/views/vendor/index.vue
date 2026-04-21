<template>
  <div class="page-container">
    <div class="card">
      <!-- 搜索区域 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索厂商名称/编码"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        
        <el-select v-model="searchForm.status" placeholder="状态" clearable>
          <el-option label="启用" value="active" />
          <el-option label="禁用" value="inactive" />
        </el-select>
        
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          新增厂商
        </el-button>
        <el-button @click="loadData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="vendorCode" label="厂商编码" width="120" />
        <el-table-column prop="vendorName" label="厂商名称" min-width="150" />
        <el-table-column prop="vendorType" label="厂商类型" width="100" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="contactEmail" label="邮箱" min-width="180" />
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
        <el-table-column label="操作" width="200" fixed="right">
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
    </div>

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
    >
      <div class="detail-container">
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
          <span class="value">{{ currentRow?.vendorType }}</span>
        </div>
        <div class="detail-item">
          <span class="label">联系人</span>
          <span class="value">{{ currentRow?.contactPerson }}</span>
        </div>
        <div class="detail-item">
          <span class="label">联系电话</span>
          <span class="value">{{ currentRow?.contactPhone }}</span>
        </div>
        <div class="detail-item">
          <span class="label">邮箱</span>
          <span class="value">{{ currentRow?.contactEmail }}</span>
        </div>
        <div class="detail-item">
          <span class="label">状态</span>
          <span class="value">
            <el-tag :type="currentRow?.status === 'active' ? 'success' : 'danger'">
              {{ currentRow?.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </span>
        </div>
        <div class="detail-item">
          <span class="label">创建时间</span>
          <span class="value">{{ currentRow?.createdAt }}</span>
        </div>
        <div class="detail-item">
          <span class="label">更新时间</span>
          <span class="value">{{ currentRow?.updatedAt }}</span>
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
import { Search, Plus, Refresh } from '@element-plus/icons-vue'
import { getVendorList, updateVendorStatus, deleteVendor } from '@/api/vendor'
import type { Vendor } from '@/types'
import VendorForm from './components/VendorForm.vue'

// 搜索表单
const searchForm = reactive({
  keyword: '',
  status: ''
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

// 认证方式映射
const authTypeMap: Record<string, string> = {
  none: '无',
  basic: 'Basic',
  oauth: 'OAuth',
  api_key: 'API Key',
  hmac: 'HMAC'
}

const getAuthTypeLabel = (type?: string) => {
  return authTypeMap[type || 'none'] || type
}

const getAuthTypeTag = (type?: string) => {
  const map: Record<string, string> = {
    none: 'info',
    basic: 'warning',
    oauth: 'success',
    api_key: 'primary',
    hmac: 'danger'
  }
  return map[type || 'none'] || 'info'
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      status: searchForm.status || undefined
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
      `确认删除厂商"${row.name}"吗？`,
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
.table-toolbar {
  margin-bottom: 16px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { request } from '@/utils/request'
import { Search, Refresh, Delete, View } from '@element-plus/icons-vue'

interface OperationLog {
  id: number
  username: string
  module: string
  operation: string
  method: string
  params: string
  result: string
  ip: string
  duration: number
  status: string
  createdAt: string
}

const loading = ref(false)
const tableData = ref<OperationLog[]>([])
const total = ref(0)
const pagination = ref({ currentPage: 1, pageSize: 10 })

const searchForm = ref({
  username: '',
  module: '',
  operation: '',
  dateRange: [] as string[]
})

const moduleOptions = [
  { label: '全部', value: '' },
  { label: '用户管理', value: 'user' },
  { label: '角色管理', value: 'role' },
  { label: '租户管理', value: 'tenant' },
  { label: '厂商管理', value: 'vendor' },
  { label: '调用方管理', value: 'caller' },
  { label: '计费管理', value: 'billing' },
  { label: '系统管理', value: 'system' }
]

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/v1/log/list', {
      params: {
        page: pagination.value.currentPage,
        pageSize: pagination.value.pageSize,
        ...searchForm.value
      }
    })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e: any) {
    console.error('获取操作日志失败:', e)
    // 模拟数据
    tableData.value = [
      { id: 1, username: 'admin', module: 'user', operation: '新增用户', method: 'POST /api/v1/user', params: '{"username":"test"}', result: 'success', ip: '192.168.1.100', duration: 150, status: 'success', createdAt: '2026-04-20 21:00:00' },
      { id: 2, username: 'admin', module: 'tenant', operation: '修改租户', method: 'PUT /api/v1/tenant/1', params: '{"name":"新租户"}', result: 'success', ip: '192.168.1.100', duration: 80, status: 'success', createdAt: '2026-04-20 20:30:00' },
      { id: 3, username: 'operator', module: 'vendor', operation: '查询厂商', method: 'GET /api/v1/vendor/list', params: '{}', result: 'success', ip: '192.168.1.101', duration: 45, status: 'success', createdAt: '2026-04-20 20:00:00' },
      { id: 4, username: 'admin', module: 'billing', operation: '导出账单', method: 'POST /api/v1/billing/export', params: '{"date":"2026-04"}', result: 'success', ip: '192.168.1.100', duration: 5200, status: 'success', createdAt: '2026-04-20 19:00:00' },
      { id: 5, username: 'guest', module: 'user', operation: '查询用户', method: 'GET /api/v1/user/1', params: '{}', result: 'error', ip: '192.168.1.102', duration: 30, status: 'failed', createdAt: '2026-04-20 18:30:00' }
    ]
    total.value = 256
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.currentPage = 1
  fetchList()
}

const handleReset = () => {
  searchForm.value = { username: '', module: '', operation: '', dateRange: [] }
  pagination.value.currentPage = 1
  fetchList()
}

const handleViewDetail = (row: OperationLog) => {
  ElMessage.info(`查看详情: ${row.operation}`)
}

const getStatusType = (status: string) => {
  return status === 'success' ? 'success' : 'danger'
}

const getStatusText = (status: string) => {
  return status === 'success' ? '成功' : '失败'
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="log-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="操作人">
          <el-input v-model="searchForm.username" placeholder="请输入操作人" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="searchForm.module" placeholder="请选择" clearable style="width: 140px">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-input v-model="searchForm.operation" placeholder="请输入操作" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="searchForm.dateRange" type="datetimerange" range-separator="至"
            start-placeholder="开始时间" end-placeholder="结束时间" style="width: 360px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">总操作次数</div>
          <div class="stat-value">1,256</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">成功操作</div>
          <div class="stat-value text-success">1,230</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">失败操作</div>
          <div class="stat-value text-danger">26</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">平均响应时间</div>
          <div class="stat-value">128ms</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 表格 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>操作日志列表</span>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="操作人" width="100" />
        <el-table-column prop="module" label="模块" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operation" label="操作" width="120" />
        <el-table-column prop="method" label="请求方法" width="180" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP地址" width="130" />
        <el-table-column prop="duration" label="耗时" width="80">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.duration > 1000 }">{{ row.duration }}ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="180" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleViewDetail(row)">详情</el-button>
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
  </div>
</template>

<style scoped>
.log-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-row {
  margin-bottom: 8px;
}

.stat-card {
  text-align: center;
}

.stat-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.text-success {
  color: #67C23A;
}

.text-danger {
  color: #F56C6C;
}
</style>
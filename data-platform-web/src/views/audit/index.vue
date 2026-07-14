<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>操作日志</h2>
        <p class="header-desc">系统操作审计与日志查询</p>
      </div>
      <el-button type="primary" :loading="exporting" @click="handleExport">导出日志</el-button>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <StatCard label="总操作次数" :value="statsData.totalCount" />
      </el-col>
      <el-col :span="6">
        <StatCard label="成功操作" :value="statsData.successCount" variant="success" />
      </el-col>
      <el-col :span="6">
        <StatCard label="失败操作" :value="statsData.failCount" variant="danger" />
      </el-col>
      <el-col :span="6">
        <StatCard label="平均响应时间" :value="statsData.avgDuration" suffix="ms" variant="warning" />
      </el-col>
    </el-row>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.username" placeholder="搜索操作人" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.module" placeholder="模块" clearable class="search-select">
            <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input v-model="searchForm.operation" placeholder="搜索操作" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-date-picker
            v-model="searchForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            class="date-picker"
          />
        </div>
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="操作人" width="100">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ row.username?.charAt(0)?.toUpperCase() }}</div>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operation" label="操作" width="120" />
        <el-table-column prop="method" label="请求方法" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="method-cell">{{ row.method }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP地址" width="130">
          <template #default="{ row }">
            <span class="ip-cell">{{ row.ip }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="80">
          <template #default="{ row }">
            <span :class="['duration-cell', { slow: row.duration > 1000 }]">{{ row.duration }}ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusTextLocalized(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="180">
          <template #default="{ row }">
            <span class="time-cell">{{ row.createdAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="操作日志详情" width="720px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="操作人">{{ detail.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ getStatusTextLocalized(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ detail.operation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法" :span="2">{{ detail.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ detail.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.duration ?? 0 }}ms</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2"><pre>{{ detail.params || '-' }}</pre></el-descriptions-item>
        <el-descriptions-item label="响应结果" :span="2"><pre>{{ detail.result || '-' }}</pre></el-descriptions-item>
        <el-descriptions-item label="操作时间" :span="2">{{ detail.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { exportLogs, getLogById, getLogList, getLogStats } from '@/api/log'
import { getStatusType as getTagType, getStatusText } from '@/utils/status'
import { extractPageData } from '@/utils/pagination'

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
const route = useRoute()
const exporting = ref(false)
const tableData = ref<OperationLog[]>([])
const detailVisible = ref(false)
const detail = ref<OperationLog | null>(null)
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })

const searchForm = reactive({
  username: typeof route.query.keyword === 'string' ? route.query.keyword : '',
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

const statsData = ref({
  totalCount: 0,
  successCount: 0,
  failCount: 0,
  avgDuration: 0
})

const fetchStats = async () => {
  try {
    const res = await getLogStats({
      startTime: searchForm.dateRange[0],
      endTime: searchForm.dateRange[1]
    })
    if (res.data) {
      statsData.value = {
        totalCount: res.data.totalCount || 0,
        successCount: res.data.successCount || 0,
        failCount: res.data.failCount || 0,
        avgDuration: res.data.avgDuration || 0
      }
    }
  } catch {
    // Keep default values on error
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getLogList({
      page: pagination.currentPage,
      pageSize: pagination.pageSize,
      keyword: searchForm.username || undefined,
      module: searchForm.module || undefined,
      operation: searchForm.operation || undefined,
      startTime: searchForm.dateRange[0],
      endTime: searchForm.dateRange[1]
    })
    const page = extractPageData<OperationLog>(res)
    tableData.value = page.list
    total.value = page.total
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.currentPage = 1
  Promise.all([fetchStats(), fetchList()])
}

const handleReset = () => {
  searchForm.username = ''
  searchForm.module = ''
  searchForm.operation = ''
  searchForm.dateRange = []
  pagination.currentPage = 1
  Promise.all([fetchStats(), fetchList()])
}

const handleViewDetail = async (row: OperationLog) => {
  const response = await getLogById(row.id)
  detail.value = response.data as OperationLog
  detailVisible.value = true
}

const handleExport = async () => {
  exporting.value = true
  try {
    const blob = await exportLogs({
      module: searchForm.module || undefined,
      operation: searchForm.operation || undefined,
      startTime: searchForm.dateRange[0],
      endTime: searchForm.dateRange[1]
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `operation-logs-${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

const getStatusType = (status: string) => getTagType('enabled', status)
const getStatusTextLocalized = (status: string) => getStatusText('enabled', status)

onMounted(async () => {
  await Promise.all([fetchStats(), fetchList()])
})
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }

.stats-row { margin-bottom: 20px; }

.search-card { margin-bottom: 20px; }
.search-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.search-inputs { display: flex; gap: 12px; flex: 1; flex-wrap: wrap; }
.search-input { width: 160px; }
.search-select { width: 140px; }
.date-picker { width: 320px; }
.search-btn-group { display: flex; gap: 10px; }

.user-cell { display: flex; align-items: center; gap: 8px; }
.user-avatar { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, var(--color-primary), #6366F1); border-radius: 6px; color: #0A1628; font-weight: 600; font-size: 12px; }
.method-cell { font-family: var(--font-mono); font-size: 12px; color: var(--color-primary); background: rgba(0, 212, 170, 0.1); padding: 2px 8px; border-radius: 4px; }
.ip-cell { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); }
.duration-cell { font-family: var(--font-mono); }
.duration-cell.slow { color: #F56C6C; }
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }

.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>

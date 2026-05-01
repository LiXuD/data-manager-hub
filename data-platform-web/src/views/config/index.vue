<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>操作日志</h2>
        <p class="header-desc">系统操作审计与日志查询</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10 9 9 9 8 9"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-label">总操作次数</div>
            <div class="stat-value">1,256</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon success">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
              <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-label">成功操作</div>
            <div class="stat-value success">1,230</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon danger">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-label">失败操作</div>
            <div class="stat-value danger">26</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon warning">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-label">平均响应时间</div>
            <div class="stat-value">128ms</div>
          </div>
        </div>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { request } from '@/utils/request'
import { getStatusType as getTagType, getStatusText } from '@/utils/status'

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
const pagination = reactive({ currentPage: 1, pageSize: 10 })

const searchForm = reactive({
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

interface LogListResponse {
  data?: { records?: OperationLog[]; total?: number } | OperationLog[]
  total?: number
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get<LogListResponse>('/log/list', {
      params: {
        page: pagination.currentPage,
        pageSize: pagination.pageSize,
        ...searchForm
      }
    })
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
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.currentPage = 1
  fetchList()
}

const handleReset = () => {
  searchForm.username = ''
  searchForm.module = ''
  searchForm.operation = ''
  searchForm.dateRange = []
  pagination.currentPage = 1
  fetchList()
}

const handleViewDetail = (row: OperationLog) => {
  ElMessage.info(`查看详情: ${row.operation}`)
}

const getStatusType = (status: string) => getTagType('enabled', status)
const getStatusTextLocalized = (status: string) => getStatusText('enabled', status)

onMounted(() => { fetchList() })
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }

.stats-row { margin-bottom: 20px; }
.stat-card {
  background: var(--color-bg-lighter);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  transition: all 0.3s ease;
}
.stat-card:hover { border-color: var(--color-primary); transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0, 212, 170, 0.1); }

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--color-primary), #00A080);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-icon svg { width: 24px; height: 24px; color: #0A1628; }
.stat-icon.success { background: linear-gradient(135deg, #67C23A, #5Daf34); }
.stat-icon.warning { background: linear-gradient(135deg, #E6A23C, #d48806); }
.stat-icon.danger { background: linear-gradient(135deg, #F56C6C, #e04545); }

.stat-info { flex: 1; min-width: 0; }
.stat-label { font-size: 13px; color: var(--color-text-tertiary); margin-bottom: 6px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--color-text-primary); font-family: var(--font-mono); }
.stat-value.success { color: #67C23A; }
.stat-value.danger { color: #F56C6C; }

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
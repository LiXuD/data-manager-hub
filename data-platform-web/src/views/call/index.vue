<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>调用记录</h2>
        <p class="header-desc">查看API调用明细与统计分析</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2v4m0 12v4M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M2 12h4m12 0h4M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-label">总调用次数</div>
            <div class="stat-value">1,234</div>
            <div class="stat-trend up">较昨日 +12%</div>
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
            <div class="stat-label">成功调用</div>
            <div class="stat-value success">1,180</div>
            <div class="stat-trend">成功率 95.6%</div>
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
            <div class="stat-value">256ms</div>
            <div class="stat-trend down">较昨日 -5%</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon info">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="1" x2="12" y2="23"/>
              <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-label">今日消费</div>
            <div class="stat-value">¥1,234.56</div>
            <div class="stat-trend up">较昨日 +8%</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.callerName" placeholder="搜索调用方" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-input v-model="searchForm.vendorName" placeholder="搜索厂商" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.dataType" placeholder="数据类型" clearable class="search-select">
            <el-option v-for="item in dataTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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
          <el-button type="success" @click="handleExport">导出</el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="callerName" label="调用方" width="120" />
        <el-table-column prop="vendorName" label="厂商" width="120" />
        <el-table-column prop="dataType" label="数据类型" width="120" />
        <el-table-column prop="apiName" label="API名称" width="150" />
        <el-table-column prop="requestTime" label="调用时间" width="180">
          <template #default="{ row }">
            <span class="time-cell">{{ row.requestTime }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="responseTime" label="耗时" width="100">
          <template #default="{ row }">
            <span :class="['response-time', { slow: row.responseTime > 1000 }]">{{ row.responseTime }}ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusTextLocalized(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="费用" width="100">
          <template #default="{ row }">
            <span class="cost-cell">¥{{ row.cost?.toFixed(2) || '0.00' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="追踪ID" width="150">
          <template #default="{ row }">
            <span class="trace-id">{{ row.traceId }}</span>
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
import { getCallRecordList } from '@/api/call'
import { extractPageData } from '@/utils/pagination'
import { getStatusType as getTagType, getStatusText } from '@/utils/status'

interface CallRecord {
  id: number
  callerName: string
  vendorName: string
  dataType: string
  apiName: string
  requestTime: string
  responseTime: number
  status: string
  cost: number
  traceId: string
}

const loading = ref(false)
const tableData = ref<CallRecord[]>([])
const total = ref(0)
const searchForm = reactive({
  callerName: '',
  vendorName: '',
  dataType: '',
  status: '',
  dateRange: [] as string[]
})
const pagination = reactive({
  currentPage: 1,
  pageSize: 10
})

const statusOptions = [
  { label: '成功', value: 'success' },
  { label: '失败', value: 'failed' },
  { label: '超时', value: 'timeout' },
  { label: '限流', value: 'rate_limited' }
]

const dataTypeOptions = [
  { label: '工商信息', value: 'BUSINESS_INFO' },
  { label: '企业征信', value: 'CREDIT_QUERY' },
  { label: '诉讼信息', value: 'LITIGATION' },
  { label: '新闻舆情', value: 'NEWS' }
]

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getCallRecordList({
      page: pagination.currentPage,
      pageSize: pagination.pageSize
    })
    const { list, total: totalCount } = extractPageData<CallRecord>(res)
    tableData.value = list
    total.value = totalCount
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.currentPage = 1
  fetchList()
}

const handleReset = () => {
  searchForm.callerName = ''
  searchForm.vendorName = ''
  searchForm.dataType = ''
  searchForm.status = ''
  searchForm.dateRange = []
  pagination.currentPage = 1
  fetchList()
}

const handleExport = () => {
  ElMessage.info('导出功能开发中...')
}

const getStatusType = (status: string) => getTagType('call', status)
const getStatusTextLocalized = (status: string) => getStatusText('call', status)

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
.stat-icon.info { background: linear-gradient(135deg, #409EFF, #337ecc); }

.stat-info { flex: 1; min-width: 0; }
.stat-label { font-size: 13px; color: var(--color-text-tertiary); margin-bottom: 6px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--color-text-primary); font-family: var(--font-mono); }
.stat-value.success { color: #67C23A; }
.stat-trend { font-size: 12px; color: var(--color-text-tertiary); margin-top: 4px; }
.stat-trend.up { color: #F56C6C; }
.stat-trend.down { color: #67C23A; }

.search-card { margin-bottom: 20px; }
.search-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.search-inputs { display: flex; gap: 12px; flex: 1; flex-wrap: wrap; }
.search-input { width: 160px; }
.search-select { width: 140px; }
.date-picker { width: 320px; }
.search-btn-group { display: flex; gap: 10px; }

.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.response-time { font-family: var(--font-mono); font-size: 13px; }
.response-time.slow { color: #F56C6C; }
.cost-cell { color: var(--color-primary); font-weight: 500; font-family: var(--font-mono); }
.trace-id { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 2px 8px; border-radius: 4px; }

.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
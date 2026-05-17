<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>调用记录</h2>
        <p class="header-desc">查看API调用明细与统计分析</p>
      </div>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <StatCard label="总调用次数" :value="statsData.totalCount" />
      </el-col>
      <el-col :span="6">
        <StatCard label="成功调用" :value="statsData.successCount" variant="success" />
      </el-col>
      <el-col :span="6">
        <StatCard label="平均响应时间" :value="statsData.avgResponseTime" suffix="ms" variant="warning" />
      </el-col>
      <el-col :span="6">
        <StatCard label="今日消费" :value="statsData.todayCost.toFixed(2)" prefix="¥" variant="info" />
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
        <el-table-column prop="callTime" label="调用时间" width="180">
          <template #default="{ row }">
            <span class="time-cell">{{ row.callTime }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="responseTime" label="耗时" width="100">
          <template #default="{ row }">
            <span :class="['response-time', { slow: row.responseTime > 1000 }]">{{ row.responseTime ?? 0 }}ms</span>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCallRecordList, getCallStats } from '@/api/call'
import type { CallRecord } from '@/types'
import { useCacheStore } from '@/stores/cache'
import { extractPageData } from '@/utils/pagination'
import { getStatusType as getTagType, getStatusText } from '@/utils/status'
// StatCard is globally registered by unplugin-vue-components

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

const cacheStore = useCacheStore()
const dataTypeOptions = computed(() =>
  cacheStore.dataTypeOptions.map(dt => ({
    label: dt.dataTypeName,
    value: dt.dataTypeCode
  }))
)

const statsData = ref({
  totalCount: 0,
  successCount: 0,
  avgResponseTime: 0,
  todayCost: 0
})

const fetchStats = async () => {
  try {
    const res = await getCallStats({})
    if (res.data) {
      statsData.value = {
        totalCount: res.data.totalCount || 0,
        successCount: res.data.successCount || 0,
        avgResponseTime: res.data.avgResponseTime || 0,
        todayCost: res.data.todayCost || 0
      }
    }
  } catch {
    // Keep default values on error
  }
}

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

onMounted(async () => {
  await Promise.all([
    cacheStore.loadDataTypes(),
    fetchStats()
  ])
  fetchList()
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

.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.response-time { font-family: var(--font-mono); font-size: 13px; }
.response-time.slow { color: #F56C6C; }
.cost-cell { color: var(--color-primary); font-weight: 500; font-family: var(--font-mono); }
.trace-id { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 2px 8px; border-radius: 4px; }

.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
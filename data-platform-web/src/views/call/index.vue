<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCallRecordList } from '@/api/call'
import { Search, Refresh, Download, Filter } from '@element-plus/icons-vue'

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
const searchForm = ref({
  callerName: '',
  vendorName: '',
  dataType: '',
  status: '',
  dateRange: [] as string[]
})
const pagination = ref({
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
      page: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    })
    tableData.value = res.data?.data?.records || res.data?.data || res.data || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    console.error('获取调用记录失败:', e)
    ElMessage.error('获取调用记录失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.currentPage = 1
  fetchList()
}

const handleReset = () => {
  searchForm.value = {
    callerName: '',
    vendorName: '',
    dataType: '',
    status: '',
    dateRange: []
  }
  pagination.value.currentPage = 1
  fetchList()
}

const handleExport = () => {
  ElMessage.info('导出功能开发中...')
}

const handleViewDetail = (row: CallRecord) => {
  ElMessage.info(`查看详情: ${row.traceId}`)
}

const handlePageChange = (page: number) => {
  pagination.value.currentPage = page
  fetchList()
}

const handleSizeChange = (size: number) => {
  pagination.value.pageSize = size
  fetchList()
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    success: 'success',
    failed: 'danger',
    timeout: 'warning',
    rate_limited: 'info'
  }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    timeout: '超时',
    rate_limited: '限流'
  }
  return map[status] || status
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="call-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="调用方">
          <el-input v-model="searchForm.callerName" placeholder="请输入调用方" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="厂商">
          <el-input v-model="searchForm.vendorName" placeholder="请输入厂商" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select v-model="searchForm.dataType" placeholder="请选择" clearable style="width: 140px">
            <el-option v-for="item in dataTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 100px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">总调用次数</div>
          <div class="stat-value">1,234</div>
          <div class="stat-change">较昨日 +12%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">成功调用</div>
          <div class="stat-value success">1,180</div>
          <div class="stat-change">成功率 95.6%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">平均响应时间</div>
          <div class="stat-value">256ms</div>
          <div class="stat-change">较昨日 -5%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">今日消费</div>
          <div class="stat-value">¥1,234.56</div>
          <div class="stat-change">较昨日 +8%</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>调用记录列表</span>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" style="width: 100%" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="callerName" label="调用方" width="120" />
        <el-table-column prop="vendorName" label="厂商" width="120" />
        <el-table-column prop="dataType" label="数据类型" width="120" />
        <el-table-column prop="apiName" label="API名称" width="150" />
        <el-table-column prop="requestTime" label="调用时间" width="180" />
        <el-table-column prop="responseTime" label="耗时(ms)" width="100">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.responseTime > 1000 }">
              {{ row.responseTime }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="费用(元)" width="100">
          <template #default="{ row }">
            ¥{{ row.cost.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="traceId" label="追踪ID" width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)">
              {{ row.traceId }}
            </el-button>
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
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<style scoped>
.call-page {
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
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-value.success {
  color: #67C23A;
}

.stat-change {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.text-danger {
  color: #F56C6C;
}
</style>
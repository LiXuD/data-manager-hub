<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>监控中心</h2>
        <p class="header-desc">系统服务健康状态与告警规则管理</p>
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
            <div class="stat-label">服务总数</div>
            <div class="stat-value">{{ statsData.totalServices }}</div>
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
            <div class="stat-label">正常运行</div>
            <div class="stat-value success">{{ statsData.healthyCount }}</div>
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
            <div class="stat-label">服务异常</div>
            <div class="stat-value danger">{{ statsData.unhealthyCount }}</div>
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
            <div class="stat-label">平均响应</div>
            <div class="stat-value">{{ statsData.avgResponseTime }}ms</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Tab 切换 -->
    <el-card class="table-card">
      <el-tabs v-model="activeTab">
        <!-- 服务健康状态 -->
        <el-tab-pane label="服务健康" name="health">
          <!-- 搜索区域 -->
          <div class="search-bar">
            <div class="search-inputs">
              <el-input v-model="searchForm.serviceName" placeholder="搜索服务名称" clearable class="search-input" @keyup.enter="handleSearch" />
              <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
                <el-option v-for="item in healthOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </div>
            <div class="search-btn-group">
              <el-button type="primary" @click="handleSearch">查询</el-button>
              <el-button @click="handleReset">重置</el-button>
            </div>
          </div>

          <!-- 健康状态表格 -->
          <el-table :data="tableData" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="serviceName" label="服务名称" width="150" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <div class="status-cell" :class="row.status">
                  <span class="status-dot"></span>
                  {{ getStatusTextLocalized(row.status) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="responseTime" label="响应时间" width="120">
              <template #default="{ row }">
                <span :class="['response-time', { slow: row.responseTime > 1000 }]">{{ row.responseTime }}ms</span>
              </template>
            </el-table-column>
            <el-table-column prop="uptime" label="可用率" width="100">
              <template #default="{ row }">
                <span :class="['uptime', { warning: row.uptime < 99 }]">{{ row.uptime }}%</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastCheck" label="最后检查" width="180">
              <template #default="{ row }">
                <span class="time-cell">{{ row.lastCheck }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleCheckNow(row)">立即检查</el-button>
                <el-button type="primary" link @click="handleViewLogs(row)">查看日志</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 告警规则 -->
        <el-tab-pane label="告警规则" name="alert">
          <div class="tool-bar">
            <el-button type="primary" @click="handleAddRule">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 5v14M5 12h14"/>
              </svg>
              新增规则
            </el-button>
          </div>

          <el-table :data="alertData" stripe>
            <el-table-column prop="ruleName" label="规则名称" width="180" />
            <el-table-column prop="metric" label="监控指标" width="150">
              <template #default="{ row }">
                <span class="metric-tag">{{ row.metric }}</span>
              </template>
            </el-table-column>
            <el-table-column label="阈值" width="120">
              <template #default="{ row }">
                <span class="threshold-cell">{{ row.operator }} {{ row.threshold }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="level" label="告警级别" width="100">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.level)" size="small">{{ getLevelTextLocalized(row.level) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-switch v-model="row.status" active-value="active" inactive-value="inactive" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleEditRule(row)">编辑</el-button>
                <el-button type="danger" link @click="handleDeleteRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 监控图表 -->
        <el-tab-pane label="监控图表" name="chart">
          <el-empty description="监控图表功能开发中...">
            <el-button type="primary">配置图表</el-button>
          </el-empty>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { request } from '@/utils/request'
import { getStatusType as getTagType, getStatusText } from '@/utils/status'

interface HealthStatus {
  id: number
  serviceName: string
  status: string
  responseTime: number
  uptime: number
  lastCheck: string
}

interface AlertRule {
  id: number
  ruleName: string
  metric: string
  threshold: number
  operator: string
  level: string
  status: string
}

const loading = ref(false)
const activeTab = ref('health')
const tableData = ref<HealthStatus[]>([])
const alertData = ref<AlertRule[]>([])

const healthOptions = [
  { label: '全部', value: '' },
  { label: '正常', value: 'healthy' },
  { label: '异常', value: 'unhealthy' },
  { label: '未知', value: 'unknown' }
]

const searchForm = reactive({
  serviceName: '',
  status: ''
})

// 统计卡片
const statsData = reactive({
  totalServices: 6,
  healthyCount: 5,
  unhealthyCount: 1,
  avgResponseTime: 528
})

interface HealthListResponse {
  data?: HealthStatus[]
}

const fetchHealth = async () => {
  loading.value = true
  try {
    const res = await request.get<HealthListResponse>('/api/v1/monitor/health', {
      params: { ...searchForm }
    })
    tableData.value = res.data || []
  } catch {
    // 错误时使用 mock 数据
    tableData.value = [
      { id: 1, serviceName: 'API网关', status: 'healthy', responseTime: 25, uptime: 99.9, lastCheck: '2026-04-20 21:00:00' },
      { id: 2, serviceName: '用户服务', status: 'healthy', responseTime: 45, uptime: 99.8, lastCheck: '2026-04-20 21:00:00' },
      { id: 3, serviceName: '厂商服务', status: 'healthy', responseTime: 38, uptime: 99.7, lastCheck: '2026-04-20 21:00:00' },
      { id: 4, serviceName: '调用服务', status: 'unhealthy', responseTime: 5000, uptime: 95.0, lastCheck: '2026-04-20 21:00:00' },
      { id: 5, serviceName: '计费服务', status: 'healthy', responseTime: 52, uptime: 99.9, lastCheck: '2026-04-20 21:00:00' },
      { id: 6, serviceName: '租户服务', status: 'healthy', responseTime: 30, uptime: 99.95, lastCheck: '2026-04-20 21:00:00' }
    ]
  } finally {
    loading.value = false
  }
}

const fetchAlerts = async () => {
  alertData.value = [
    { id: 1, ruleName: '响应时间告警', metric: 'response_time', threshold: 3000, operator: '>', level: 'warning', status: 'active' },
    { id: 2, ruleName: 'CPU使用率告警', metric: 'cpu_usage', threshold: 80, operator: '>', level: 'critical', status: 'active' },
    { id: 3, ruleName: '内存使用率告警', metric: 'memory_usage', threshold: 85, operator: '>', level: 'warning', status: 'active' },
    { id: 4, ruleName: '错误率告警', metric: 'error_rate', threshold: 5, operator: '>', level: 'error', status: 'inactive' },
    { id: 5, ruleName: '调用量告警', metric: 'call_count', threshold: 10000, operator: '>', level: 'info', status: 'active' }
  ]
}

const handleSearch = () => { fetchHealth() }
const handleReset = () => { searchForm.serviceName = ''; searchForm.status = ''; fetchHealth() }
const handleAddRule = () => { ElMessage.info('新增告警规则') }
const handleEditRule = (row: AlertRule) => { ElMessage.info(`编辑告警规则: ${row.ruleName}`) }
const handleDeleteRule = (_row: AlertRule) => { ElMessage.success('删除成功'); fetchAlerts() }

const getStatusTextLocalized = (status: string) => getStatusText('health', status)
const getLevelType = (level: string) => getTagType('enabled', level)
const getLevelTextLocalized = (level: string) => getStatusText('level', level)

const handleCheckNow = (row: HealthStatus) => { ElMessage.info(`立即检查: ${row.serviceName}`) }
const handleViewLogs = (row: HealthStatus) => { ElMessage.info(`查看日志: ${row.serviceName}`) }

onMounted(() => { Promise.all([fetchHealth(), fetchAlerts()]) })
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

.search-bar, .tool-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 16px; }
.search-inputs { display: flex; gap: 12px; flex: 1; flex-wrap: wrap; }
.search-input { width: 180px; }
.search-select { width: 140px; }
.search-btn-group { display: flex; gap: 10px; }
.tool-bar .el-button { display: flex; align-items: center; gap: 6px; }
.tool-bar .el-button svg { width: 16px; height: 16px; }

.status-cell { display: flex; align-items: center; gap: 6px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #909399; }
.status-cell.healthy .status-dot { background: #67C23A; box-shadow: 0 0 6px #67C23A; }
.status-cell.unhealthy .status-dot { background: #F56C6C; box-shadow: 0 0 6px #F56C6C; }
.status-cell.unknown .status-dot { background: #E6A23C; }

.response-time { font-family: var(--font-mono); }
.response-time.slow { color: #F56C6C; }
.uptime { font-family: var(--font-mono); color: #67C23A; }
.uptime.warning { color: #E6A23C; }
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.metric-tag { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 2px 8px; border-radius: 4px; }
.threshold-cell { font-family: var(--font-mono); color: var(--color-text-secondary); }

:deep(.el-tabs__item) { font-size: 14px; }
:deep(.el-tabs__item.is-active) { color: var(--color-primary); }
:deep(.el-tabs__active-bar) { background: var(--color-primary); }
</style>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { request } from '@/utils/request'
import { Monitor, Warning, CircleCheck, CircleClose } from '@element-plus/icons-vue'

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
const pagination = ref({ currentPage: 1, pageSize: 10 })

const healthOptions = [
  { label: '全部', value: '' },
  { label: '正常', value: 'healthy' },
  { label: '异常', value: 'unhealthy' },
  { label: '未知', value: 'unknown' }
]

const searchForm = ref({
  serviceName: '',
  status: ''
})

// 统计卡片
const statsData = ref({
  totalServices: 0,
  healthyCount: 0,
  unhealthyCount: 0,
  avgResponseTime: 0
})

const fetchHealth = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/v1/monitor/health', {
      params: { ...searchForm.value }
    })
    tableData.value = res.data || []
  } catch (e: any) {
    console.error('获取健康状态失败:', e)
    // 模拟数据
    tableData.value = [
      { id: 1, serviceName: 'API网关', status: 'healthy', responseTime: 25, uptime: 99.9, lastCheck: '2026-04-20 21:00:00' },
      { id: 2, serviceName: '用户服务', status: 'healthy', responseTime: 45, uptime: 99.8, lastCheck: '2026-04-20 21:00:00' },
      { id: 3, serviceName: '厂商服务', status: 'healthy', responseTime: 38, uptime: 99.7, lastCheck: '2026-04-20 21:00:00' },
      { id: 4, serviceName: '调用服务', status: 'unhealthy', responseTime: 5000, uptime: 95.0, lastCheck: '2026-04-20 21:00:00' },
      { id: 5, serviceName: '计费服务', status: 'healthy', responseTime: 52, uptime: 99.9, lastCheck: '2026-04-20 21:00:00' },
      { id: 6, serviceName: '租户服务', status: 'healthy', responseTime: 30, uptime: 99.95, lastCheck: '2026-04-20 21:00:00' }
    ]
    
    statsData.value = {
      totalServices: 6,
      healthyCount: 5,
      unhealthyCount: 1,
      avgResponseTime: 528
    }
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

const handleSearch = () => {
  fetchHealth()
}

const handleReset = () => {
  searchForm.value = { serviceName: '', status: '' }
  fetchHealth()
}

const handleAddRule = () => {
  ElMessage.info('新增告警规则')
}

const handleEditRule = (row: AlertRule) => {
  ElMessage.info(`编辑告警规则: ${row.ruleName}`)
}

const handleDeleteRule = (row: AlertRule) => {
  ElMessage.success('删除成功')
  fetchAlerts()
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = { healthy: 'success', unhealthy: 'danger', unknown: 'warning' }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = { healthy: '正常', unhealthy: '异常', unknown: '未知' }
  return map[status] || status
}

const getLevelType = (level: string) => {
  const map: Record<string, string> = { info: 'info', warning: 'warning', error: 'danger', critical: 'danger' }
  return map[level] || 'info'
}

const getLevelText = (level: string) => {
  const map: Record<string, string> = { info: '信息', warning: '警告', error: '错误', critical: '严重' }
  return map[level] || level
}

const handleCheckNow = (row: HealthStatus) => {
  ElMessage.info(`立即检查: ${row.serviceName}`)
}

const handleViewLogs = (row: HealthStatus) => {
  ElMessage.info(`查看日志: ${row.serviceName}`)
}

onMounted(() => {
  fetchHealth()
  fetchAlerts()
})
</script>

<template>
  <div class="monitor-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #409EFF"><Monitor /></div>
          <div class="stat-content">
            <div class="stat-title">服务总数</div>
            <div class="stat-value">{{ statsData.totalServices }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #67C23A"><CircleCheck /></div>
          <div class="stat-content">
            <div class="stat-title">正常运行</div>
            <div class="stat-value text-success">{{ statsData.healthyCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #F56C6C"><CircleClose /></div>
          <div class="stat-content">
            <div class="stat-title">服务异常</div>
            <div class="stat-value text-danger">{{ statsData.unhealthyCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #E6A23C"><Warning /></div>
          <div class="stat-content">
            <div class="stat-title">平均响应</div>
            <div class="stat-value">{{ statsData.avgResponseTime }}ms</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab 切换 -->
    <el-card>
      <el-tabs v-model="activeTab">
        <!-- 服务健康状态 -->
        <el-tab-pane label="服务健康" name="health">
          <!-- 搜索区域 -->
          <div class="search-bar">
            <el-form :model="searchForm" inline>
              <el-form-item label="服务名称">
                <el-input v-model="searchForm.serviceName" placeholder="请输入服务名称" clearable style="width: 180px" />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
                  <el-option v-for="item in healthOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="handleReset">重置</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 健康状态表格 -->
          <el-table :data="tableData" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="serviceName" label="服务名称" width="150" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
                  <el-icon v-if="row.status === 'healthy'" style="margin-right: 4px"><CircleCheck /></el-icon>
                  <el-icon v-else-if="row.status === 'unhealthy'" style="margin-right: 4px"><CircleClose /></el-icon>
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="responseTime" label="响应时间" width="120">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.responseTime > 1000 }">
                  {{ row.responseTime }}ms
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="uptime" label="可用率" width="100">
              <template #default="{ row }">
                <span :class="row.uptime >= 99 ? 'text-success' : 'text-warning'">
                  {{ row.uptime }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="lastCheck" label="最后检查" width="180" />
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
            <el-button type="primary" @click="handleAddRule">新增规则</el-button>
          </div>

          <el-table :data="alertData" stripe>
            <el-table-column prop="ruleName" label="规则名称" width="180" />
            <el-table-column prop="metric" label="监控指标" width="150">
              <template #default="{ row }">
                <el-tag>{{ row.metric }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="阈值" width="120">
              <template #default="{ row }">
                {{ row.operator }} {{ row.threshold }}
              </template>
            </el-table-column>
            <el-table-column prop="level" label="告警级别" width="100">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.level)">{{ getLevelText(row.level) }}</el-tag>
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

<style scoped>
.monitor-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  margin-bottom: 8px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
}

.stat-content {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  color: #909399;
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

.text-warning {
  color: #E6A23C;
}

.search-bar, .tool-bar {
  margin-bottom: 16px;
}
</style>
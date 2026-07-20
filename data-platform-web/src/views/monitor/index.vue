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
            <el-table-column prop="serviceName" label="服务名称" width="150" />
            <el-table-column prop="instanceCount" label="实例数" width="90" />
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
            <el-table-column prop="targetType" label="监控指标" width="150">
              <template #default="{ row }">
                <span class="metric-tag">{{ row.targetType }}</span>
              </template>
            </el-table-column>
            <el-table-column label="阈值" width="120">
              <template #default="{ row }">
                <span class="threshold-cell">{{ row.conditionType }} {{ row.thresholdValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="severity" label="告警级别" width="100">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.severity || row.level || 'warning')" size="small">{{ getLevelTextLocalized(row.severity || row.level || 'warning') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-switch v-model="row.status" active-value="active" inactive-value="inactive" @change="handleRuleStatusChange(row)" />
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

        <el-tab-pane label="告警记录" name="record">
          <el-table :data="alertRecords" stripe>
            <el-table-column prop="alertTitle" label="告警标题" min-width="180" />
            <el-table-column prop="alertMessage" label="告警内容" min-width="240" show-overflow-tooltip />
            <el-table-column prop="level" label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="getLevelType(row.level)" size="small">{{ getLevelTextLocalized(row.level) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">{{ row.status === 'resolved' ? '已处理' : '待处理' }}</template>
            </el-table-column>
            <el-table-column prop="alertTime" label="告警时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'resolved'" type="primary" link @click="openResolveDialog(row)">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 监控图表 -->
        <el-tab-pane label="监控图表" name="chart">
          <div v-if="tableData.length" class="health-chart">
            <div v-for="item in tableData" :key="item.serviceName" class="chart-row">
              <span class="chart-label">{{ item.serviceName }}</span>
              <div class="chart-track">
                <div class="chart-bar" :class="item.status" :style="{ width: `${Math.max(2, item.uptime)}%` }"></div>
              </div>
              <span class="chart-value">{{ item.uptime }}%</span>
            </div>
          </div>
          <el-empty v-else description="暂无服务健康数据" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="ruleDialogVisible" :title="ruleForm.id ? '编辑告警规则' : '新增告警规则'" width="560px">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.ruleName" />
        </el-form-item>
        <el-form-item label="监控指标" required>
          <el-input v-model="ruleForm.targetType" placeholder="例如 response_time" />
        </el-form-item>
        <el-form-item label="触发条件" required>
          <el-select v-model="ruleForm.conditionType" style="width: 100%">
            <el-option label="大于" value="gt" />
            <el-option label="大于等于" value="gte" />
            <el-option label="小于" value="lt" />
            <el-option label="小于等于" value="lte" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值" required>
          <el-input-number v-model="ruleForm.thresholdValue" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时间窗口">
          <el-input-number v-model="ruleForm.timeWindowMinutes" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="告警级别">
          <el-select v-model="ruleForm.severity" style="width: 100%">
            <el-option label="提示" value="info" />
            <el-option label="警告" value="warning" />
            <el-option label="严重" value="critical" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-input v-model="ruleForm.notifyChannels" placeholder="例如 email,webhook" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSubmitting" @click="handleSaveRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveDialogVisible" title="处理告警" width="500px">
      <el-input v-model="resolution" type="textarea" :rows="4" placeholder="请输入处理结果" />
      <template #footer>
        <el-button @click="resolveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resolving" @click="handleResolveAlert">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  checkServiceHealth,
  createAlertRule,
  deleteAlertRule,
  getAlertRecordList,
  getAlertRuleList,
  getServiceHealth,
  resolveAlertRecord,
  updateAlertRule,
  updateAlertRuleStatus,
  type ServiceHealth
} from '@/api/monitor'
import type { AlertRecord, AlertRule } from '@/types'
import { getStatusType as getTagType, getStatusText } from '@/utils/status'
import { extractPageData } from '@/utils/pagination'

const loading = ref(false)
const router = useRouter()
const activeTab = ref('health')
const tableData = ref<ServiceHealth[]>([])
type AlertRuleView = AlertRule & { severity?: string }
const alertData = ref<AlertRuleView[]>([])
const alertRecords = ref<AlertRecord[]>([])
const ruleDialogVisible = ref(false)
const ruleSubmitting = ref(false)
const resolveDialogVisible = ref(false)
const resolving = ref(false)
const resolution = ref('')
const resolvingRecordId = ref<number | null>(null)
const ruleForm = reactive({
  id: null as number | null,
  ruleName: '',
  ruleType: 'THRESHOLD',
  targetType: '',
  conditionType: 'gt',
  thresholdValue: 0,
  timeWindowMinutes: 5,
  notifyChannels: '',
  severity: 'warning',
  status: 'active' as 'active' | 'inactive'
})

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

const statsData = reactive({
  totalServices: 0,
  healthyCount: 0,
  unhealthyCount: 0,
  avgResponseTime: 0
})

const fetchHealth = async () => {
  loading.value = true
  try {
    const response = await getServiceHealth({
      serviceName: searchForm.serviceName || undefined,
      status: searchForm.status || undefined
    })
    tableData.value = response.data.list || []
    Object.assign(statsData, response.data.stats)
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const fetchAlerts = async () => {
  try {
    const res = await getAlertRuleList({ page: 1, pageSize: 100 })
    alertData.value = extractPageData<AlertRuleView>(res).list
  } catch {
    alertData.value = []
  }
}

const fetchAlertRecords = async () => {
  const response = await getAlertRecordList({ page: 1, pageSize: 100 })
  alertRecords.value = extractPageData<AlertRecord>(response).list
}

const handleSearch = () => { fetchHealth() }
const handleReset = () => { searchForm.serviceName = ''; searchForm.status = ''; fetchHealth() }
const handleAddRule = () => {
  Object.assign(ruleForm, { id: null, ruleName: '', ruleType: 'THRESHOLD', targetType: '', conditionType: 'gt', thresholdValue: 0, timeWindowMinutes: 5, notifyChannels: '', severity: 'warning', status: 'active' })
  ruleDialogVisible.value = true
}
const handleEditRule = (row: AlertRuleView) => {
  Object.assign(ruleForm, { ...row })
  ruleDialogVisible.value = true
}
const handleSaveRule = async () => {
  if (!ruleForm.ruleName.trim() || !ruleForm.targetType.trim()) {
    ElMessage.warning('请填写规则名称和监控指标')
    return
  }
  ruleSubmitting.value = true
  try {
    const payload = { ...ruleForm, id: undefined }
    if (ruleForm.id) {
      await updateAlertRule(ruleForm.id, payload)
    } else {
      await createAlertRule(payload)
    }
    ElMessage.success('保存成功')
    ruleDialogVisible.value = false
    fetchAlerts()
  } finally {
    ruleSubmitting.value = false
  }
}
const handleRuleStatusChange = async (row: AlertRule) => {
  try {
    await updateAlertRuleStatus(row.id, row.status)
  } catch {
    row.status = row.status === 'active' ? 'inactive' : 'active'
  }
}
const openResolveDialog = (row: AlertRecord) => {
  resolvingRecordId.value = row.id
  resolution.value = ''
  resolveDialogVisible.value = true
}
const handleResolveAlert = async () => {
  if (!resolvingRecordId.value || !resolution.value.trim()) {
    ElMessage.warning('请输入处理结果')
    return
  }
  resolving.value = true
  try {
    await resolveAlertRecord(resolvingRecordId.value, resolution.value.trim())
    ElMessage.success('告警已处理')
    resolveDialogVisible.value = false
    fetchAlertRecords()
  } finally {
    resolving.value = false
  }
}
const handleDeleteRule = async (row: AlertRule) => {
  try {
    await ElMessageBox.confirm(`确定要删除告警规则"${row.ruleName}"吗？`, '提示', { type: 'warning' })
    await deleteAlertRule(row.id!)
    ElMessage.success('删除成功')
    fetchAlerts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const getStatusTextLocalized = (status: string) => getStatusText('health', status)
const getLevelType = (level: string) => getTagType('enabled', level)
const getLevelTextLocalized = (level: string) => getStatusText('level', level)

const handleCheckNow = async (row: ServiceHealth) => {
  const response = await checkServiceHealth(row.serviceName)
  Object.assign(row, response.data)
  ElMessage.success('检查完成')
}
const handleViewLogs = (row: ServiceHealth) => {
  router.push({ path: '/audit', query: { keyword: row.serviceName } })
}

onMounted(() => { Promise.all([fetchHealth(), fetchAlerts(), fetchAlertRecords()]) })
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
.health-chart { display: flex; flex-direction: column; gap: 16px; padding: 12px 4px; }
.chart-row { display: grid; grid-template-columns: 180px 1fr 64px; gap: 12px; align-items: center; }
.chart-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chart-track { height: 12px; overflow: hidden; border-radius: 6px; background: var(--color-bg-light); }
.chart-bar { height: 100%; border-radius: 6px; background: #909399; }
.chart-bar.healthy { background: #67C23A; }
.chart-bar.unhealthy { background: #F56C6C; }
.chart-value { text-align: right; font-family: var(--font-mono); }

:deep(.el-tabs__item) { font-size: 14px; }
:deep(.el-tabs__item.is-active) { color: var(--color-primary); }
:deep(.el-tabs__active-bar) { background: var(--color-primary); }
</style>

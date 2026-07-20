<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent
} from 'echarts/components'
import { graphic, init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { getCallDimensionStats, getCallRecordList, getCallStats } from '@/api/call'
import { getVendorAll } from '@/api/vendor'
import { getCallerList } from '@/api/caller'
import { extractPageData } from '@/utils/pagination'
import type { CallRecord, CallerDTO, Vendor } from '@/types'

use([
  LineChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  CanvasRenderer
])

const router = useRouter()

const statsData = ref([
  { label: '调用总量', value: '0', unit: '次', note: '成功率 0%' },
  { label: '今日调用', value: '0', unit: '次', note: '实时统计' },
  { label: '活跃厂商', value: '0', unit: '家', note: '启用中' },
  { label: '调用方', value: '0', unit: '个', note: '已接入' }
])

// 快捷入口
const quickActions = [
  { label: '新增厂商', icon: 'plus', route: '/vendor' },
  { label: '调用查询', icon: 'search', route: '/call' },
  { label: '账单查看', icon: 'wallet', route: '/billing' },
  { label: '告警处理', icon: 'alarm', route: '/monitor' }
]

const recentCalls = ref<Array<{ id: number; vendor: string; type: string; caller: string; status: string; time: string; cost: number }>>([])
const hourlyCounts = ref<number[]>(Array(24).fill(0))
const vendorDistribution = ref<Array<{ value: number; name: string }>>([])

// 图表引用
const trendChartRef = ref<HTMLDivElement>()
const vendorChartRef = ref<HTMLDivElement>()
let trendChart: ECharts | null = null
let vendorChart: ECharts | null = null

// 初始化趋势图表
const initTrendChart = () => {
  if (!trendChartRef.value) return

  trendChart = init(trendChartRef.value)

  const xAxisData = Array.from({ length: 24 }, (_, i) => `${i}:00`)

  trendChart.setOption({
    backgroundColor: 'transparent',
    grid: {
      left: 50,
      right: 20,
      top: 40,
      bottom: 30
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(21, 31, 50, 0.95)',
      borderColor: 'rgba(0, 212, 170, 0.3)',
      borderWidth: 1,
      textStyle: { color: '#E8EDF3' },
      formatter: (params: { name: string; value: number }[]) => {
        const data = params[0]
        return `<div style="font-family: var(--font-mono)">${data.name}<br/><span style="color: #00D4AA">调用量:</span> ${data.value.toLocaleString()}</div>`
      }
    },
    xAxis: {
      type: 'category',
      data: xAxisData,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      axisLabel: { color: '#5A6A7E', fontSize: 11 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: '#5A6A7E', fontSize: 11, formatter: (v: number) => v.toLocaleString() },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.04)' } }
    },
    series: [{
      name: '调用量',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#00D4AA', width: 2 },
      areaStyle: {
        color: new graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0, 212, 170, 0.25)' },
          { offset: 1, color: 'rgba(0, 212, 170, 0)' }
        ])
      },
      data: hourlyCounts.value
    }]
  })
}

// 初始化厂商分布图表
const initVendorChart = () => {
  if (!vendorChartRef.value) return

  vendorChart = init(vendorChartRef.value)

  vendorChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(21, 31, 50, 0.95)',
      borderColor: 'rgba(0, 212, 170, 0.3)',
      borderWidth: 1,
      textStyle: { color: '#E8EDF3' }
    },
    series: [{
      type: 'pie',
      radius: ['55%', '80%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#151F32', borderWidth: 2 },
      label: { show: false },
      emphasis: {
        label: { show: false },
        itemStyle: { shadowBlur: 20, shadowColor: 'rgba(0, 212, 170, 0.5)' }
      },
      labelLine: { show: false },
      data: vendorDistribution.value
    }]
  })
}

// 窗口大小变化时重新调整图表
const handleResize = () => {
  trendChart?.resize()
  vendorChart?.resize()
}

const formatDateTime = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const loadDashboard = async () => {
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const [allStatsResponse, todayStatsResponse, todayRecordsResponse, recentRecordsResponse, vendorsResponse, callersResponse] = await Promise.all([
    getCallDimensionStats({}),
    getCallStats({ startTime: formatDateTime(todayStart), endTime: formatDateTime(now) }),
    getCallRecordList({ page: 1, pageSize: 100, startTime: formatDateTime(todayStart), endTime: formatDateTime(now) }),
    getCallRecordList({ page: 1, pageSize: 5 }),
    getVendorAll(),
    getCallerList({ page: 1, pageSize: 100 })
  ])

  const allStats = allStatsResponse.data as Record<string, any>
  const todayStats = todayStatsResponse.data as Record<string, any>
  const vendors = vendorsResponse.data || []
  const callersPage = extractPageData<CallerDTO>(callersResponse)
  const activeVendors = vendors.filter(vendor => vendor.status === 'active' || vendor.status === 'enabled')
  const totalCount = Number(allStats.totalCount || 0)
  const successCount = Number(allStats.successCount || 0)
  const successRate = totalCount ? (successCount / totalCount * 100).toFixed(1) : '0.0'

  statsData.value = [
    { label: '调用总量', value: totalCount.toLocaleString(), unit: '次', note: `成功率 ${successRate}%` },
    { label: '今日调用', value: Number(todayStats.totalCount || 0).toLocaleString(), unit: '次', note: '实时统计' },
    { label: '活跃厂商', value: activeVendors.length.toLocaleString(), unit: '家', note: `共 ${vendors.length} 家` },
    { label: '调用方', value: callersPage.total.toLocaleString(), unit: '个', note: '已接入' }
  ]

  const vendorNameMap = new Map<string, string>(vendors.map((vendor: Vendor) => [vendor.vendorCode, vendor.vendorName]))
  const callerNameMap = new Map<number, string>(callersPage.list.filter(item => item.id != null).map(item => [item.id!, item.callerName]))
  const records = extractPageData<CallRecord>(todayRecordsResponse).list
  const recentRecords = extractPageData<CallRecord>(recentRecordsResponse).list
  recentCalls.value = recentRecords.map(record => ({
    id: record.id,
    vendor: vendorNameMap.get(record.vendorCode) || record.vendorCode || String(record.vendorId),
    type: record.dataType || record.dataTypeCode || '-',
    caller: callerNameMap.get(record.callerId) || String(record.callerId),
    status: record.success ? 'success' : 'failed',
    time: (record.callTime || record.createdAt || '').slice(11, 19),
    cost: Number(record.cost || 0)
  }))

  hourlyCounts.value = Array(24).fill(0)
  records.forEach(record => {
    const hour = Number((record.callTime || record.createdAt || '').slice(11, 13))
    if (Number.isInteger(hour) && hour >= 0 && hour < 24) hourlyCounts.value[hour] += 1
  })

  const byVendor = Array.isArray(allStats.byVendor) ? allStats.byVendor : []
  vendorDistribution.value = byVendor.map((item: Record<string, any>) => {
    const code = String(item.vendorCode || item.vendor_code || '未知厂商')
    return { name: vendorNameMap.get(code) || code, value: Number(item.totalCount || 0) }
  })
}

onMounted(async () => {
  await loadDashboard()
  await nextTick()
  initTrendChart()
  initVendorChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  trendChart?.dispose()
  vendorChart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="dashboard">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>数据概览</h2>
        <p class="header-desc">实时监控数据调用情况</p>
      </div>
      <div class="header-time">
        <span class="time-label">当前时间</span>
        <span class="time-value">{{ new Date().toLocaleString('zh-CN', { weekday: 'short', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }}</span>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div v-for="(stat, index) in statsData" :key="index" class="stat-card">
        <div class="stat-header">
          <span class="stat-label">{{ stat.label }}</span>
          <span class="stat-trend up">{{ stat.note }}</span>
        </div>
        <div class="stat-value">
          <span class="value-number">{{ stat.value }}</span>
          <span class="value-unit">{{ stat.unit }}</span>
        </div>
        <div class="stat-sparkline">
          <svg viewBox="0 0 100 30" preserveAspectRatio="none">
            <path d="M0,25 Q10,20 20,22 T40,18 T60,15 T80,10 T100,5" fill="none" stroke="currentColor" stroke-width="2" opacity="0.5"/>
            <path d="M0,25 Q10,20 20,22 T40,18 T60,15 T80,10 T100,5 L100,30 L0,30 Z" fill="currentColor" opacity="0.1"/>
          </svg>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="quick-actions">
      <div v-for="action in quickActions" :key="action.label" class="action-item" @click="router.push(action.route)">
        <div class="action-icon">
          <svg v-if="action.icon === 'plus'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          <svg v-else-if="action.icon === 'search'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
          <svg v-else-if="action.icon === 'wallet'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 12V8H6a2 2 0 0 1-2-2c0-1.1.9-2 2-2h12v4M4 6v12c0 1.1.9 2 2 2h14v-4"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>
          </svg>
        </div>
        <span class="action-label">{{ action.label }}</span>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="charts-row">
      <!-- 调用趋势图 -->
      <el-card class="chart-card trend-chart">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">今日最近调用时段分布</span>
          </div>
        </template>
        <div ref="trendChartRef" class="chart-container"></div>
      </el-card>

      <!-- 厂商分布图 -->
      <el-card class="chart-card vendor-chart">
        <template #header>
          <div class="chart-header">
            <span class="chart-title">厂商调用分布</span>
          </div>
        </template>
        <div ref="vendorChartRef" class="chart-container"></div>
      </el-card>
    </div>

    <!-- 底部数据表格 -->
    <el-card class="recent-card">
      <template #header>
        <div class="chart-header">
          <span class="chart-title">最近调用记录</span>
          <el-button type="primary" link @click="router.push('/call')">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentCalls" stripe class="recent-table">
        <el-table-column prop="vendor" label="厂商" width="120">
          <template #default="{ row }">
            <div class="vendor-cell">
              <span class="vendor-name">{{ row.vendor }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="数据类型" min-width="120" />
        <el-table-column prop="caller" label="调用方" width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="time" label="调用时间" width="100">
          <template #default="{ row }">
            <span class="time-cell">{{ row.time }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="费用" width="80" align="right">
          <template #default="{ row }">
            <span class="cost-cell">¥{{ row.cost.toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1600px;
  margin: 0 auto;
}

/* 页面标题 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 28px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px;
  letter-spacing: -0.02em;
}

.header-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
}

.header-time {
  text-align: right;
  padding: 12px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
}

.time-label {
  display: block;
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-bottom: 4px;
}

.time-value {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
  color: var(--color-primary);
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  background: linear-gradient(135deg, var(--color-surface) 0%, var(--color-bg-light) 100%);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 24px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--color-primary), transparent);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.stat-card:hover {
  border-color: rgba(0, 212, 170, 0.3);
  transform: translateY(-4px);
  box-shadow: 0 12px 40px -12px rgba(0, 212, 170, 0.2);
}

.stat-card:hover::before {
  opacity: 1;
}

.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 20px;
}

.stat-trend.up {
  color: var(--color-success);
  background: rgba(16, 185, 129, 0.15);
}

.stat-trend.down {
  color: var(--color-danger);
  background: rgba(239, 68, 68, 0.15);
}

.stat-trend svg {
  width: 14px;
  height: 14px;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.value-number {
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.value-unit {
  font-size: 14px;
  color: var(--color-text-tertiary);
}

.stat-sparkline {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 40px;
  opacity: 0.6;
}

.stat-sparkline svg {
  width: 100%;
  height: 100%;
  color: var(--color-primary);
}

/* 快捷入口 */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-item:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-light);
}

.action-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary), #00B894);
  border-radius: 12px;
  color: #0A1628;
  transition: transform 0.2s ease;
}

.action-item:hover .action-icon {
  transform: scale(1.1);
}

.action-icon svg {
  width: 24px;
  height: 24px;
}

.action-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
}

/* 图表区域 */
.charts-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.chart-card {
  background: var(--color-surface) !important;
  border: 1px solid var(--color-border) !important;
  border-radius: 16px !important;
}

.chart-card :deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
}

.chart-card :deep(.el-card__body) {
  padding: 0;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.chart-tabs {
  display: flex;
  gap: 4px;
  background: var(--color-bg-light);
  padding: 4px;
  border-radius: 8px;
}

.chart-tab {
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.chart-tab.active {
  background: var(--color-primary);
  color: #0A1628;
}

.chart-container {
  height: 280px;
  padding: 16px;
}

/* 最近调用表格 */
.recent-card {
  background: var(--color-surface) !important;
  border: 1px solid var(--color-border) !important;
  border-radius: 16px !important;
}

.recent-card :deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border);
}

.recent-table {
  --el-table-bg-color: transparent !important;
}

.vendor-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.vendor-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.success {
  background: rgba(16, 185, 129, 0.15);
  color: var(--color-success);
}

.status-badge.failed {
  background: rgba(239, 68, 68, 0.15);
  color: var(--color-danger);
}

.time-cell {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-text-secondary);
}

.cost-cell {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--color-primary);
}

/* 响应式 */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .quick-actions {
    grid-template-columns: repeat(2, 1fr);
  }

  .charts-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .quick-actions {
    grid-template-columns: repeat(2, 1fr);
  }

  .page-header {
    flex-direction: column;
    gap: 16px;
  }

  .header-time {
    width: 100%;
    text-align: left;
  }
}

/* 减少动画支持 */
@media (prefers-reduced-motion: reduce) {
  .stat-card {
    transition: none;
  }

  .stat-card:hover {
    transform: none;
    box-shadow: none;
  }

  .action-item:hover .action-icon {
    transform: none;
  }
}
</style>

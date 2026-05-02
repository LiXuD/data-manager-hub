<template>
  <el-dialog
    :model-value="modelValue"
    title=""
    width="880px"
    class="stats-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="stats-header">
        <div class="header-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 20V10M12 20V4M6 20v-6"/>
          </svg>
        </div>
        <div class="header-content">
          <h3>接口调用统计</h3>
          <p class="interface-name">{{ interfaceData?.interfaceName }}</p>
        </div>
        <div class="time-range">
          <el-date-picker
            v-model="timeRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :shortcuts="shortcuts"
            @change="loadStats"
          />
        </div>
      </div>
    </template>

    <div class="stats-content" v-loading="loading">
      <div class="stats-grid">
        <div class="stat-card total">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
            </svg>
          </div>
          <div class="stat-body">
            <span class="stat-value">{{ formatNumber(stats?.totalCalls || 0) }}</span>
            <span class="stat-label">总调用次数</span>
          </div>
        </div>

        <div class="stat-card success">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 6L9 17l-5-5"/>
            </svg>
          </div>
          <div class="stat-body">
            <span class="stat-value">{{ formatNumber(stats?.successCalls || 0) }}</span>
            <span class="stat-label">成功调用</span>
          </div>
        </div>

        <div class="stat-card failed">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </div>
          <div class="stat-body">
            <span class="stat-value">{{ formatNumber(failedCalls) }}</span>
            <span class="stat-label">失败调用</span>
          </div>
        </div>

        <div class="stat-card latency">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 6v6l4 2"/>
            </svg>
          </div>
          <div class="stat-body">
            <span class="stat-value">{{ (stats?.avgLatency || 0).toFixed(1) }}<span class="unit">ms</span></span>
            <span class="stat-label">平均延迟</span>
          </div>
        </div>

        <div class="stat-card rate">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
            </svg>
          </div>
          <div class="stat-body">
            <span class="stat-value">{{ successRate.toFixed(1) }}<span class="unit">%</span></span>
            <span class="stat-label">成功率</span>
          </div>
        </div>

        <div class="stat-card slow">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
            </svg>
          </div>
          <div class="stat-body">
            <span class="stat-value">{{ formatNumber(stats?.slowCalls || 0) }}</span>
            <span class="stat-label">慢调用</span>
          </div>
        </div>
      </div>

      <div class="chart-section">
        <div class="section-header">
          <h4>调用趋势</h4>
          <div class="legend">
            <span class="legend-item"><i class="dot success"></i>成功</span>
            <span class="legend-item"><i class="dot failed"></i>失败</span>
          </div>
        </div>
        <div class="chart-container">
          <div v-if="dailyStats.length === 0" class="empty-chart">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M3 3v18h18"/>
              <path d="M7 16l4-4 4 4 5-6"/>
            </svg>
            <p>暂无数据</p>
          </div>
          <div v-else class="bar-chart">
            <div class="chart-y-axis">
              <span>{{ maxValue }}</span>
              <span>{{ Math.round(maxValue / 2) }}</span>
              <span>0</span>
            </div>
            <div class="chart-bars">
              <div
                v-for="item in chartData"
                :key="item.date"
                class="bar-group"
              >
                <div class="bar-wrapper">
                  <div
                    class="bar success"
                    :style="{ height: item.successHeight + '%' }"
                    :title="`成功: ${item.successCalls}`"
                  ></div>
                  <div
                    class="bar failed"
                    :style="{ height: item.failedHeight + '%' }"
                    :title="`失败: ${item.failedCalls}`"
                  ></div>
                </div>
                <span class="bar-label">{{ formatDate(item.date) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { getInterfaceStats, getInterfaceDailyStats, type InterfaceStats, type DailyStatItem } from '@/api/interface'
import type { ApiInterface } from '@/types'

interface Props {
  modelValue: boolean
  interfaceData?: ApiInterface | null
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const stats = ref<InterfaceStats | null>(null)
const dailyStats = ref<DailyStatItem[]>([])

const timeRange = ref<[string, string] | null>(null)

// 快捷选项
const shortcuts = [
  {
    text: '最近7天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 7)
      return [start, end]
    }
  },
  {
    text: '最近30天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24 * 30)
      return [start, end]
    }
  }
]

// 计算失败次数
const failedCalls = computed(() => {
  if (!stats.value) return 0
  return Math.max(0, (stats.value.totalCalls || 0) - (stats.value.successCalls || 0))
})

// 计算成功率
const successRate = computed(() => {
  if (!stats.value || !stats.value.totalCalls) return 0
  return ((stats.value.successCalls || 0) / stats.value.totalCalls) * 100
})

// 计算每日最大值
const maxValue = computed(() => {
  if (dailyStats.value.length === 0) return 100
  let max = 0
  for (const d of dailyStats.value) {
    const val = d.total_calls || 0
    if (val > max) max = val
  }
  return Math.ceil(max / 10) * 10 || 100
})

// 预计算图表数据
const chartData = computed(() => {
  const max = maxValue.value
  return dailyStats.value.map(item => {
    const successCalls = item.success_calls || 0
    const failedCalls = Math.max(0, (item.total_calls || 0) - successCalls)
    return {
      date: item.date,
      successCalls,
      failedCalls,
      successHeight: max > 0 ? (successCalls / max) * 100 : 0,
      failedHeight: max > 0 ? (failedCalls / max) * 100 : 0
    }
  })
})

// 格式化日期
const formatDate = (date: string) => {
  const d = new Date(date)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

// 格式化数字
const formatNumber = (num: number) => {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

// 加载统计数据
const loadStats = async () => {
  if (!props.interfaceData?.id) return

  loading.value = true
  try {
    const params: { startTime?: string; endTime?: string } = {}
    if (timeRange.value) {
      params.startTime = timeRange.value[0] + ' 00:00:00'
      params.endTime = timeRange.value[1] + ' 23:59:59'
    }

    const [statsRes, dailyRes] = await Promise.all([
      getInterfaceStats(props.interfaceData.id, params),
      getInterfaceDailyStats(props.interfaceData.id, params)
    ])

    stats.value = statsRes
    dailyStats.value = dailyRes || []
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 关闭弹窗
const handleClose = () => {
  emit('update:modelValue', false)
}

// 监听弹窗打开
watch(() => props.modelValue, (val) => {
  if (val && props.interfaceData?.id) {
    const end = new Date()
    const start = new Date()
    start.setTime(start.getTime() - 24 * 60 * 60 * 1000 * 7)
    timeRange.value = [
      start.toISOString().split('T')[0],
      end.toISOString().split('T')[0]
    ]
    loadStats()
  } else if (!val) {
    stats.value = null
    dailyStats.value = []
  }
})
</script>

<style scoped>
.stats-dialog :deep(.el-dialog__header) {
  padding: 0;
  margin: 0;
}

.stats-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.stats-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 12px 12px 0 0;
  margin: -24px -24px 24px;
}

.header-icon {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #00d4ff 0%, #7c3aed 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-icon svg {
  width: 24px;
  height: 24px;
  color: white;
}

.header-content {
  flex: 1;
}

.header-content h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}

.interface-name {
  margin: 4px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.time-range :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.1);
  border: none;
  box-shadow: none;
}

.time-range :deep(.el-input__inner) {
  color: #fff;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--color-bg-light);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid transparent;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.stat-card.total { border-color: rgba(99, 102, 241, 0.3); }
.stat-card.total .stat-icon { background: linear-gradient(135deg, #6366f1, #8b5cf6); }

.stat-card.success { border-color: rgba(16, 185, 129, 0.3); }
.stat-card.success .stat-icon { background: linear-gradient(135deg, #10b981, #34d399); }

.stat-card.failed { border-color: rgba(239, 68, 68, 0.3); }
.stat-card.failed .stat-icon { background: linear-gradient(135deg, #ef4444, #f87171); }

.stat-card.latency { border-color: rgba(245, 158, 11, 0.3); }
.stat-card.latency .stat-icon { background: linear-gradient(135deg, #f59e0b, #fbbf24); }

.stat-card.rate { border-color: rgba(6, 182, 212, 0.3); }
.stat-card.rate .stat-icon { background: linear-gradient(135deg, #06b6d4, #22d3ee); }

.stat-card.slow { border-color: rgba(236, 72, 153, 0.3); }
.stat-card.slow .stat-icon { background: linear-gradient(135deg, #ec4899, #f472b6); }

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 22px;
  height: 22px;
  color: white;
}

.stat-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.stat-value .unit {
  font-size: 14px;
  font-weight: 500;
  margin-left: 2px;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.chart-section {
  background: var(--color-bg-light);
  border-radius: 12px;
  padding: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.legend {
  display: flex;
  gap: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.legend-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.legend-item .dot.success { background: #10b981; }
.legend-item .dot.failed { background: #ef4444; }

.chart-container {
  height: 220px;
  position: relative;
}

.empty-chart {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
}

.empty-chart svg {
  width: 48px;
  height: 48px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-chart p {
  margin: 0;
  font-size: 14px;
}

.bar-chart {
  display: flex;
  height: 100%;
}

.chart-y-axis {
  width: 40px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-right: 8px;
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-align: right;
}

.chart-bars {
  flex: 1;
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding-bottom: 24px;
  overflow-x: auto;
}

.bar-group {
  flex: 1;
  min-width: 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.bar-wrapper {
  width: 100%;
  height: 160px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
}

.bar {
  width: 10px;
  border-radius: 3px 3px 0 0;
  transition: height 0.3s ease;
  min-height: 2px;
}

.bar.success {
  background: linear-gradient(180deg, #34d399 0%, #10b981 100%);
}

.bar.failed {
  background: linear-gradient(180deg, #f87171 0%, #ef4444 100%);
}

.bar-label {
  margin-top: 8px;
  font-size: 10px;
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stats-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .time-range {
    width: 100%;
  }

  .time-range :deep(.el-date-editor) {
    width: 100%;
  }
}
</style>

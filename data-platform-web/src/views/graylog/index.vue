<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>灰度发布</h2>
        <p class="header-desc">管理灰度发布规则与流量分配</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增规则
      </el-button>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <StatCard label="灰度规则总数" :value="statsData.totalCount" />
      </el-col>
      <el-col :span="6">
        <StatCard label="启用中" :value="statsData.activeCount" variant="success" />
      </el-col>
      <el-col :span="6">
        <StatCard label="即将过期" :value="statsData.expiringCount" variant="warning" />
      </el-col>
      <el-col :span="6">
        <StatCard label="已过期" :value="statsData.expiredCount" variant="info" />
      </el-col>
    </el-row>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.keyword" placeholder="搜索规则名称/服务名称" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option v-for="item in GRAY_RULE_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="ruleName" label="规则名称" width="180" />
        <el-table-column prop="serviceName" label="服务名称" width="150">
          <template #default="{ row }">
            <span class="service-tag">{{ row.serviceName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="目标版本" width="120">
          <template #default="{ row }">
            <span class="version-tag">v{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="流量权重" width="120">
          <template #default="{ row }">
            <div class="weight-cell">
              <el-progress :percentage="row.weight" :stroke-width="8" :show-text="false" :color="'#00D4AA'" />
              <span class="weight-value">{{ row.weight }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="conditionType" label="匹配条件" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ getStatusText('conditionType', row.conditionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'active'"
              active-text="启用"
              inactive-text="禁用"
              @change="(val: string | number | boolean) => handleStatusChange(row, val === true)"
            />
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="160">
          <template #default="{ row }">
            <span class="time-cell">{{ row.startTime?.slice(0, 10) || '-' }} ~ {{ row.endTime?.slice(0, 10) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
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

    <!-- 表单弹窗 -->
    <GrayRuleForm
      v-model="formVisible"
      :form-data="currentRow"
      :mode="formMode"
      @success="fetchList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getGrayRuleList, deleteGrayRule, updateGrayRuleStatus } from '@/api/graylog'
import type { GrayRule } from '@/api/graylog'
import { GRAY_RULE_STATUS_OPTIONS, GRAY_RULE_STATUS } from '@/constants'
import { getStatusText } from '@/utils/status'
import GrayRuleForm from './components/GrayRuleForm.vue'

const loading = ref(false)
const tableData = ref<GrayRule[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })

const searchForm = reactive({
  keyword: '',
  status: ''
})

const formVisible = ref(false)
const formMode = ref<'add' | 'edit'>('add')
const currentRow = ref<GrayRule | null>(null)


const fetchList = async () => {
  loading.value = true
  try {
    const res = await getGrayRuleList({
      page: pagination.currentPage,
      pageSize: pagination.pageSize,
      ...searchForm
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

const handleSearch = () => { pagination.currentPage = 1; fetchList() }
const handleReset = () => { searchForm.keyword = ''; searchForm.status = ''; pagination.currentPage = 1; fetchList() }

const handleAdd = () => {
  formMode.value = 'add'
  currentRow.value = null
  formVisible.value = true
}

const handleEdit = (row: GrayRule) => {
  formMode.value = 'edit'
  currentRow.value = { ...row }
  formVisible.value = true
}

const handleDelete = async (row: GrayRule) => {
  try {
    await ElMessageBox.confirm(`确定要删除规则"${row.ruleName}"吗？`, '提示', { type: 'warning' })
    await deleteGrayRule(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleStatusChange = async (row: GrayRule, enabled: boolean) => {
  const newStatus = enabled ? GRAY_RULE_STATUS.ACTIVE : GRAY_RULE_STATUS.INACTIVE
  const oldStatus = row.status
  row.status = newStatus
  try {
    await updateGrayRuleStatus(row.id, newStatus)
    ElMessage.success(enabled ? '已启用' : '已禁用')
  } catch {
    row.status = oldStatus
    ElMessage.error('状态更新失败')
  }
}

type RuleCategory = 'active' | 'expiring' | 'expired'

const getRuleCategory = (rule: GrayRule, today: string, now: Date): RuleCategory => {
  if (rule.status === GRAY_RULE_STATUS.EXPIRED) return 'expired'
  if (rule.status !== GRAY_RULE_STATUS.ACTIVE) return 'expired'

  const endDate = rule.endTime?.slice(0, 10)
  if (!endDate) return 'active'
  if (endDate < today) return 'expired'

  const daysUntilExpiry = Math.ceil((new Date(endDate).getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  return daysUntilExpiry <= 7 ? 'expiring' : 'active'
}

const statsData = computed(() => {
  const now = new Date()
  const today = now.toISOString().slice(0, 10)

  const counts = { active: 0, expiring: 0, expired: 0 }
  tableData.value.forEach(rule => {
    counts[getRuleCategory(rule, today, now)]++
  })

  return {
    totalCount: tableData.value.length,
    activeCount: counts.active,
    expiringCount: counts.expiring,
    expiredCount: counts.expired
  }
})

onMounted(() => { fetchList() })
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }
.page-header .el-button { display: flex; align-items: center; gap: 8px; }
.page-header .el-button svg { width: 18px; height: 18px; }

.stats-row { margin-bottom: 20px; }

.search-card { margin-bottom: 20px; }
.search-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.search-inputs { display: flex; gap: 12px; flex: 1; flex-wrap: wrap; }
.search-input { width: 200px; }
.search-select { width: 140px; }
.search-btn-group { display: flex; gap: 10px; }

.service-tag { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-primary); }
.version-tag { font-family: var(--font-mono); font-size: 12px; color: var(--color-primary); background: rgba(0, 212, 170, 0.1); padding: 2px 8px; border-radius: 4px; }
.weight-cell { display: flex; align-items: center; gap: 8px; }
.weight-cell .el-progress { flex: 1; }
.weight-value { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); min-width: 36px; }
.time-cell { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); }

.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }

:deep(.el-progress-bar__outer) { background: var(--color-bg-light); }
</style>

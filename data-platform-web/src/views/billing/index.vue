<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>计费管理</h2>
        <p class="header-desc">查看账单明细与管理计费规则</p>
      </div>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <StatCard label="本月总消费" :value="statsData.totalCost" prefix="¥" />
      </el-col>
      <el-col :span="6">
        <StatCard label="本月调用次数" :value="statsData.totalCalls" variant="success" />
      </el-col>
      <el-col :span="6">
        <StatCard label="平均单价" :value="statsData.avgCost.toFixed(2)" prefix="¥" variant="warning" />
      </el-col>
      <el-col :span="6">
        <StatCard label="逾期账单" :value="statsData.overdueCount" suffix="笔" variant="danger" />
      </el-col>
    </el-row>

    <!-- Tab 切换 -->
    <el-card class="table-card">
      <el-tabs v-model="activeTab">
        <!-- 账单记录 -->
        <el-tab-pane label="账单记录" name="record">
          <!-- 搜索区域 -->
          <div class="search-bar">
            <div class="search-inputs">
              <el-input v-model="searchForm.tenantName" placeholder="搜索租户" clearable class="search-input" @keyup.enter="handleSearch" />
              <el-input v-model="searchForm.vendorName" placeholder="搜索厂商" clearable class="search-input" @keyup.enter="handleSearch" />
              <el-date-picker
                v-model="searchForm.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                class="date-picker"
              />
            </div>
            <div class="search-btn-group">
              <el-button type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
              <el-button type="success" @click="handleExport">导出</el-button>
            </div>
          </div>

          <!-- 账单表格 -->
          <el-table :data="tableData" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="tenantName" label="租户" width="120" />
            <el-table-column prop="vendorName" label="厂商" width="120" />
            <el-table-column prop="dataType" label="数据类型" width="120" />
            <el-table-column prop="callCount" label="调用次数" width="100">
              <template #default="{ row }">
                <span class="number-cell">{{ row.callCount?.toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="单价" width="100">
              <template #default="{ row }">
                <span class="price-cell">¥{{ row.unitPrice?.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="totalCost" label="总费用" width="120">
              <template #default="{ row }">
                <span class="cost-cell">¥{{ row.totalCost?.toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="billingDate" label="账单日期" width="120">
              <template #default="{ row }">
                <span class="time-cell">{{ row.billingDate }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusTextLocalized(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row: _row }">
                <el-button type="primary" link @click="handleViewDetail(_row)">详情</el-button>
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
        </el-tab-pane>

        <!-- 计费规则 -->
        <el-tab-pane label="计费规则" name="rule">
          <div class="tool-bar">
            <el-button type="primary" @click="handleAddRule">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 5v14M5 12h14"/>
              </svg>
              新增规则
            </el-button>
          </div>

          <el-table :data="ruleData" stripe>
            <el-table-column prop="vendorName" label="厂商" width="150" />
            <el-table-column prop="dataType" label="数据类型" width="150" />
            <el-table-column prop="unitPrice" label="单价" width="100">
              <template #default="{ row }">
                <span class="price-cell">¥{{ row.unitPrice?.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="阶梯范围" width="150">
              <template #default="{ row }">
                <span class="number-cell">{{ row.tierMin?.toLocaleString() }} - {{ row.tierMax?.toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="discount" label="折扣" width="100">
              <template #default="{ row }">
                <el-tag type="warning" size="small">{{ (row.discount * 10).toFixed(1) }}折</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : row.status === 'tier' ? 'warning' : 'info'" size="small">
                  {{ row.status === 'active' ? '标准' : row.status === 'tier' ? '阶梯' : '禁用' }}
                </el-tag>
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

        <!-- 报表分析 -->
        <el-tab-pane label="报表分析" name="report">
          <el-empty description="报表分析功能开发中...">
            <el-button type="primary">生成报表</el-button>
          </el-empty>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增/编辑规则弹窗 -->
    <el-dialog v-model="dialogVisible" :title="ruleForm.id ? '编辑规则' : '新增规则'" width="500px" class="form-dialog">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="厂商" required>
          <el-input v-model="ruleForm.vendorName" placeholder="请输入厂商名称" />
        </el-form-item>
        <el-form-item label="数据类型" required>
          <el-select v-model="ruleForm.dataType" style="width: 100%">
            <el-option label="工商信息" value="BUSINESS_INFO" />
            <el-option label="企业征信" value="CREDIT_QUERY" />
            <el-option label="诉讼信息" value="LITIGATION" />
            <el-option label="新闻舆情" value="NEWS" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价(元)" required>
          <el-input-number v-model="ruleForm.unitPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-divider>阶梯计费（可选）</el-divider>
        <el-form-item label="最小调用量">
          <el-input-number v-model="ruleForm.tierMin" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大调用量">
          <el-input-number v-model="ruleForm.tierMax" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="折扣">
          <el-slider v-model="ruleForm.discount" :min="0.5" :max="1" :step="0.1" show-stops />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="ruleForm.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitRule">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBillingList, getBillingRuleList, getBillingStats } from '@/api/billing'
import { extractPageData } from '@/utils/pagination'
import { getStatusType as getTagType, getStatusText } from '@/utils/status'
// StatCard is globally registered by unplugin-vue-components

interface BillingRecord {
  id: number
  tenantName: string
  vendorName: string
  dataType: string
  callCount: number
  unitPrice: number
  totalCost: number
  billingDate: string
  status: string
}

interface BillingRule {
  id: number
  vendorName: string
  dataType: string
  unitPrice: number
  tierMin: number
  tierMax: number
  discount: number
  status: string
}

const loading = ref(false)
const activeTab = ref('record')
const tableData = ref<BillingRecord[]>([])
const ruleData = ref<BillingRule[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })

const searchForm = reactive({
  tenantName: '',
  vendorName: '',
  dateRange: [] as string[]
})

const ruleForm = reactive({
  id: null as number | null,
  vendorName: '',
  dataType: '',
  unitPrice: 0,
  tierMin: 0,
  tierMax: 100000,
  discount: 1.0,
  status: 'active'
})

const dialogVisible = ref(false)

// 统计卡片数据
const statsData = ref({
  totalCost: 0,
  totalCalls: 0,
  avgCost: 0,
  overdueCount: 0
})

const fetchStats = async () => {
  try {
    const res = await getBillingStats({})
    if (res.data) {
      statsData.value = {
        totalCost: res.data.totalAmount || 0,
        totalCalls: res.data.totalCalls || 0,
        avgCost: res.data.avgPrice || 0,
        overdueCount: res.data.overdueCount || 0
      }
    }
  } catch {
    // Keep default values on error
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getBillingList({
      page: pagination.currentPage,
      pageSize: pagination.pageSize
    })
    const { list, total: totalCount } = extractPageData<BillingRecord>(res)
    tableData.value = list
    total.value = totalCount
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const fetchRules = async () => {
  try {
    const res = await getBillingRuleList({ page: 1, pageSize: 100 })
    ruleData.value = res.data?.list || []
  } catch {
    ruleData.value = []
  }
}

const handleSearch = () => {
  pagination.currentPage = 1
  fetchList()
}

const handleReset = () => {
  searchForm.tenantName = ''
  searchForm.vendorName = ''
  searchForm.dateRange = []
  pagination.currentPage = 1
  fetchList()
}

const handleAddRule = () => {
  Object.assign(ruleForm, { id: null, vendorName: '', dataType: '', unitPrice: 0, tierMin: 0, tierMax: 100000, discount: 1.0, status: 'active' })
  dialogVisible.value = true
}

const handleEditRule = (row: BillingRule) => {
  Object.assign(ruleForm, { ...row })
  dialogVisible.value = true
}

const handleDeleteRule = async (_row: BillingRule) => {
  try {
    await ElMessageBox.confirm(`确定要删除该计费规则吗？`, '提示', { type: 'warning' })
    ElMessage.success('删除成功')
    fetchRules()
  } catch {}
}

const handleSubmitRule = async () => {
  if (!ruleForm.vendorName || !ruleForm.dataType) {
    ElMessage.warning('请填写完整信息')
    return
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchRules()
}

const handleExport = () => {
  ElMessage.info('导出功能开发中...')
}

const handleViewDetail = (row: BillingRecord) => {
  ElMessage.info(`查看账单详情: ${row.id}`)
}

const getStatusType = (status: string) => getTagType('billing', status)
const getStatusTextLocalized = (status: string) => getStatusText('billing', status)

onMounted(async () => {
  await Promise.all([fetchStats(), fetchList(), fetchRules()])
})
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }

.stats-row { margin-bottom: 20px; }

.search-card { margin-bottom: 20px; }
.search-bar, .tool-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 16px; }
.search-inputs { display: flex; gap: 12px; flex: 1; flex-wrap: wrap; }
.search-input { width: 160px; }
.search-select { width: 140px; }
.date-picker { width: 260px; }
.search-btn-group { display: flex; gap: 10px; }
.tool-bar .el-button { display: flex; align-items: center; gap: 6px; }
.tool-bar .el-button svg { width: 16px; height: 16px; }

.number-cell { font-family: var(--font-mono); color: var(--color-text-secondary); }
.price-cell { color: var(--color-text-secondary); }
.cost-cell { color: var(--color-primary); font-weight: 600; font-family: var(--font-mono); }
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }

.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }

:deep(.el-tabs__item) { font-size: 14px; }
</style>
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
        <StatCard label="累计总消费" :value="statsData.totalCost" prefix="¥" />
      </el-col>
      <el-col :span="6">
        <StatCard label="累计调用次数" :value="statsData.totalCalls" variant="success" />
      </el-col>
      <el-col :span="6">
        <StatCard label="平均单价" :value="statsData.avgCost.toFixed(2)" prefix="¥" variant="warning" />
      </el-col>
      <el-col :span="6">
        <StatCard label="计费天数" :value="statsData.billingDays" suffix="天" variant="info" />
      </el-col>
    </el-row>

    <!-- Tab 切换 -->
    <el-card class="table-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="计费方案" name="plan">
          <BillingPlanWorkspace />
        </el-tab-pane>
        <!-- 账单记录 -->
        <el-tab-pane label="账单记录" name="record">
          <!-- 搜索区域 -->
          <div class="search-bar">
            <div class="search-inputs">
              <el-input-number v-model="searchForm.tenantId" placeholder="租户ID" :min="1" controls-position="right" class="search-input" />
              <el-select v-model="searchForm.vendorId" placeholder="厂商" clearable class="search-select">
                <el-option v-for="vendor in cacheStore.vendorOptions" :key="vendor.id" :label="vendor.vendorName" :value="Number(vendor.id)" />
              </el-select>
              <el-date-picker
                v-model="searchForm.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                class="date-picker"
              />
            </div>
            <div class="search-btn-group">
              <el-button type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
              <el-button type="success" :loading="exporting" @click="handleExport">导出</el-button>
            </div>
          </div>

          <!-- 账单表格 -->
          <el-table :data="tableData" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="tenantId" label="租户ID" width="100" />
            <el-table-column prop="vendorId" label="厂商" width="140">
              <template #default="{ row }">{{ vendorName(row.vendorId) }}</template>
            </el-table-column>
            <el-table-column prop="dataType" label="数据类型" width="120" />
            <el-table-column prop="callCount" label="调用次数" width="100">
              <template #default="{ row }">
                <span class="number-cell">{{ row.callCount?.toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="successCount" label="成功次数" width="100" />
            <el-table-column prop="failCount" label="失败次数" width="100" />
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
        <el-tab-pane label="旧计费规则" name="rule">
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
            <el-table-column label="接口" min-width="220">
              <template #default="{ row }">
                <div>{{ row.interfaceName || row.interfaceCode }}</div>
                <div class="interface-code">{{ row.interfaceCode }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="单价" width="100">
              <template #default="{ row }">
                <span class="price-cell">¥{{ row.unitPrice?.toFixed(4) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="billingType" label="计费方式" width="110">
              <template #default="{ row }">
                <el-tag :type="row.billingType === 'TIERED' ? 'warning' : 'info'" size="small">
                  {{ row.billingType === 'TIERED' ? '阶梯计费' : row.billingType === 'DYNAMIC' ? '动态计费' : '标准计费' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="阶梯明细" min-width="330">
              <template #default="{ row }">
                <div v-if="row.billingType === 'TIERED' && row.tiers?.length" class="tier-tags">
                  <el-tag v-for="tier in row.tiers" :key="`${tier.tierMin}-${tier.tierMax}`" type="warning" size="small">
                    {{ formatTierRange(tier) }}：{{ (tier.discount * 10).toFixed(1) }}折
                  </el-tag>
                </div>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                  {{ row.status === 'active' ? '启用' : '禁用' }}
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
          <el-descriptions :column="2" border>
            <el-descriptions-item label="累计费用">¥{{ statsData.totalCost.toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="累计调用">{{ statsData.totalCalls.toLocaleString() }} 次</el-descriptions-item>
            <el-descriptions-item label="平均单价">¥{{ statsData.avgCost.toFixed(4) }}</el-descriptions-item>
            <el-descriptions-item label="计费天数">{{ statsData.billingDays }} 天</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增/编辑规则弹窗 -->
    <el-dialog v-model="dialogVisible" :title="ruleForm.id ? '编辑规则' : '新增规则'" width="760px" class="form-dialog">
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="厂商" required>
          <el-select v-model="ruleForm.vendorId" style="width: 100%" @change="handleVendorChange">
            <el-option v-for="vendor in cacheStore.vendorOptions" :key="vendor.id" :label="vendor.vendorName" :value="Number(vendor.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="接口" required>
          <el-select
            v-model="ruleForm.interfaceId"
            style="width: 100%"
            :disabled="!ruleForm.vendorId"
            placeholder="请先选择厂商"
            @change="handleInterfaceChange"
          >
            <el-option
              v-for="item in interfaceOptions"
              :key="item.id"
              :label="`${item.interfaceName} (${item.interfaceCode})`"
              :value="Number(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="统计类型">
          <el-input :model-value="selectedInterface?.dataTypeName || '-'" disabled />
        </el-form-item>
        <el-form-item label="单价(元)" required>
          <el-input-number v-model="ruleForm.unitPrice" :min="0" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计费方式" required>
          <el-select v-model="ruleForm.billingType" style="width: 100%" @change="handleBillingTypeChange">
            <el-option label="标准计费" value="STANDARD" />
            <el-option label="阶梯计费" value="TIERED" />
            <el-option label="动态计费（SLA）" value="DYNAMIC" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="ruleForm.billingType === 'TIERED'" label="阶梯配置" required>
          <div class="tier-editor">
            <div class="tier-editor-header">
              <span>调用量下限（含）</span>
              <span>调用量上限（不含）</span>
              <span>折扣率</span>
              <span></span>
            </div>
            <div v-for="(tier, index) in ruleForm.tiers" :key="index" class="tier-editor-row">
              <el-input-number v-model="tier.tierMin" :min="0" :controls="false" disabled />
              <el-input-number
                v-if="index < ruleForm.tiers.length - 1"
                v-model="tier.tierMax"
                :min="tier.tierMin + 1"
                :controls="false"
                @change="syncTierBounds(index)"
              />
              <el-input v-else model-value="无上限" disabled />
              <el-input-number v-model="tier.discount" :min="0.01" :max="1" :step="0.05" :precision="2" />
              <el-button type="danger" link :disabled="ruleForm.tiers.length === 1" @click="removeTier(index)">删除</el-button>
            </div>
            <el-button type="primary" link @click="addTier">+ 添加阶梯</el-button>
            <div class="tier-help">按自然月累计并按区间累进，例如本月15万次 = 前10万原价 + 后5万9折。</div>
          </div>
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

    <el-dialog v-model="detailVisible" title="账单详情" width="620px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="账单ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="账单日期">{{ detail.billingDate }}</el-descriptions-item>
        <el-descriptions-item label="租户ID">{{ detail.tenantId }}</el-descriptions-item>
        <el-descriptions-item label="调用方ID">{{ detail.callerId }}</el-descriptions-item>
        <el-descriptions-item label="厂商">{{ vendorName(detail.vendorId) }}</el-descriptions-item>
        <el-descriptions-item label="数据类型">{{ detail.dataType }}</el-descriptions-item>
        <el-descriptions-item label="调用次数">{{ detail.callCount }}</el-descriptions-item>
        <el-descriptions-item label="总费用">¥{{ Number(detail.totalCost || 0).toFixed(2) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createBillingRule, deleteBillingRule, exportBilling, getBillingById, getBillingList, getBillingRuleList, getBillingStats, updateBillingRule } from '@/api/billing'
import { extractPageData } from '@/utils/pagination'
import { useCacheStore } from '@/stores/cache'
import { getInterfaceOptions } from '@/api/interface'
import type { ApiInterface } from '@/types'
import BillingPlanWorkspace from './BillingPlanWorkspace.vue'
// StatCard is globally registered by unplugin-vue-components

interface BillingRecord {
  id: number
  tenantId: number
  callerId: number
  vendorId: number
  dataType: string
  callCount: number
  successCount: number
  failCount: number
  totalCost: number
  billingDate: string
}

interface BillingRule {
  id: number
  ruleName: string
  vendorId: number
  vendorName: string
  interfaceId: number
  interfaceCode: string
  interfaceName: string
  billingType: 'STANDARD' | 'TIERED' | 'DYNAMIC'
  unitPrice: number
  tierMin: number
  tierMax: number
  discount: number
  tiers: BillingTier[]
  status: string
}

interface BillingTier {
  id?: number
  tierMin: number
  tierMax: number | null
  discount: number
  sortOrder?: number
}

const loading = ref(false)
const exporting = ref(false)
const activeTab = ref('plan')
const tableData = ref<BillingRecord[]>([])
const ruleData = ref<BillingRule[]>([])
const interfaceOptions = ref<ApiInterface[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })

const searchForm = reactive({
  tenantId: undefined as number | undefined,
  vendorId: undefined as number | undefined,
  dateRange: [] as string[]
})

const ruleForm = reactive({
  id: null as number | null,
  ruleName: '',
  vendorId: undefined as number | undefined,
  vendorName: '',
  interfaceId: undefined as number | undefined,
  interfaceCode: '',
  interfaceName: '',
  billingType: 'STANDARD' as 'STANDARD' | 'TIERED' | 'DYNAMIC',
  unitPrice: 0,
  tierMin: 0,
  tierMax: null as number | null,
  discount: 1.0,
  tiers: [] as BillingTier[],
  status: 'active'
})

const dialogVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<BillingRecord | null>(null)
const cacheStore = useCacheStore()
const selectedInterface = computed(() =>
  interfaceOptions.value.find(item => Number(item.id) === ruleForm.interfaceId)
)

// 统计卡片数据
const statsData = ref({
  totalCost: 0,
  totalCalls: 0,
  avgCost: 0,
  billingDays: 0
})

const fetchStats = async () => {
  try {
    const res = await getBillingStats({
      tenantId: searchForm.tenantId,
      startDate: searchForm.dateRange[0],
      endDate: searchForm.dateRange[1]
    })
    if (res.data) {
      statsData.value = {
        totalCost: Number(res.data.totalCost || 0),
        totalCalls: Number(res.data.totalCallCount || 0),
        avgCost: Number(res.data.totalCallCount || 0) ? Number(res.data.totalCost || 0) / Number(res.data.totalCallCount) : 0,
        billingDays: Number(res.data.days || 0)
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
      pageSize: pagination.pageSize,
      tenantId: searchForm.tenantId,
      vendorId: searchForm.vendorId,
      startDate: searchForm.dateRange[0],
      endDate: searchForm.dateRange[1]
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
    ruleData.value = extractPageData<BillingRule>(res).list
  } catch {
    ruleData.value = []
  }
}

const handleSearch = () => {
  pagination.currentPage = 1
  Promise.all([fetchStats(), fetchList()])
}

const handleReset = () => {
  searchForm.tenantId = undefined
  searchForm.vendorId = undefined
  searchForm.dateRange = []
  pagination.currentPage = 1
  Promise.all([fetchStats(), fetchList()])
}

const loadInterfaceOptions = async (vendorId?: number) => {
  if (!vendorId) {
    interfaceOptions.value = []
    return
  }
  const response = await getInterfaceOptions({ vendorId, status: 'active' })
  interfaceOptions.value = response.data || []
}

const handleVendorChange = async (vendorId?: number) => {
  ruleForm.interfaceId = undefined
  ruleForm.interfaceCode = ''
  ruleForm.interfaceName = ''
  await loadInterfaceOptions(vendorId)
}

const handleInterfaceChange = (interfaceId?: number) => {
  const item = interfaceOptions.value.find(option => Number(option.id) === interfaceId)
  ruleForm.interfaceCode = item?.interfaceCode || ''
  ruleForm.interfaceName = item?.interfaceName || ''
}

const handleAddRule = () => {
  Object.assign(ruleForm, { id: null, ruleName: '', vendorId: undefined, vendorName: '', interfaceId: undefined, interfaceCode: '', interfaceName: '', billingType: 'STANDARD', unitPrice: 0, tierMin: 0, tierMax: null, discount: 1.0, tiers: [], status: 'active' })
  interfaceOptions.value = []
  dialogVisible.value = true
}

const handleBillingTypeChange = (billingType: string) => {
  if (billingType === 'TIERED' && !ruleForm.tiers.length) {
    ruleForm.tiers.push(
      { tierMin: 0, tierMax: 100000, discount: 1 },
      { tierMin: 100000, tierMax: 200000, discount: 0.9 },
      { tierMin: 200000, tierMax: 500000, discount: 0.8 },
      { tierMin: 500000, tierMax: null, discount: 0.7 }
    )
  }
}

const handleEditRule = async (row: BillingRule) => {
  await loadInterfaceOptions(row.vendorId)
  const tiers = row.tiers?.length
    ? row.tiers.map(tier => ({ ...tier, tierMax: tier.tierMax ?? null }))
    : row.billingType === 'TIERED'
      ? [{ tierMin: 0, tierMax: null, discount: row.discount || 1 }]
      : []
  Object.assign(ruleForm, { ...row, tiers })
  dialogVisible.value = true
}

const addTier = () => {
  if (!ruleForm.tiers.length) {
    ruleForm.tiers.push({ tierMin: 0, tierMax: null, discount: 1 })
    return
  }
  const previous = ruleForm.tiers[ruleForm.tiers.length - 1]
  const nextMin = previous.tierMin + 100000
  previous.tierMax = nextMin
  ruleForm.tiers.push({ tierMin: nextMin, tierMax: null, discount: Math.max(0.01, previous.discount - 0.1) })
}

const removeTier = (index: number) => {
  ruleForm.tiers.splice(index, 1)
  ruleForm.tiers.forEach((tier, tierIndex) => {
    tier.tierMin = tierIndex === 0 ? 0 : Number(ruleForm.tiers[tierIndex - 1].tierMax)
    if (tierIndex === ruleForm.tiers.length - 1) tier.tierMax = null
  })
}

const syncTierBounds = (index: number) => {
  const current = ruleForm.tiers[index]
  const next = ruleForm.tiers[index + 1]
  if (next && current.tierMax != null) next.tierMin = current.tierMax
}

const formatTierRange = (tier: BillingTier) =>
  `${tier.tierMin.toLocaleString()} - ${tier.tierMax == null ? '∞' : tier.tierMax.toLocaleString()}`

const handleDeleteRule = async (row: BillingRule) => {
  try {
    await ElMessageBox.confirm(`确定要删除该计费规则吗？`, '提示', { type: 'warning' })
    await deleteBillingRule(row.id)
    ElMessage.success('删除成功')
    fetchRules()
  } catch {}
}

const handleSubmitRule = async () => {
  if (!ruleForm.vendorId || !ruleForm.interfaceId || !ruleForm.ruleName) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (ruleForm.billingType === 'TIERED') {
    if (!ruleForm.tiers.length || ruleForm.tiers.some((tier, index) =>
      tier.discount <= 0 || tier.discount > 1
      || (index < ruleForm.tiers.length - 1 && (tier.tierMax == null || tier.tierMax <= tier.tierMin)))) {
      ElMessage.warning('请配置连续、有效的阶梯区间和折扣')
      return
    }
    ruleForm.tiers[ruleForm.tiers.length - 1].tierMax = null
  }
  const vendor = cacheStore.vendorOptions.find(item => Number(item.id) === ruleForm.vendorId)
  const payload = {
    ...ruleForm,
    vendorName: vendor?.vendorName || '',
    tiers: ruleForm.billingType === 'TIERED' ? ruleForm.tiers : [],
    id: undefined
  }
  if (ruleForm.id) {
    await updateBillingRule(ruleForm.id, payload)
  } else {
    await createBillingRule(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchRules()
}

const handleExport = async () => {
  exporting.value = true
  try {
    const blob = await exportBilling({
      vendorId: searchForm.vendorId,
      startDate: searchForm.dateRange[0],
      endDate: searchForm.dateRange[1]
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `billing-${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

const handleViewDetail = async (row: BillingRecord) => {
  const response = await getBillingById(row.id)
  detail.value = response.data as unknown as BillingRecord
  detailVisible.value = true
}

const vendorName = (vendorId: number) => cacheStore.vendorOptions.find(item => Number(item.id) === vendorId)?.vendorName || String(vendorId)

onMounted(async () => {
  await cacheStore.loadAll()
  await Promise.all([fetchStats(), fetchList(), fetchRules()])
})
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }
.interface-code { color: var(--color-text-tertiary); font-size: 12px; margin-top: 2px; }
.tier-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tier-editor { width: 100%; }
.tier-editor-header, .tier-editor-row { display: grid; grid-template-columns: 1fr 1fr 110px 48px; gap: 8px; align-items: center; }
.tier-editor-header { margin-bottom: 8px; color: var(--color-text-tertiary); font-size: 12px; }
.tier-editor-row { margin-bottom: 8px; }
.tier-editor-row :deep(.el-input-number) { width: 100%; }
.tier-help { margin-top: 8px; color: var(--color-text-tertiary); font-size: 12px; }

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

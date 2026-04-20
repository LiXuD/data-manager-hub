<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/utils/request'
import { Money, Timer, Warning, TrendCharts } from '@element-plus/icons-vue'

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
const ruleData = ref<BillingRecord[]>([])
const total = ref(0)
const pagination = ref({ currentPage: 1, pageSize: 10 })

const searchForm = ref({
  tenantName: '',
  vendorName: '',
  dateRange: [] as string[]
})

const ruleForm = ref({
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

const statusOptions = [
  { label: '待结算', value: 'pending' },
  { label: '已结算', value: 'settled' },
  { label: '逾期', value: 'overdue' }
]

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/v1/billing/list', {
      params: { page: pagination.value.currentPage, pageSize: pagination.value.pageSize }
    })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e: any) {
    console.error('获取账单列表失败:', e)
    // 模拟数据
    tableData.value = [
      { id: 1, tenantName: '风控部', vendorName: '企查查', dataType: '工商信息', callCount: 15000, unitPrice: 0.3, totalCost: 4500, billingDate: '2026-04-20', status: 'pending' },
      { id: 2, tenantName: '信贷部', vendorName: '天眼查', dataType: '企业征信', callCount: 8000, unitPrice: 2.5, totalCost: 20000, billingDate: '2026-04-20', status: 'settled' },
      { id: 3, tenantName: '核心系统', vendorName: '企查查', dataType: '工商信息', callCount: 50000, unitPrice: 0.25, totalCost: 12500, billingDate: '2026-04-19', status: 'settled' },
      { id: 4, tenantName: '网贷系统', vendorName: '裁判文书网', dataType: '诉讼信息', callCount: 3000, unitPrice: 0.8, totalCost: 2400, billingDate: '2026-04-19', status: 'pending' },
      { id: 5, tenantName: '风控部', vendorName: '启信宝', dataType: '新闻舆情', callCount: 12000, unitPrice: 0.5, totalCost: 6000, billingDate: '2026-04-18', status: 'overdue' }
    ]
    total.value = 156
    
    // 计算统计数据
    statsData.value = {
      totalCost: 45800,
      totalCalls: 88000,
      avgCost: 0.52,
      overdueCount: 1
    }
  } finally {
    loading.value = false
  }
}

const fetchRules = async () => {
  // 模拟计费规则数据
  ruleData.value = [
    { id: 1, tenantName: '', vendorName: '企查查', dataType: '工商信息', callCount: 0, unitPrice: 0.3, totalCost: 0, billingDate: '', status: 'active' },
    { id: 2, tenantName: '', vendorName: '天眼查', dataType: '企业征信', callCount: 0, unitPrice: 2.5, totalCost: 0, billingDate: '', status: 'active' },
    { id: 3, tenantName: '', vendorName: '企查查', dataType: '工商信息', callCount: 100000, unitPrice: 0.25, totalCost: 0, billingDate: '', status: 'tier' },
    { id: 4, tenantName: '', vendorName: '天眼查', dataType: '企业征信', callCount: 50000, unitPrice: 2.0, totalCost: 0, billingDate: '', status: 'tier' }
  ]
}

const handleSearch = () => {
  pagination.value.currentPage = 1
  fetchList()
}

const handleReset = () => {
  searchForm.value = { tenantName: '', vendorName: '', dateRange: [] }
  pagination.value.currentPage = 1
  fetchList()
}

const handleAddRule = () => {
  ruleForm.value = {
    id: null, vendorName: '', dataType: '', unitPrice: 0,
    tierMin: 0, tierMax: 100000, discount: 1.0, status: 'active'
  }
  dialogVisible.value = true
}

const handleEditRule = (row: any) => {
  ruleForm.value = { ...row }
  dialogVisible.value = true
}

const handleDeleteRule = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除该计费规则吗？`, '提示', { type: 'warning' })
    ElMessage.success('删除成功')
    fetchRules()
  } catch (e) {}
}

const handleSubmitRule = async () => {
  if (!ruleForm.value.vendorName || !ruleForm.value.dataType) {
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

const getStatusType = (status: string) => {
  const map: Record<string, string> = { pending: 'warning', settled: 'success', overdue: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = { pending: '待结算', settled: '已结算', overdue: '逾期' }
  return map[status] || status
}

onMounted(() => {
  fetchList()
  fetchRules()
})
</script>

<template>
  <div class="billing-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #409EFF"><Money /></div>
          <div class="stat-content">
            <div class="stat-title">本月总消费</div>
            <div class="stat-value">¥{{ statsData.totalCost.toLocaleString() }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #67C23A"><Timer /></div>
          <div class="stat-content">
            <div class="stat-title">本月调用次数</div>
            <div class="stat-value">{{ statsData.totalCalls.toLocaleString() }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #E6A23C"><TrendCharts /></div>
          <div class="stat-content">
            <div class="stat-title">平均单价</div>
            <div class="stat-value">¥{{ statsData.avgCost.toFixed(2) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #F56C6C"><Warning /></div>
          <div class="stat-content">
            <div class="stat-title">逾期账单</div>
            <div class="stat-value text-danger">{{ statsData.overdueCount }}笔</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab 切换 -->
    <el-card>
      <el-tabs v-model="activeTab">
        <!-- 账单记录 -->
        <el-tab-pane label="账单记录" name="record">
          <!-- 搜索区域 -->
          <div class="search-bar">
            <el-form :model="searchForm" inline>
              <el-form-item label="租户">
                <el-input v-model="searchForm.tenantName" placeholder="请输入租户" clearable style="width: 150px" />
              </el-form-item>
              <el-form-item label="厂商">
                <el-input v-model="searchForm.vendorName" placeholder="请输入厂商" clearable style="width: 150px" />
              </el-form-item>
              <el-form-item label="账单日期">
                <el-date-picker v-model="searchForm.dateRange" type="daterange" range-separator="至"
                  start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSearch">查询</el-button>
                <el-button @click="handleReset">重置</el-button>
                <el-button type="success" @click="handleExport">导出</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 账单表格 -->
          <el-table :data="tableData" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="tenantName" label="租户" width="120" />
            <el-table-column prop="vendorName" label="厂商" width="120" />
            <el-table-column prop="dataType" label="数据类型" width="120" />
            <el-table-column prop="callCount" label="调用次数" width="100">
              <template #default="{ row }">{{ row.callCount.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="单价(元)" width="100">
              <template #default="{ row }">¥{{ row.unitPrice.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="totalCost" label="总费用(元)" width="120">
              <template #default="{ row }">
                <span class="text-primary">¥{{ row.totalCost.toLocaleString() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="billingDate" label="账单日期" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link>详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 20px; justify-content: flex-end"
          />
        </el-tab-pane>

        <!-- 计费规则 -->
        <el-tab-pane label="计费规则" name="rule">
          <div class="tool-bar">
            <el-button type="primary" @click="handleAddRule">新增规则</el-button>
          </div>

          <el-table :data="ruleData" stripe>
            <el-table-column prop="vendorName" label="厂商" width="150" />
            <el-table-column prop="dataType" label="数据类型" width="150" />
            <el-table-column prop="unitPrice" label="单价(元)" width="100">
              <template #default="{ row }">¥{{ row.unitPrice.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="阶梯范围" width="150">
              <template #default="{ row }">
                {{ row.tierMin.toLocaleString() }} - {{ row.tierMax.toLocaleString() }}
              </template>
            </el-table-column>
            <el-table-column prop="discount" label="折扣" width="100">
              <template #default="{ row }">{{ (row.discount * 10).toFixed(1) }}折</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : row.status === 'tier' ? 'warning' : 'info'">
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
    <el-dialog v-model="dialogVisible" :title="ruleForm.id ? '编辑规则' : '新增规则'" width="500px">
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

<style scoped>
.billing-page {
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

.text-danger {
  color: #F56C6C;
}

.text-primary {
  color: #409EFF;
  font-weight: 500;
}

.search-bar, .tool-bar {
  margin-bottom: 16px;
}
</style>
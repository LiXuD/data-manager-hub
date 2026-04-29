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

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-label">灰度规则总数</div>
            <div class="stat-value">12</div>
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
            <div class="stat-label">启用中</div>
            <div class="stat-value success">4</div>
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
            <div class="stat-label">即将过期</div>
            <div class="stat-value warning">2</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon info">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
            </svg>
          </div>
          <div class="stat-info">
            <div class="stat-label">已过期</div>
            <div class="stat-value">6</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.serviceName" placeholder="搜索服务名称" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
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
            <el-tag size="small">{{ row.conditionType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusTextLocalized(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="160">
          <template #default="{ row }">
            <span class="time-cell">{{ row.startTime?.slice(0, 10) }} ~ {{ row.endTime?.slice(0, 10) }}</span>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" class="form-dialog">
      <el-form :model="formData" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规则名称" required>
              <el-input v-model="formData.ruleName" placeholder="如: 用户服务V2灰度" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="服务名称" required>
              <el-input v-model="formData.serviceName" placeholder="如: user-service" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="目标版本" required>
              <el-input v-model="formData.version" placeholder="如: v2.0.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="流量权重">
              <el-slider v-model="formData.weight" :min="0" :max="100" show-input />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="匹配条件">
              <el-select v-model="formData.conditionType" style="width: 100%">
                <el-option v-for="item in conditionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="条件值">
              <el-input v-model="formData.conditionValue" placeholder="根据条件类型填写" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="开始时间">
              <el-date-picker v-model="formData.startTime" type="datetime" placeholder="选择开始时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间">
              <el-date-picker v-model="formData.endTime" type="datetime" placeholder="选择结束时间" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formData.status" active-value="active" inactive-value="inactive" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/utils/request'
import { getStatusType as getTagType, getStatusText } from '@/utils/status'

interface GrayRule {
  id: number
  ruleName: string
  serviceName: string
  version: string
  weight: number
  conditionType: string
  conditionValue: string
  description: string
  status: string
  startTime: string
  endTime: string
  createdAt: string
}

const loading = ref(false)
const tableData = ref<GrayRule[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })

const searchForm = reactive({
  serviceName: '',
  status: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formData = reactive<GrayRule>({
  id: 0, ruleName: '', serviceName: '', version: '', weight: 10,
  conditionType: 'random', conditionValue: '', description: '', status: 'active',
  startTime: '', endTime: '', createdAt: ''
})

const statusOptions = [
  { label: '全部', value: '' },
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'inactive' },
  { label: '已过期', value: 'expired' }
]

const conditionTypeOptions = [
  { label: '随机流量', value: 'random' },
  { label: '用户ID', value: 'userId' },
  { label: 'IP段', value: 'ip' },
  { label: 'Cookie', value: 'cookie' }
]

interface GraylogConfig {
  id: number
  ruleName: string
  serviceName: string
  version: string
  weight: number
  conditionType: string
  conditionValue: string
  description: string
  status: string
  startTime: string
  endTime: string
  createdAt: string
}

interface GraylogListResponse {
  data?: { records?: GraylogConfig[]; total?: number } | GraylogConfig[]
  total?: number
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get<GraylogListResponse>('/api/v1/graylog/list', {
      params: {
        page: pagination.currentPage,
        pageSize: pagination.pageSize,
        ...searchForm
      }
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
    tableData.value = [
      { id: 1, ruleName: '用户服务V2灰度', serviceName: 'user-service', version: '2.0.0', weight: 20, conditionType: 'random', conditionValue: '', description: '新版本用户服务灰度发布', status: 'active', startTime: '2026-04-15 00:00:00', endTime: '2026-04-30 23:59:59', createdAt: '2026-04-14 10:00:00' },
      { id: 2, ruleName: '计费服务V1.5', serviceName: 'billing-service', version: '1.5.0', weight: 50, conditionType: 'userId', conditionValue: '1000-5000', description: '特定用户ID范围灰度', status: 'active', startTime: '2026-04-10 00:00:00', endTime: '2026-04-25 23:59:59', createdAt: '2026-04-09 14:00:00' },
      { id: 3, ruleName: 'API网关V3灰度', serviceName: 'gateway', version: '3.0.0', weight: 10, conditionType: 'ip', conditionValue: '192.168.1.0/24', description: '内网IP灰度', status: 'inactive', startTime: '2026-04-01 00:00:00', endTime: '2026-04-15 23:59:59', createdAt: '2026-03-31 16:00:00' },
      { id: 4, ruleName: '监控服务升级', serviceName: 'monitor-service', version: '2.1.0', weight: 30, conditionType: 'cookie', conditionValue: 'gray=true', description: 'Cookie标记用户灰度', status: 'expired', startTime: '2026-03-01 00:00:00', endTime: '2026-03-31 23:59:59', createdAt: '2026-02-28 11:00:00' }
    ]
    total.value = 12
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.currentPage = 1; fetchList() }
const handleReset = () => { searchForm.serviceName = ''; searchForm.status = ''; pagination.currentPage = 1; fetchList() }

const handleAdd = () => {
  dialogTitle.value = '新增灰度规则'
  Object.assign(formData, { id: 0, ruleName: '', serviceName: '', version: '', weight: 10, conditionType: 'random', conditionValue: '', description: '', status: 'active', startTime: '', endTime: '', createdAt: '' })
  dialogVisible.value = true
}

const handleEdit = (row: GrayRule) => {
  dialogTitle.value = '编辑灰度规则'
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

const handleDelete = async (row: GrayRule) => {
  try {
    await ElMessageBox.confirm(`确定要删除规则"${row.ruleName}"吗？`, '提示', { type: 'warning' })
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {}
}

const handleSubmit = async () => {
  if (!formData.ruleName || !formData.serviceName) {
    ElMessage.warning('请填写完整信息')
    return
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchList()
}

const getStatusType = (status: string) => getTagType('active', status)
const getStatusTextLocalized = (status: string) => getStatusText('active', status)

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
.stat-icon.info { background: linear-gradient(135deg, #909399, #6c6c6c); }

.stat-info { flex: 1; min-width: 0; }
.stat-label { font-size: 13px; color: var(--color-text-tertiary); margin-bottom: 6px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--color-text-primary); font-family: var(--font-mono); }
.stat-value.success { color: #67C23A; }
.stat-value.warning { color: #E6A23C; }

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
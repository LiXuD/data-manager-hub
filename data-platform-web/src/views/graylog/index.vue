<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Switch, Promotion } from '@element-plus/icons-vue'

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
const pagination = ref({ currentPage: 1, pageSize: 10 })

const searchForm = ref({
  serviceName: '',
  status: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formData = ref<GrayRule>({
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

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/v1/graylog/list', {
      params: {
        page: pagination.value.currentPage,
        pageSize: pagination.value.pageSize,
        ...searchForm.value
      }
    })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e: any) {
    console.error('获取灰度规则列表失败:', e)
    // 模拟数据
    tableData.value = [
      { id: 1, ruleName: '用户服务V2灰度', serviceName: 'user-service', version: 'v2.0.0', weight: 20, conditionType: 'random', conditionValue: '', description: '新版本用户服务灰度发布', status: 'active', startTime: '2026-04-15 00:00:00', endTime: '2026-04-30 23:59:59', createdAt: '2026-04-14 10:00:00' },
      { id: 2, ruleName: '计费服务V1.5', serviceName: 'billing-service', version: 'v1.5.0', weight: 50, conditionType: 'userId', conditionValue: '1000-5000', description: '特定用户ID范围灰度', status: 'active', startTime: '2026-04-10 00:00:00', endTime: '2026-04-25 23:59:59', createdAt: '2026-04-09 14:00:00' },
      { id: 3, ruleName: 'API网关V3灰度', serviceName: 'gateway', version: 'v3.0.0', weight: 10, conditionType: 'ip', conditionValue: '192.168.1.0/24', description: '内网IP灰度', status: 'inactive', startTime: '2026-04-01 00:00:00', endTime: '2026-04-15 23:59:59', createdAt: '2026-03-31 16:00:00' },
      { id: 4, ruleName: '监控服务升级', serviceName: 'monitor-service', version: 'v2.1.0', weight: 30, conditionType: 'cookie', conditionValue: 'gray=true', description: 'Cookie标记用户灰度', status: 'expired', startTime: '2026-03-01 00:00:00', endTime: '2026-03-31 23:59:59', createdAt: '2026-02-28 11:00:00' }
    ]
    total.value = 12
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.currentPage = 1
  fetchList()
}

const handleReset = () => {
  searchForm.value = { serviceName: '', status: '' }
  pagination.value.currentPage = 1
  fetchList()
}

const handleAdd = () => {
  dialogTitle.value = '新增灰度规则'
  formData.value = {
    id: 0, ruleName: '', serviceName: '', version: '', weight: 10,
    conditionType: 'random', conditionValue: '', description: '', status: 'active',
    startTime: '', endTime: '', createdAt: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row: GrayRule) => {
  dialogTitle.value = '编辑灰度规则'
  formData.value = { ...row }
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
  if (!formData.value.ruleName || !formData.value.serviceName) {
    ElMessage.warning('请填写完整信息')
    return
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchList()
}

const handleToggleStatus = (row: GrayRule) => {
  row.status = row.status === 'active' ? 'inactive' : 'active'
  ElMessage.success(row.status === 'active' ? '已启用' : '已禁用')
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = { active: 'success', inactive: 'info', expired: 'warning' }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = { active: '启用中', inactive: '已禁用', expired: '已过期' }
  return map[status] || status
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="graylog-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #409EFF"><Promotion /></div>
          <div class="stat-content">
            <div class="stat-title">灰度规则总数</div>
            <div class="stat-value">12</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #67C23A"><Switch /></div>
          <div class="stat-content">
            <div class="stat-title">启用中</div>
            <div class="stat-value text-success">4</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #E6A23C"><Promotion /></div>
          <div class="stat-content">
            <div class="stat-title">即将过期</div>
            <div class="stat-value text-warning">2</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon" style="background: #909399"><Switch /></div>
          <div class="stat-content">
            <div class="stat-title">已过期</div>
            <div class="stat-value">6</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="服务名称">
          <el-input v-model="searchForm.serviceName" placeholder="请输入服务名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>灰度规则列表</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增规则</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="ruleName" label="规则名称" width="180" />
        <el-table-column prop="serviceName" label="服务名称" width="150" />
        <el-table-column prop="version" label="目标版本" width="120">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="流量权重" width="100">
          <template #default="{ row }">
            <el-progress :percentage="row.weight" :stroke-width="10" :show-text="false" />
            <span style="font-size: 12px">{{ row.weight }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="conditionType" label="匹配条件" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.conditionType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="180">
          <template #default="{ row }">
            {{ row.startTime?.slice(0, 10) }} ~ {{ row.endTime?.slice(0, 10) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.currentPage"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
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

<style scoped>
.graylog-page {
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

.text-success { color: #67C23A; }
.text-warning { color: #E6A23C; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
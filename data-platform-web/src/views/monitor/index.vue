<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="告警规则" name="rules">
        <div class="table-toolbar">
          <el-button type="primary" @click="handleAdd">新增规则</el-button>
        </div>
        <el-table :data="tableData" stripe v-loading="loading">
          <el-table-column prop="ruleName" label="规则名称" />
          <el-table-column prop="ruleType" label="规则类型" />
          <el-table-column prop="threshold" label="阈值" />
          <el-table-column prop="level" label="级别">
            <template #default="{ row }">
              <el-tag v-if="row.level === 'critical'" type="danger">严重</el-tag>
              <el-tag v-else-if="row.level === 'warning'" type="warning">警告</el-tag>
              <el-tag v-else type="info">信息</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-switch v-model="row.status" active-value="active" inactive-value="inactive" @change="handleStatusChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
          style="margin-top: 20px"
        />
      </el-tab-pane>
      <el-tab-pane label="告警记录" name="records">
        <el-table :data="alertData" stripe v-loading="loadingAlert">
          <el-table-column prop="alertTitle" label="告警标题" />
          <el-table-column prop="alertType" label="类型" />
          <el-table-column prop="severity" label="级别">
            <template #default="{ row }">
              <el-tag v-if="row.severity === 'critical'" type="danger">严重</el-tag>
              <el-tag v-else-if="row.severity === 'warning'" type="warning">警告</el-tag>
              <el-tag v-else type="info">信息</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'firing'" type="danger">触发中</el-tag>
              <el-tag v-else type="success">已恢复</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="firedAt" label="触发时间" />
        </el-table>
        <el-pagination
          v-model:current-page="paginationAlert.page"
          v-model:page-size="paginationAlert.pageSize"
          :total="paginationAlert.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchAlertData"
          @current-change="fetchAlertData"
          style="margin-top: 20px"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted} from "vue"
import {ElMessage, ElMessageBox} from "element-plus"
import {getAlertRuleList, deleteAlertRule, updateAlertRuleStatus, getAlertRecordList} from "@/api/monitor"

const activeTab = ref("rules")

// Alert Rules
const tableData = ref([])
const loading = ref(false)
const pagination = ref({
  page: 1,
  pageSize: 10,
  total: 0
})

// Alert Records
const alertData = ref([])
const loadingAlert = ref(false)
const paginationAlert = ref({
  page: 1,
  pageSize: 10,
  total: 0
})

// Fetch Alert Rules
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getAlertRuleList({
      page: pagination.value.page,
      pageSize: pagination.value.pageSize
    })
    // Handle both response formats: res.data or res directly
    const data = res.data?.data || res.data || res
    if (Array.isArray(data)) {
      tableData.value = data
      pagination.value.total = res.data?.total || res.total || data.length
    } else if (data?.records) {
      tableData.value = data.records
      pagination.value.total = data.total || 0
    }
  } catch (error: any) {
    console.error('加载失败:', error)
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// Fetch Alert Records
const fetchAlertData = async () => {
  loadingAlert.value = true
  try {
    const res = await getAlertRecordList({
      page: paginationAlert.value.page,
      pageSize: paginationAlert.value.pageSize
    })
    const data = res.data?.data || res.data || res
    if (Array.isArray(data)) {
      alertData.value = data
      paginationAlert.value.total = res.data?.total || res.total || data.length
    } else if (data?.records) {
      alertData.value = data.records
      paginationAlert.value.total = data.total || 0
    }
  } catch (error: any) {
    console.error('加载失败:', error)
    ElMessage.error(error.message || '加载失败')
  } finally {
    loadingAlert.value = false
  }
}

const handleAdd = () => {
  ElMessage.info('新增功能开发中')
}

const handleEdit = (row: any) => {
  ElMessage.info('编辑功能开发中')
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除规则 "${row.ruleName}" 吗?`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteAlertRule(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const handleStatusChange = async (row: any) => {
  try {
    await updateAlertRuleStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch (error: any) {
    ElMessage.error(error.message || '状态更新失败')
    fetchData() // revert
  }
}

// Tab change handler
const handleTabChange = (tab: string) => {
  if (tab === 'rules') {
    fetchData()
  } else if (tab === 'records') {
    fetchAlertData()
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.table-toolbar {
  margin-bottom: 15px;
}
</style>
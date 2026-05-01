<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>调用方管理</h2>
        <p class="header-desc">管理内部系统API调用方与密钥</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增调用方
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.keyword" placeholder="搜索调用方名称/编码" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
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
        <el-table-column prop="callerCode" label="调用方编码" width="140">
          <template #default="{ row }">
            <span class="code-tag">{{ row.callerCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="callerName" label="调用方名称" min-width="160" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" active-value="active" inactive-value="inactive" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="handleApiKey(row)">API Key</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- API Key弹窗 -->
    <el-dialog v-model="apiKeyVisible" title="API Key管理" width="700px" class="form-dialog">
      <div class="api-key-header">
        <el-button type="primary" @click="handleCreateApiKey">创建API Key</el-button>
      </div>
      <el-table :data="apiKeyList" stripe class="api-key-table">
        <el-table-column prop="apiKey" label="API Key" min-width="250">
          <template #default="{ row }">
            <code class="api-key-value">{{ row.apiKey }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="rateLimit" label="速率限制" width="100" align="center">
          <template #default="{ row }">{{ row.rateLimit || 1000 }}/min</template>
        </el-table-column>
        <el-table-column prop="quotaUsed" label="已用/配额" width="120" align="center">
          <template #default="{ row }">{{ row.quotaUsed || 0 }} / {{ row.quotaLimit || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleDeleteApiKey(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCallerList, deleteCaller, getApiKeyList, createApiKey, deleteApiKey, updateCallerStatus } from '@/api/caller'
import type { Caller, ApiKey } from '@/api/caller'

const searchForm = reactive({ keyword: '', status: '' })
const tableData = ref<Caller[]>([])
const loading = ref(false)
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const apiKeyVisible = ref(false)
const currentCallerId = ref<number>(0)
const apiKeyList = ref<ApiKey[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCallerList({ page: pagination.page, pageSize: pagination.pageSize })
    tableData.value = res.data || []
    pagination.total = res.total || 0
  } catch { tableData.value = [] }
  finally { loading.value = false }
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { searchForm.keyword = ''; searchForm.status = ''; loadData() }
const handleAdd = () => { ElMessage.info('新增功能开发中') }
const handleEdit = (_row: Caller) => { ElMessage.info('编辑功能开发中') }
const handleDelete = async (row: Caller) => { await ElMessageBox.confirm(`确认删除"${row.callerName}"?`, '提示', { type: 'warning' }); await deleteCaller(row.id!); ElMessage.success('删除成功'); loadData() }
const handleApiKey = async (row: Caller) => { currentCallerId.value = row.id!; const res = await getApiKeyList(row.id!); apiKeyList.value = res.data || []; apiKeyVisible.value = true }
const handleCreateApiKey = async () => { const res = await createApiKey(currentCallerId.value); ElMessage.success('创建成功: ' + res.apiKey); apiKeyList.value = [...apiKeyList.value, res] }
const handleDeleteApiKey = async (id: number) => { await deleteApiKey(id); ElMessage.success('删除成功'); apiKeyList.value = apiKeyList.value.filter(k => k.id !== id) }
const handleStatusChange = async (row: Caller) => {
  try {
    await updateCallerStatus(row.id!, row.status as 'active' | 'inactive')
    ElMessage.success(row.status === 'active' ? '已启用' : '已禁用')
  } catch (error) {
    row.status = row.status === 'active' ? 'inactive' : 'active'
    ElMessage.error('状态更新失败')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page-container { max-width: 1600px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }
.page-header .el-button { display: flex; align-items: center; gap: 8px; }
.page-header .el-button svg { width: 18px; height: 18px; }
.search-card { margin-bottom: 20px; }
.search-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.search-inputs { display: flex; gap: 12px; flex: 1; }
.search-input { width: 280px; }
.search-select { width: 160px; }
.search-btn-group { display: flex; gap: 10px; }
.code-tag { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 4px 10px; border-radius: 6px; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
.api-key-header { margin-bottom: 16px; }
.api-key-value { font-family: var(--font-mono); font-size: 12px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 4px 8px; border-radius: 4px; word-break: break-all; }
</style>
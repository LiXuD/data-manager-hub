<template>
  <div class="page-container">
    <div class="card">
      <!-- 搜索区域 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索调用方名称/编码"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="searchForm.status" placeholder="状态" clearable>
          <el-option label="启用" value="active" />
          <el-option label="禁用" value="inactive" />
        </el-select>
        <div class="search-btn-group">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增调用方
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="callerCode" label="调用方编码" width="120" />
        <el-table-column prop="callerName" label="调用方名称" min-width="150" />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
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
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- API Key弹窗 -->
    <el-dialog v-model="apiKeyVisible" title="API Key管理" width="600px">
      <el-button type="primary" @click="handleCreateApiKey" style="margin-bottom: 16px">
        创建API Key
      </el-button>
      <el-table :data="apiKeyList" stripe>
        <el-table-column prop="apiKey" label="API Key" min-width="200" show-overflow-tooltip />
        <el-table-column prop="rateLimit" label="速率限制" width="100" />
        <el-table-column prop="quotaLimit" label="额度" width="100" />
        <el-table-column prop="quotaUsed" label="已用" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
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
import { Search, Plus } from '@element-plus/icons-vue'
import { getCallerList, createCaller, updateCaller, deleteCaller, getApiKeyList, createApiKey, deleteApiKey } from '@/api/caller'
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
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const handleSearch = () => { pagination.page = 1; loadData() }
const handleReset = () => { searchForm.keyword = ''; searchForm.status = ''; loadData() }
const handleAdd = () => { ElMessage.info('新增功能开发中') }
const handleEdit = (row: Caller) => { ElMessage.info('编辑功能开发中') }
const handleDelete = async (row: Caller) => {
  await ElMessageBox.confirm(`确认删除"${row.callerName}"?`, '提示', { type: 'warning' })
  await deleteCaller(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

const handleApiKey = async (row: Caller) => {
  currentCallerId.value = row.id!
  const res = await getApiKeyList(row.id!)
  apiKeyList.value = res.data || []
  apiKeyVisible.value = true
}

const handleCreateApiKey = async () => {
  const res = await createApiKey(currentCallerId.value)
  ElMessage.success('创建成功: ' + res.data.apiKey)
  apiKeyList.value = [...apiKeyList.value, res.data]
}

const handleDeleteApiKey = async (id: number) => {
  await deleteApiKey(id)
  ElMessage.success('删除成功')
  apiKeyList.value = apiKeyList.value.filter(k => k.id !== id)
}

onMounted(() => { loadData() })
</script>

<style scoped>
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>

<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>数据类型管理</h2>
        <p class="header-desc">管理系统支持的数据类型</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增类型
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.dataTypeName" placeholder="搜索类型名称" clearable class="search-input" @keyup.enter="handleSearch" />
          <el-select v-model="searchForm.dataCategory" placeholder="分类" clearable class="search-select">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="dataTypeCode" label="类型编码" width="180">
          <template #default="{ row }">
            <span class="code-tag">{{ row.dataTypeCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dataTypeName" label="类型名称" min-width="150" />
        <el-table-column prop="dataCategory" label="分类" width="120">
          <template #default="{ row }">
            <el-tag :type="getCategoryTag(row.dataCategory)" size="small">{{ categoryMap[row.dataCategory] || row.dataCategory }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" active-value="active" inactive-value="inactive" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="time-cell">{{ row.createdAt }}</span>
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
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑数据类型' : '新增数据类型'" width="500px" class="form-dialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="类型编码" required>
          <el-input v-model="form.dataTypeCode" placeholder="如: BUSINESS_INFO" />
        </el-form-item>
        <el-form-item label="类型名称" required>
          <el-input v-model="form.dataTypeName" placeholder="请输入类型名称" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.dataCategory" style="width: 100%">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDataTypeList,
  createDataType,
  updateDataType,
  deleteDataType,
  updateDataTypeStatus
} from '@/api/datatype'
import { COMMON_STATUS } from '@/constants'
import { extractPageData } from '@/utils/pagination'

interface DataType { id: number; dataTypeCode: string; dataTypeName: string; dataCategory: string; description: string; status: string; createdAt: string }

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<DataType[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })
const searchForm = reactive({ dataTypeName: '', dataCategory: '', status: '' })
const dialogVisible = ref(false)
const form = reactive({ id: null as number | null, dataTypeCode: '', dataTypeName: '', dataCategory: '', description: '', status: COMMON_STATUS.ACTIVE })

const categoryOptions = [
  { label: '工商信息', value: 'business' },
  { label: '司法信息', value: 'judicial' },
  { label: '财务信息', value: 'financial' },
  { label: '舆情信息', value: 'public_opinion' },
  { label: '其他', value: 'other' }
]

const categoryMap: Record<string, string> = { business: '工商信息', judicial: '司法信息', financial: '财务信息', public_opinion: '舆情信息', other: '其他' }
const getCategoryTag = (category: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { business: 'success', judicial: 'warning', financial: 'info', public_opinion: 'danger', other: 'info' }
  return map[category] || 'info'
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getDataTypeList({ page: pagination.currentPage, pageSize: pagination.pageSize, ...searchForm })
    const page = extractPageData<DataType>(res)
    tableData.value = page.list
    total.value = page.total
  } catch {
    tableData.value = []
    total.value = 0
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.currentPage = 1; fetchList() }
const handleReset = () => { searchForm.dataTypeName = ''; searchForm.dataCategory = ''; searchForm.status = ''; pagination.currentPage = 1; fetchList() }
const handleAdd = () => { Object.assign(form, { id: null, dataTypeCode: '', dataTypeName: '', dataCategory: '', description: '', status: 'active' }); dialogVisible.value = true }
const handleEdit = (row: DataType) => { Object.assign(form, { ...row }); dialogVisible.value = true }

const handleDelete = async (row: DataType) => {
  try {
    await ElMessageBox.confirm(`确定要删除数据类型"${row.dataTypeName}"吗？`, '提示', { type: 'warning' })
    await deleteDataType(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!form.dataTypeCode || !form.dataTypeName) {
    ElMessage.warning('请填写类型编码和类型名称')
    return
  }
  submitting.value = true
  try {
    if (form.id) {
      await updateDataType(form.id, { ...form, id: undefined })
    } else {
      await createDataType({ ...form, id: undefined })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchList()
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const handleStatusChange = async (row: DataType) => {
  try {
    await updateDataTypeStatus(row.id, row.status)
    ElMessage.success(row.status === COMMON_STATUS.ACTIVE ? '已启用' : '已禁用')
  } catch (error) {
    row.status = row.status === COMMON_STATUS.ACTIVE ? COMMON_STATUS.INACTIVE : COMMON_STATUS.ACTIVE
    ElMessage.error('状态更新失败')
  }
}

onMounted(() => { fetchList() })
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
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>

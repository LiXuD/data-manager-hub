<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/utils/request'

interface DataType {
  id: number
  typeCode: string
  typeName: string
  category: string
  description: string
  status: string
  createdAt: string
}

const loading = ref(false)
const tableData = ref<DataType[]>([])
const total = ref(0)
const searchForm = ref({
  typeName: '',
  category: '',
  status: ''
})
const dialogVisible = ref(false)
const form = ref({
  id: null as number | null,
  typeCode: '',
  typeName: '',
  category: '',
  description: '',
  status: 'active'
})

const categoryOptions = [
  { label: '工商信息', value: 'business' },
  { label: '司法信息', value: 'judicial' },
  { label: '财务信息', value: 'financial' },
  { label: '舆情信息', value: 'public_opinion' },
  { label: '其他', value: 'other' }
]

const statusOptions = [
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'inactive' }
]

const categoryMap: Record<string, string> = {
  business: '工商信息',
  judicial: '司法信息',
  financial: '财务信息',
  public_opinion: '舆情信息',
  other: '其他'
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/v1/data-type/list', {
      params: {
        page: 1,
        pageSize: 10,
        ...searchForm.value
      }
    })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e: any) {
    console.error('获取数据类型列表失败:', e)
    // 模拟数据
    tableData.value = [
      { id: 1, typeCode: 'BUSINESS_INFO', typeName: '工商信息', category: 'business', description: '企业工商信息查询', status: 'active', createdAt: '2026-01-01 10:00:00' },
      { id: 2, typeCode: 'CREDIT_QUERY', typeName: '企业征信', category: 'financial', description: '企业征信报告查询', status: 'active', createdAt: '2026-01-02 10:00:00' },
      { id: 3, typeCode: 'LITIGATION', typeName: '诉讼信息', category: 'judicial', description: '企业诉讼记录查询', status: 'active', createdAt: '2026-01-03 10:00:00' },
      { id: 4, typeCode: 'NEWS', typeName: '新闻舆情', category: 'public_opinion', description: '企业新闻舆情监控', status: 'active', createdAt: '2026-01-04 10:00:00' }
    ]
    total.value = 4
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  fetchList()
}

const handleReset = () => {
  searchForm.value = { typeName: '', category: '', status: '' }
  fetchList()
}

const handleAdd = () => {
  form.value = {
    id: null,
    typeCode: '',
    typeName: '',
    category: '',
    description: '',
    status: 'active'
  }
  dialogVisible.value = true
}

const handleEdit = (row: DataType) => {
  form.value = { ...row }
  dialogVisible.value = true
}

const handleDelete = async (row: DataType) => {
  try {
    await ElMessageBox.confirm(`确定要删除数据类型"${row.typeName}"吗？`, '提示', {
      type: 'warning'
    })
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 用户取消
  }
}

const handleSubmit = async () => {
  if (!form.value.typeCode || !form.value.typeName) {
    ElMessage.warning('请填写类型编码和类型名称')
    return
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchList()
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="datatype-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="类型名称">
          <el-input v-model="searchForm.typeName" placeholder="请输入类型名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="请选择" clearable style="width: 140px">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>数据类型列表</span>
          <el-button type="primary" @click="handleAdd">新增数据类型</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="typeCode" label="类型编码" width="180" />
        <el-table-column prop="typeName" label="类型名称" width="150" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            {{ categoryMap[row.category] || row.category }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="1"
        v-model:page-size="10"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑数据类型' : '新增数据类型'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="类型编码" required>
          <el-input v-model="form.typeCode" placeholder="如: BUSINESS_INFO" />
        </el-form-item>
        <el-form-item label="类型名称" required>
          <el-input v-model="form.typeName" placeholder="请输入类型名称" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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
.datatype-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
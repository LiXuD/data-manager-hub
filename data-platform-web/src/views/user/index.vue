<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/utils/request'

interface User {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  status: string
  createdAt: string
}

const loading = ref(false)
const tableData = ref<User[]>([])
const total = ref(0)
const searchForm = ref({
  username: '',
  status: ''
})
const dialogVisible = ref(false)
const form = ref({
  id: null as number | null,
  username: '',
  nickname: '',
  email: '',
  phone: '',
  status: 'active'
})

const statusOptions = [
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'inactive' }
]

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/v1/user/list', {
      params: {
        page: 1,
        pageSize: 10,
        ...searchForm.value
      }
    })
    tableData.value = res.data || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  fetchList()
}

const handleReset = () => {
  searchForm.value = { username: '', status: '' }
  fetchList()
}

const handleAdd = () => {
  form.value = {
    id: null,
    username: '',
    nickname: '',
    email: '',
    phone: '',
    status: 'active'
  }
  dialogVisible.value = true
}

const handleEdit = (row: User) => {
  form.value = { ...row }
  dialogVisible.value = true
}

const handleDelete = async (row: User) => {
  try {
    await ElMessageBox.confirm('确定要删除该用户吗？', '提示', {
      type: 'warning'
    })
    // await request.delete(`/api/v1/user/${row.id}`)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 用户取消
  }
}

const handleSubmit = async () => {
  try {
    // await request.post('/api/v1/user', form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchList()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="user-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable>
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
          <span>用户列表</span>
          <el-button type="primary" @click="handleAdd">新增用户</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" />
        <el-table-column label="操作" width="180">
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
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
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
.user-page {
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
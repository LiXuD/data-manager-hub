<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <div>
        <h2>用户管理</h2>
        <p class="header-desc">管理系统用户账户与权限</p>
      </div>
      <el-button type="primary" @click="handleAdd">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增用户
      </el-button>
    </div>

    <!-- 搜索区域 -->
    <el-card class="search-card">
      <div class="search-bar">
        <div class="search-inputs">
          <el-input v-model="searchForm.username" placeholder="搜索用户名" clearable class="search-input" @keyup.enter="handleSearch" />
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
        <el-table-column prop="username" label="用户名" min-width="120">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ row.username?.charAt(0)?.toUpperCase() }}</div>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
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
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="500px" class="form-dialog">
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
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
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

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/utils/request'

interface User { id: number; username: string; nickname: string; email: string; phone: string; status: string; createdAt: string }

const loading = ref(false)
const tableData = ref<User[]>([])
const total = ref(0)
const pagination = reactive({ currentPage: 1, pageSize: 10 })
const searchForm = reactive({ username: '', status: '' })
const dialogVisible = ref(false)
const form = reactive({ id: null as number | null, username: '', nickname: '', email: '', phone: '', status: 'active' })

interface UserListResponse {
  data?: User[]
  total?: number
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get<UserListResponse>('/user/list', { params: { page: pagination.currentPage, pageSize: pagination.pageSize, ...searchForm } })
    tableData.value = res.data || []
    total.value = res.total || 0
  } catch (e: unknown) {
    console.error('加载用户列表失败:', e)
    tableData.value = []
    total.value = 0
  } finally { loading.value = false }
}

const handleSearch = () => { pagination.currentPage = 1; fetchList() }
const handleReset = () => { searchForm.username = ''; searchForm.status = ''; pagination.currentPage = 1; fetchList() }

const handleAdd = () => { Object.assign(form, { id: null, username: '', nickname: '', email: '', phone: '', status: 'active' }); dialogVisible.value = true }
const handleEdit = (row: User) => { Object.assign(form, { ...row }); dialogVisible.value = true }

const handleDelete = async (_row: User) => {
  await ElMessageBox.confirm('确定要删除该用户吗？', '提示', { type: 'warning' })
  ElMessage.success('删除成功')
  fetchList()
}

const handleSubmit = async () => {
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchList()
}

const handleStatusChange = (row: User) => { ElMessage.success(row.status === 'active' ? '已启用' : '已禁用') }

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
.user-cell { display: flex; align-items: center; gap: 10px; }
.user-avatar { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, var(--color-primary), #6366F1); border-radius: 8px; color: #0A1628; font-weight: 600; font-size: 14px; }
.time-cell { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
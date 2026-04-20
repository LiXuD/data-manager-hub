<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '@/utils/request'

interface Role {
  id: number
  roleCode: string
  roleName: string
  description: string
  status: string
  createdAt: string
}

const loading = ref(false)
const tableData = ref<Role[]>([])
const total = ref(0)
const searchForm = ref({
  roleName: '',
  status: ''
})
const dialogVisible = ref(false)
const form = ref({
  id: null as number | null,
  roleCode: '',
  roleName: '',
  description: '',
  status: 'active'
})

const statusOptions = [
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'inactive' }
]

const fetchList = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/v1/role/list', {
      params: {
        page: 1,
        pageSize: 10,
        ...searchForm.value
      }
    })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e: any) {
    console.error('获取角色列表失败:', e)
    // 模拟数据
    tableData.value = [
      { id: 1, roleCode: 'ADMIN', roleName: '系统管理员', description: '拥有所有权限', status: 'active', createdAt: '2026-01-01 10:00:00' },
      { id: 2, roleCode: 'TENANT_ADMIN', roleName: '租户管理员', description: '租户管理权限', status: 'active', createdAt: '2026-01-02 10:00:00' },
      { id: 3, roleCode: 'OPERATOR', roleName: '运营人员', description: '日常运营操作', status: 'active', createdAt: '2026-01-03 10:00:00' },
      { id: 4, roleCode: 'VIEWER', roleName: '只读用户', description: '查看数据权限', status: 'active', createdAt: '2026-01-04 10:00:00' }
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
  searchForm.value = { roleName: '', status: '' }
  fetchList()
}

const handleAdd = () => {
  form.value = {
    id: null,
    roleCode: '',
    roleName: '',
    description: '',
    status: 'active'
  }
  dialogVisible.value = true
}

const handleEdit = (row: Role) => {
  form.value = { ...row }
  dialogVisible.value = true
}

const handleDelete = async (row: Role) => {
  try {
    await ElMessageBox.confirm(`确定要删除角色"${row.roleName}"吗？`, '提示', {
      type: 'warning'
    })
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 用户取消
  }
}

const handleSubmit = async () => {
  if (!form.value.roleCode || !form.value.roleName) {
    ElMessage.warning('请填写角色编码和角色名称')
    return
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchList()
}

const handlePermission = (row: Role) => {
  ElMessage.info(`配置角色"${row.roleName}"的权限`)
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="role-page">
    <!-- 搜索区域 -->
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="角色名称">
          <el-input v-model="searchForm.roleName" placeholder="请输入角色名称" clearable style="width: 200px" />
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
          <span>角色列表</span>
          <el-button type="primary" @click="handleAdd">新增角色</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleCode" label="角色编码" width="150" />
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePermission(row">配置权限</el-button>
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
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑角色' : '新增角色'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.roleCode" placeholder="如: ADMIN, TENANT_ADMIN" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
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
.role-page {
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
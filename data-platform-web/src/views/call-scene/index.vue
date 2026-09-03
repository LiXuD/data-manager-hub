<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>场景字典</h2>
        <p class="header-desc">维护统一调用入口使用的公共业务场景</p>
      </div>
      <el-button v-if="canAdd" type="primary" @click="openCreate">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        新增场景
      </el-button>
    </div>

    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="sceneCode" label="场景编码" min-width="180">
          <template #default="{ row }">
            <span class="code-tag">{{ row.sceneCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sceneName" label="场景名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDisable" link :type="row.status === 'active' ? 'danger' : 'success'"
                       @click="handleStatusChange(row)">
              {{ row.status === 'active' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑场景' : '新增场景'" width="520px"
               class="form-dialog" @closed="resetForm">
      <el-form :model="form" label-width="90px">
        <el-form-item v-if="!editing" label="场景编码" required>
          <el-input v-model="form.sceneCode" placeholder="pre-loan-review" />
        </el-form-item>
        <el-form-item label="场景名称" required>
          <el-input v-model="form.sceneName" placeholder="贷前审批" />
        </el-form-item>
        <el-form-item v-if="!editing" label="状态">
          <el-select v-model="form.status" class="full-width">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="场景说明" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCallScene,
  getCallSceneList,
  updateCallScene,
  updateCallSceneStatus
} from '@/api/call-scene'
import type { CallScene } from '@/api/call-scene'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canAdd = computed(() => userStore.hasPermission('call-scene:add'))
const canEdit = computed(() => userStore.hasPermission('call-scene:edit'))
const canDisable = computed(() => userStore.hasPermission('call-scene:disable'))
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const tableData = ref<CallScene[]>([])
const form = reactive<CallScene>({
  sceneCode: '',
  sceneName: '',
  status: 'active',
  description: ''
})

const resetForm = () => {
  editing.value = false
  form.id = undefined
  form.sceneCode = ''
  form.sceneName = ''
  form.status = 'active'
  form.description = ''
}

const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}

const openEdit = (scene: CallScene) => {
  editing.value = true
  form.id = scene.id
  form.sceneCode = scene.sceneCode
  form.sceneName = scene.sceneName
  form.status = scene.status
  form.description = scene.description || ''
  dialogVisible.value = true
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCallSceneList()
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

const actionErrorMessage = (error: unknown, fallback: string) => {
  const response = (error as { response?: { data?: { msg?: string; message?: string } } })?.response
  return response?.data?.msg || response?.data?.message || fallback
}

const handleSubmit = async () => {
  if (!form.sceneCode.trim() || !form.sceneName.trim()) {
    ElMessage.warning('请填写场景编码和场景名称')
    return
  }
  submitting.value = true
  try {
    if (editing.value && form.id) {
      await updateCallScene(form.id, {
        sceneName: form.sceneName.trim(),
        description: form.description?.trim()
      })
      ElMessage.success('场景更新成功')
    } else {
      await createCallScene({
        sceneCode: form.sceneCode.trim(),
        sceneName: form.sceneName.trim(),
        status: form.status,
        description: form.description?.trim()
      })
      ElMessage.success('场景创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(actionErrorMessage(error, '场景保存失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

const handleStatusChange = async (scene: CallScene) => {
  const nextStatus = scene.status === 'active' ? 'inactive' : 'active'
  const action = nextStatus === 'active' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${action}场景“${scene.sceneName}”吗？`, `${action}场景`, {
      type: nextStatus === 'active' ? 'info' : 'warning'
    })
    await updateCallSceneStatus(scene.id!, nextStatus)
    ElMessage.success(`场景已${action}`)
    await loadData()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(actionErrorMessage(error, `场景${action}失败，请稍后重试`))
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-container { max-width: 1400px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }
.page-header .el-button { display: flex; align-items: center; gap: 8px; }
.page-header .el-button svg { width: 18px; height: 18px; }
.code-tag { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); background: var(--color-bg-light); padding: 4px 10px; border-radius: 6px; }
.full-width { width: 100%; }
</style>

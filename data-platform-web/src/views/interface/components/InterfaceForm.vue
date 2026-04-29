<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'add' ? '新增接口' : '编辑接口'"
    width="600px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="接口编码" prop="interfaceCode">
        <el-input v-model="form.interfaceCode" placeholder="请输入接口编码" :disabled="mode === 'edit'" />
      </el-form-item>
      <el-form-item label="接口名称" prop="interfaceName">
        <el-input v-model="form.interfaceName" placeholder="请输入接口名称" />
      </el-form-item>
      <el-form-item label="接口路径" prop="path">
        <el-input v-model="form.path" placeholder="请输入接口路径，如 /api/v1/user/query" />
      </el-form-item>
      <el-form-item label="所属厂商" prop="vendorId">
        <el-select v-model="form.vendorId" placeholder="请选择所属厂商" clearable style="width: 100%">
          <el-option
            v-for="vendor in vendorOptions"
            :key="vendor.id"
            :label="vendor.vendorName"
            :value="Number(vendor.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="数据类型" prop="dataTypeId">
        <el-select v-model="form.dataTypeId" placeholder="请选择数据类型" style="width: 100%">
          <el-option
            v-for="dt in datatypeOptions"
            :key="dt.id"
            :label="dt.typeName"
            :value="dt.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入接口描述" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio value="active">启用</el-radio>
          <el-radio value="inactive">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { createInterface, updateInterface } from '@/api/interface'
import type { ApiInterface, Vendor } from '@/types'

interface Props {
  modelValue: boolean
  formData?: ApiInterface | null
  mode: 'add' | 'edit'
  vendorOptions: Vendor[]
  datatypeOptions: { id: number; typeName: string }[]
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)

type InterfaceStatus = 'active' | 'inactive'

const form = ref<{
  interfaceCode: string
  interfaceName: string
  path: string
  vendorId?: number
  dataTypeId: number | undefined
  sort: number
  description: string
  status: InterfaceStatus
}>({
  interfaceCode: '',
  interfaceName: '',
  path: '',
  vendorId: undefined,
  dataTypeId: undefined,
  sort: 0,
  description: '',
  status: 'active'
})

const rules: FormRules = {
  interfaceCode: [{ required: true, message: '请输入接口编码', trigger: 'blur' }],
  interfaceName: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入接口路径', trigger: 'blur' }],
  dataTypeId: [{ required: true, message: '请选择数据类型', trigger: 'change' }]
}

watch(() => props.formData, (val) => {
  if (val) {
    form.value = {
      interfaceCode: val.interfaceCode || '',
      interfaceName: val.interfaceName || '',
      path: val.path || '',
      vendorId: val.vendorId,
      dataTypeId: val.dataTypeId,
      sort: val.sort ?? 0,
      description: val.description || '',
      status: val.status || 'active'
    }
  } else {
    form.value = {
      interfaceCode: '',
      interfaceName: '',
      path: '',
      vendorId: undefined,
      dataTypeId: undefined,
      sort: 0,
      description: '',
      status: 'active'
    }
  }
}, { immediate: true })

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  loading.value = true
  try {
    if (props.mode === 'add') {
      await createInterface(form.value)
      ElMessage.success('新增成功')
    } else {
      await updateInterface(props.formData!.id, form.value)
      ElMessage.success('更新成功')
    }
    emit('success')
    handleClose()
  } catch (error) {
    console.error('操作失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

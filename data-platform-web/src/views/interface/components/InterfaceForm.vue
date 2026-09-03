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
      <el-form-item label="数据类型" prop="dataTypeId">
        <el-select v-model="form.dataTypeId" placeholder="请选择数据类型" clearable style="width: 100%">
          <el-option
            v-for="dt in datatypeOptions"
            :key="dt.id"
            :label="dt.dataTypeName"
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
      <el-alert type="info" :closable="false" show-icon>
        <template #title>固定调用入口：POST /openapi/v1/query</template>
        请求体通过 apiCode 区分业务接口；新增接口默认停用，请先绑定厂商、配置主备并发布连接器。
      </el-alert>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button v-if="canSubmit" type="primary" @click="handleSubmit" :loading="loading">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { createInterface, updateInterface } from '@/api/interface'
import type { ApiInterface, DataType } from '@/types'

interface Props {
  modelValue: boolean
  formData?: ApiInterface | null
  mode: 'add' | 'edit'
  datatypeOptions: DataType[]
  canSubmit?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = ref<{
  interfaceCode: string
  interfaceName: string
  dataTypeId?: number | string
  sort: number
  description: string
}>({
  interfaceCode: '',
  interfaceName: '',
  dataTypeId: undefined,
  sort: 0,
  description: ''
})

const rules: FormRules = {
  interfaceCode: [{ required: true, message: '请输入接口编码', trigger: 'blur' }],
  interfaceName: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
  dataTypeId: [{ required: true, message: '请选择数据类型', trigger: 'change' }]
}

// 确保id类型一致的辅助函数
const normalizeId = (id: any): number | string | undefined => {
  if (id == null) return undefined
  const num = Number(id)
  return !isNaN(num) ? num : id
}

watch(() => props.formData, (val) => {
  
  if (val) {
    form.value = {
      interfaceCode: val.interfaceCode || '',
      interfaceName: val.interfaceName || '',
      dataTypeId: normalizeId(val.dataTypeId),
      sort: val.sort ?? 0,
      description: val.description || ''
    }
  } else {
    form.value = {
      interfaceCode: '',
      interfaceName: '',
      dataTypeId: undefined,
      sort: 0,
      description: ''
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
    const commonFields: Partial<ApiInterface> = {
      interfaceName: form.value.interfaceName,
      dataTypeId: form.value.dataTypeId != null ? Number(form.value.dataTypeId) : undefined,
      sort: form.value.sort,
      description: form.value.description
    }

    if (props.mode === 'add') {
      await createInterface({ ...commonFields, interfaceCode: form.value.interfaceCode })
      ElMessage.success('新增成功')
    } else {
      await updateInterface(props.formData!.id, commonFields)
      ElMessage.success('更新成功')
    }
    emit('success')
    handleClose()
  } catch {
    console.error('操作失败')
  } finally {
    loading.value = false
  }
}
</script>

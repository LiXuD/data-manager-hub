<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'add' ? '新增厂商' : '编辑厂商'"
    width="600px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="厂商编码" prop="code">
        <el-input v-model="form.code" placeholder="请输入厂商编码" :disabled="mode === 'edit'" />
      </el-form-item>
      <el-form-item label="厂商名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入厂商名称" />
      </el-form-item>
      <el-form-item label="联系人" prop="contact">
        <el-input v-model="form.contact" placeholder="请输入联系人" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="API地址" prop="url">
        <el-input v-model="form.url" placeholder="请输入API地址" />
      </el-form-item>
      <el-form-item label="认证方式" prop="authType">
        <el-select v-model="form.authType" placeholder="请选择认证方式">
          <el-option label="无" value="none" />
          <el-option label="Basic" value="basic" />
          <el-option label="OAuth" value="oauth" />
          <el-option label="API Key" value="api_key" />
          <el-option label="HMAC" value="hmac" />
        </el-select>
      </el-form-item>
      <el-form-item label="接口版本" prop="version">
        <el-input v-model="form.version" placeholder="请输入接口版本" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio value="enabled">启用</el-radio>
          <el-radio value="disabled">禁用</el-radio>
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
import { createVendor, updateVendor } from '@/api/vendor'
import type { Vendor } from '@/types'

interface Props {
  modelValue: boolean
  formData?: Vendor | null
  mode: 'add' | 'edit'
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = ref({
  code: '',
  name: '',
  contact: '',
  email: '',
  url: '',
  authType: 'none' as const,
  status: 'enabled' as const,
  version: 'v1'
})

const rules: FormRules = {
  code: [{ required: true, message: '请输入厂商编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入厂商名称', trigger: 'blur' }],
  url: [{ required: true, message: '请输入API地址', trigger: 'blur' }]
}

watch(() => props.formData, (val) => {
  if (val) {
    form.value = { ...val }
  } else {
    form.value = {
      code: '',
      name: '',
      contact: '',
      email: '',
      url: '',
      authType: 'none',
      status: 'enabled',
      version: 'v1'
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
      await createVendor(form.value)
      ElMessage.success('新增成功')
    } else {
      await updateVendor(props.formData!.id, form.value)
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

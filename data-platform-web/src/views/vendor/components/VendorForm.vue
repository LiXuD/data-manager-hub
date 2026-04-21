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
      <el-form-item label="厂商编码" prop="vendorCode">
        <el-input v-model="form.vendorCode" placeholder="请输入厂商编码" :disabled="mode === 'edit'" />
      </el-form-item>
      <el-form-item label="厂商名称" prop="vendorName">
        <el-input v-model="form.vendorName" placeholder="请输入厂商名称" />
      </el-form-item>
      <el-form-item label="厂商类型" prop="vendorType">
        <el-select v-model="form.vendorType" placeholder="请选择厂商类型">
          <el-option label="工商" value="business" />
          <el-option label="个人" value="personal" />
          <el-option label="信用" value="credit" />
          <el-option label="其他" value="other" />
        </el-select>
      </el-form-item>
      <el-form-item label="联系人" prop="contactPerson">
        <el-input v-model="form.contactPerson" placeholder="请输入联系人" />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
      </el-form-item>
      <el-form-item label="邮箱" prop="contactEmail">
        <el-input v-model="form.contactEmail" placeholder="请输入邮箱" />
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
  vendorCode: '',
  vendorName: '',
  vendorType: 'business',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  status: 'active' as const
})

const rules: FormRules = {
  vendorCode: [{ required: true, message: '请输入厂商编码', trigger: 'blur' }],
  vendorName: [{ required: true, message: '请输入厂商名称', trigger: 'blur' }]
}

watch(() => props.formData, (val) => {
  if (val) {
    form.value = {
      vendorCode: val.vendorCode || '',
      vendorName: val.vendorName || '',
      vendorType: val.vendorType || 'business',
      contactPerson: val.contactPerson || '',
      contactPhone: val.contactPhone || '',
      contactEmail: val.contactEmail || '',
      status: val.status || 'active'
    }
  } else {
    form.value = {
      vendorCode: '',
      vendorName: '',
      vendorType: 'business',
      contactPerson: '',
      contactPhone: '',
      contactEmail: '',
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
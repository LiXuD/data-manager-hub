<template>
  <el-dialog
    :model-value="modelValue"
    title="API配置"
    width="700px"
    @close="handleClose"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    >
      <template #title>
        配置接口的请求参数和响应格式定义
      </template>
    </el-alert>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
    >
      <el-form-item label="请求参数Schema" prop="requestSchema">
        <el-input
          v-model="form.requestSchema"
          type="textarea"
          :rows="8"
          placeholder="请输入JSON格式的请求参数Schema"
        />
        <div class="schema-tip">示例: {"type": "object", "properties": {"name": {"type": "string"}}}</div>
      </el-form-item>
      <el-form-item label="响应参数Schema" prop="responseSchema">
        <el-input
          v-model="form.responseSchema"
          type="textarea"
          :rows="8"
          placeholder="请输入JSON格式的响应参数Schema"
        />
        <div class="schema-tip">示例: {"type": "object", "properties": {"code": {"type": "integer"}, "data": {"type": "object"}}}</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">保存配置</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { getInterfaceById, updateInterface } from '@/api/interface'

interface Props {
  modelValue: boolean
  interfaceId?: number
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = ref({
  requestSchema: '',
  responseSchema: ''
})

const rules: FormRules = {}

// 监听弹窗打开，加载接口详情
watch(() => props.modelValue, async (val) => {
  if (val && props.interfaceId) {
    try {
      const res = await getInterfaceById(props.interfaceId)
      form.value = {
        requestSchema: res.requestSchema || '',
        responseSchema: res.responseSchema || ''
      }
    } catch (error) {
      console.error('加载接口详情失败:', error)
      ElMessage.error('加载接口详情失败')
    }
  } else if (!val) {
    form.value = {
      requestSchema: '',
      responseSchema: ''
    }
  }
})

const handleClose = () => {
  emit('update:modelValue', false)
}

const validateJson = (value: string): boolean => {
  if (!value) return true
  try {
    JSON.parse(value)
    return true
  } catch {
    return false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  // 验证JSON格式
  if (form.value.requestSchema && !validateJson(form.value.requestSchema)) {
    ElMessage.error('请求参数Schema格式错误，请输入有效的JSON')
    return
  }
  if (form.value.responseSchema && !validateJson(form.value.responseSchema)) {
    ElMessage.error('响应参数Schema格式错误，请输入有效的JSON')
    return
  }

  loading.value = true
  try {
    await updateInterface(props.interfaceId!, {
      requestSchema: form.value.requestSchema || undefined,
      responseSchema: form.value.responseSchema || undefined
    })
    emit('success')
    handleClose()
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存配置失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.schema-tip {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 8px;
}
</style>

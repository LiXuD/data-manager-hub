<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'add' ? '新增灰度规则' : '编辑灰度规则'"
    width="700px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="form.ruleName" placeholder="如: 用户服务V2灰度" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="服务名称" prop="serviceName">
            <el-input v-model="form.serviceName" placeholder="如: USER_API" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="目标版本" prop="version">
            <el-input v-model="form.version" placeholder="如: v2.0.0" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="流量权重" prop="weight">
            <el-slider v-model="form.weight" :min="0" :max="100" show-input />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="匹配条件" prop="conditionType">
            <el-select v-model="form.conditionType" style="width: 100%">
              <el-option
                v-for="item in CONDITION_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="条件值" prop="conditionValue">
            <el-input v-model="form.conditionValue" placeholder="根据条件类型填写" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              placeholder="选择开始时间"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="结束时间" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              placeholder="选择结束时间"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="规则用途说明" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio
            v-for="item in GRAY_RULE_STATUS_OPTIONS"
            :key="item.value"
            :value="item.value"
          >{{ item.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createGrayRule, updateGrayRule } from '@/api/graylog'
import type { GrayRule } from '@/api/graylog'
import { GRAY_RULE_STATUS_OPTIONS, CONDITION_TYPE_OPTIONS } from '@/constants'

interface Props {
  modelValue: boolean
  formData?: GrayRule | null
  mode: 'add' | 'edit'
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref<FormInstance>()
const loading = ref(false)

type GrayRuleForm = Omit<GrayRule, 'id' | 'createdAt'>

const defaultForm: GrayRuleForm = {
  ruleName: '',
  serviceName: '',
  version: '',
  weight: 10,
  conditionType: 'random',
  conditionValue: '',
  description: '',
  status: 'active',
  startTime: '',
  endTime: ''
}

const form = ref<GrayRuleForm>({ ...defaultForm })

const rules: FormRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  serviceName: [{ required: true, message: '请输入服务名称', trigger: 'blur' }]
}

watch(() => props.formData, (val) => {
  if (val) {
    form.value = {
      ruleName: val.ruleName || '',
      serviceName: val.serviceName || '',
      version: val.version || '',
      weight: val.weight ?? 10,
      conditionType: val.conditionType ?? 'random',
      conditionValue: val.conditionValue || '',
      description: val.description || '',
      status: val.status || 'active',
      startTime: val.startTime || '',
      endTime: val.endTime || ''
    }
  } else {
    form.value = { ...defaultForm }
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
      await createGrayRule(form.value)
      ElMessage.success('新增成功')
    } else {
      await updateGrayRule(props.formData!.id, form.value)
      ElMessage.success('更新成功')
    }
    emit('success')
    handleClose()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    loading.value = false
  }
}
</script>

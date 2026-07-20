<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElInput, ElRadioGroup, ElRadioButton, ElFormItem, ElCheckbox, ElCheckboxGroup, ElMessage } from 'element-plus'
import type { SignType } from '@/types'

interface SignConfigData {
  type: SignType
  secretKey?: string
  signFields?: string[]
}

interface Props {
  modelValue: SignConfigData
  availableFields?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({ type: 'NONE' }),
  availableFields: () => []
})

const emit = defineEmits<{
  'update:modelValue': [value: SignConfigData]
  'change': [value: SignConfigData]
}>()

// 直接使用 computed，不维护内部副本
const config = computed(() => props.modelValue)

// 更新配置
function updateConfig(updates: Partial<SignConfigData>) {
  const newConfig = { ...config.value, ...updates }
  emit('update:modelValue', newConfig)
  emit('change', newConfig)
}

// 更新类型（重置其他字段）
function updateType(newType: SignType) {
  const base: SignConfigData = { type: newType }
  if (newType !== 'NONE') {
    base.secretKey = ''
    base.signFields = []
  }
  emit('update:modelValue', base)
  emit('change', base)
}

// 更新签名字段
function updateSignFields(fields: string[]) {
  updateConfig({ signFields: fields })
}

// 移除签名字段
function removeField(field: string) {
  const newFields = config.value.signFields?.filter(f => f !== field) || []
  updateConfig({ signFields: newFields })
}

// 自定义字段输入（本地状态，不影响父组件）
const customFieldInput = ref('')
function addCustomField() {
  if (!customFieldInput.value.trim()) return
  if (config.value.signFields?.includes(customFieldInput.value)) {
    ElMessage.warning('字段已存在')
    return
  }
  const newFields = [...(config.value.signFields || []), customFieldInput.value.trim()]
  updateConfig({ signFields: newFields })
  customFieldInput.value = ''
}

// 签名类型选项
const signTypeOptions = [
  { label: '无签名', value: 'NONE', desc: '不需要签名验证' },
  { label: 'HMAC-SHA256', value: 'HMAC_SHA256', desc: '使用密钥进行 HMAC-SHA256 签名' },
  { label: 'MD5', value: 'MD5', desc: '使用盐值进行 MD5 签名' }
]

// 获取当前选中类型的描述
const currentTypeDesc = computed(() => {
  const opt = signTypeOptions.find(o => o.value === config.value.type)
  return opt?.desc || ''
})

// 常用签名字段
const commonSignFields = [
  { label: 'timestamp (时间戳)', value: 'timestamp' },
  { label: 'nonce (随机数)', value: 'nonce' },
  { label: 'appKey (应用标识)', value: 'appKey' },
  { label: 'appId (应用ID)', value: 'appId' }
]

// 可选字段（合并常用字段和传入的自定义字段）
const selectableFields = computed(() => {
  const fields = [...commonSignFields]
  props.availableFields.forEach(f => {
    if (!fields.some(item => item.value === f)) {
      fields.push({ label: f, value: f })
    }
  })
  return fields
})

// 生成签名示例
const generateSignExample = computed(() => {
  if (config.value.type === 'NONE') return null

  const fields = config.value.signFields || []
  const exampleParams = fields.map(f => {
    switch (f) {
      case 'timestamp': return `${f}=1704067200`
      case 'nonce': return `${f}=abc123xyz`
      case 'appKey':
      case 'appId': return `${f}=your_app_key`
      default: return `${f}=value`
    }
  })

  const signContent = exampleParams.join('&')

  if (config.value.type === 'HMAC_SHA256') {
    return {
      content: signContent,
      key: config.value.secretKey || '${vendor.sign.key}',
      result: 'HMAC-SHA256(content, secretKey)'
    }
  }

  if (config.value.type === 'MD5') {
    return {
      content: signContent + '&key=' + (config.value.secretKey || '${salt}'),
      result: 'MD5(content + key)'
    }
  }

  return null
})
</script>

<template>
  <div class="sign-config">
    <div class="config-header">
      <h4>签名配置</h4>
      <p>配置请求签名方式，防止请求被篡改</p>
    </div>

    <!-- 签名类型选择 -->
    <el-form-item label="签名方式">
      <el-radio-group :model-value="config.type" @update:model-value="(val) => updateType(val as SignType)">
        <el-radio-button
          v-for="opt in signTypeOptions"
          :key="opt.value"
          :value="opt.value"
        >
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
      <div class="type-desc">{{ currentTypeDesc }}</div>
    </el-form-item>

    <!-- 签名配置表单 -->
    <template v-if="config.type !== 'NONE'">
      <div class="sign-form">
        <!-- 密钥配置 -->
        <el-form-item label="密钥">
          <el-input
            :model-value="config.secretKey"
            :placeholder="config.type === 'HMAC_SHA256' ? '输入密钥或使用变量 ${vendor.sign.key}' : '输入盐值或使用变量 ${salt}'"
            show-password
            @update:model-value="(val: string) => updateConfig({ secretKey: val })"
          />
          <div class="form-hint">
            支持从配置中心引用变量：${vendor.sign.key}、${salt}
          </div>
        </el-form-item>

        <!-- 签名字段选择 -->
        <el-form-item label="签名字段">
          <div class="sign-fields">
            <!-- 已选择的字段 -->
            <div class="selected-fields">
              <span
                v-for="field in config.signFields"
                :key="field"
                class="field-tag"
              >
                {{ field }}
                <button type="button" class="remove-btn" @click="removeField(field)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M18 6L6 18M6 6l12 12"/>
                  </svg>
                </button>
              </span>
            </div>

            <!-- 快捷选择 -->
            <div class="quick-select">
              <el-checkbox-group
                :model-value="config.signFields || []"
                @update:model-value="(val) => updateSignFields(val as string[])"
              >
                <el-checkbox
                  v-for="item in selectableFields"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </div>

            <!-- 自定义添加 -->
            <div class="custom-field">
              <el-input
                v-model="customFieldInput"
                placeholder="自定义字段名"
                size="small"
                @keyup.enter="addCustomField"
              />
              <button type="button" class="add-btn" @click="addCustomField">
                添加
              </button>
            </div>
          </div>
        </el-form-item>
      </div>

      <!-- 签名示例 -->
      <div v-if="generateSignExample" class="sign-example">
        <div class="example-header">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 16v-4M12 8h.01"/>
          </svg>
          签名计算示例
        </div>
        <div class="example-content">
          <div class="example-row">
            <span class="label">待签名内容:</span>
            <code>{{ generateSignExample.content }}</code>
          </div>
          <div v-if="config.type === 'HMAC_SHA256'" class="example-row">
            <span class="label">签名密钥:</span>
            <code>{{ generateSignExample.key }}</code>
          </div>
          <div class="example-row">
            <span class="label">计算方式:</span>
            <code>{{ generateSignExample.result }}</code>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.sign-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.config-header h4 {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.config-header p {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.type-desc {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.sign-form {
  padding: 16px;
  background: var(--color-bg-light);
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 8px;
}

.sign-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.selected-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
}

.field-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-text-primary);
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-text-tertiary);
  transition: color 0.2s;
}

.remove-btn:hover {
  color: var(--color-danger);
}

.remove-btn svg {
  width: 12px;
  height: 12px;
}

.quick-select {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.custom-field {
  display: flex;
  gap: 8px;
}

.custom-field :deep(.el-input) {
  flex: 1;
}

.add-btn {
  padding: 4px 12px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.add-btn:hover {
  opacity: 0.8;
}

.sign-example {
  padding: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.example-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}

.example-header svg {
  width: 16px;
  height: 16px;
  color: var(--color-info);
}

.example-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.example-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
}

.example-row .label {
  flex-shrink: 0;
  width: 80px;
  color: var(--color-text-tertiary);
}

.example-row code {
  flex: 1;
  padding: 4px 8px;
  background: var(--color-bg);
  border-radius: 4px;
  font-family: var(--font-mono);
  color: var(--color-primary);
  word-break: break-all;
}
</style>

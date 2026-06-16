<script setup lang="ts">
import { computed } from 'vue'
import { ElInput, ElRadioGroup, ElRadioButton, ElFormItem } from 'element-plus'
import type { AuthConfig, AuthType } from '@/types'

interface Props {
  modelValue: AuthConfig
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({ type: 'NONE' })
})

const emit = defineEmits<{
  'update:modelValue': [value: AuthConfig]
  'change': [value: AuthConfig]
}>()

// 直接使用 computed，不维护内部副本
const config = computed(() => props.modelValue)

// 更新配置
function updateConfig(updates: Partial<AuthConfig>) {
  const newConfig = { ...config.value, ...updates }
  emit('update:modelValue', newConfig)
  emit('change', newConfig)
}

// 更新类型（重置其他字段）
function updateType(newType: AuthType) {
  const base: AuthConfig = { type: newType }
  switch (newType) {
    case 'BASIC':
      base.username = ''
      base.password = ''
      break
    case 'BEARER':
      base.token = ''
      break
    case 'API_KEY':
      base.apiKeyName = ''
      base.apiKeyValue = ''
      base.apiKeyLocation = 'header'
      break
  }
  emit('update:modelValue', base)
  emit('change', base)
}

const authTypeOptions: { label: string; value: AuthType; desc: string }[] = [
  { label: '无认证', value: 'NONE', desc: '不需要任何认证' },
  { label: 'Basic Auth', value: 'BASIC', desc: 'HTTP Basic 认证' },
  { label: 'Bearer Token', value: 'BEARER', desc: 'OAuth2 Bearer Token' },
  { label: 'API Key', value: 'API_KEY', desc: '自定义 API Key' }
]

const currentTypeDesc = computed(() => {
  const opt = authTypeOptions.find(o => o.value === config.value.type)
  return opt?.desc || ''
})

const apiKeyLocationOptions = [
  { label: '请求头', value: 'header' as const },
  { label: '查询参数', value: 'query' as const }
]

const getPreviewString = computed(() => {
  switch (config.value.type) {
    case 'NONE':
      return '无需认证'
    case 'BASIC':
      if (config.value.username && config.value.password) {
        return `Authorization: Basic base64(${config.value.username}:${config.value.password})`
      }
      return '请填写用户名和密码'
    case 'BEARER':
      if (config.value.token) {
        return `Authorization: Bearer ${config.value.token}`
      }
      return '请填写 Token'
    case 'API_KEY':
      if (config.value.apiKeyName && config.value.apiKeyValue) {
        if (config.value.apiKeyLocation === 'header') {
          return `${config.value.apiKeyName}: ${config.value.apiKeyValue}`
        }
        return `?${config.value.apiKeyName}=${config.value.apiKeyValue}`
      }
      return '请填写 API Key 名称和值'
    default:
      return ''
  }
})

</script>

<template>
  <div class="auth-config">
    <div class="config-header">
      <h4>认证配置</h4>
      <p>配置访问厂商 API 的认证方式</p>
    </div>

    <el-form-item label="认证方式">
      <el-radio-group :model-value="config.type" @update:model-value="(val) => updateType(val as AuthType)">
        <el-radio-button
          v-for="opt in authTypeOptions"
          :key="opt.value"
          :value="opt.value"
        >
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
      <div class="type-desc">{{ currentTypeDesc }}</div>
    </el-form-item>

    <template v-if="config.type === 'BASIC'">
      <div class="auth-form">
        <el-form-item label="用户名">
          <el-input
            :model-value="config.username"
            placeholder="请输入用户名"
            @update:model-value="(val: string) => updateConfig({ username: val })"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            :model-value="config.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @update:model-value="(val: string) => updateConfig({ password: val })"
          />
        </el-form-item>
      </div>
    </template>

    <template v-else-if="config.type === 'BEARER'">
      <div class="auth-form">
        <el-form-item label="Token">
          <el-input
            :model-value="config.token"
            type="textarea"
            :rows="3"
            placeholder="请输入 Bearer Token，支持变量 ${token}"
            @update:model-value="(val: string) => updateConfig({ token: val })"
          />
        </el-form-item>
        <div class="form-hint">
          支持变量占位符：${token} - 从配置中心获取的令牌
        </div>
      </div>
    </template>

    <template v-else-if="config.type === 'API_KEY'">
      <div class="auth-form">
        <el-form-item label="Key 名称">
          <el-input
            :model-value="config.apiKeyName"
            placeholder="如: X-API-Key"
            @update:model-value="(val: string) => updateConfig({ apiKeyName: val })"
          />
        </el-form-item>
        <el-form-item label="Key 值">
          <el-input
            :model-value="config.apiKeyValue"
            placeholder="请输入 API Key 值，支持变量 ${apiKey}"
            show-password
            @update:model-value="(val: string) => updateConfig({ apiKeyValue: val })"
          />
        </el-form-item>
        <el-form-item label="传递位置">
          <el-radio-group
            :model-value="config.apiKeyLocation"
            @update:model-value="(val) => updateConfig({ apiKeyLocation: val as 'header' | 'query' })"
          >
            <el-radio-button
              v-for="opt in apiKeyLocationOptions"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
      </div>
    </template>

    <div class="auth-preview">
      <div class="preview-label">请求预览</div>
      <div class="preview-content">
        <code>{{ getPreviewString }}</code>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-config {
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

.auth-form {
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

.auth-preview {
  padding: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.preview-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.preview-content {
  padding: 8px 12px;
  background: var(--color-bg);
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-text-primary);
  word-break: break-all;
}

.preview-content code {
  background: none;
  padding: 0;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import KeyValueEditor from '@/components/common/KeyValueEditor.vue'
import type { KeyValueItem } from '@/components/common/KeyValueEditor.vue'

interface Props {
  modelValue: KeyValueItem[]
}

defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: KeyValueItem[]]
  'change': [value: KeyValueItem[]]
}>()

// 预设常用请求头
const presetHeaders = [
  { label: 'Content-Type', value: 'Content-Type' },
  { label: 'Authorization', value: 'Authorization' },
  { label: 'Accept', value: 'Accept' },
  { label: 'Accept-Encoding', value: 'Accept-Encoding' },
  { label: 'Accept-Language', value: 'Accept-Language' },
  { label: 'Cache-Control', value: 'Cache-Control' },
  { label: 'User-Agent', value: 'User-Agent' },
  { label: 'X-Request-ID', value: 'X-Request-ID' },
  { label: 'X-API-Key', value: 'X-API-Key' },
  { label: 'X-Auth-Token', value: 'X-Auth-Token' }
]

// Content-Type 值模板
const contentTypeValues = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/x-www-form-urlencoded', value: 'application/x-www-form-urlencoded' },
  { label: 'multipart/form-data', value: 'multipart/form-data' },
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/html', value: 'text/html' },
  { label: 'application/xml', value: 'application/xml' }
]

// Authorization 值模板
const authValues = [
  { label: 'Bearer ${token}', value: 'Bearer ${token}' },
  { label: 'Bearer ${apiKey}', value: 'Bearer ${apiKey}' },
  { label: 'Basic ${credentials}', value: 'Basic ${credentials}' },
  { label: 'ApiKey ${apiKey}', value: 'ApiKey ${apiKey}' }
]

// Accept 值模板
const acceptValues = [
  { label: 'application/json', value: 'application/json' },
  { label: '*/*', value: '*/*' },
  { label: 'text/html', value: 'text/html' },
  { label: 'application/xml', value: 'application/xml' }
]

// 根据键名获取值模板
const valueTemplates = computed(() => {
  // 这里返回通用的值模板
  // 具体的模板选择由组件内部处理
  return [
    ...contentTypeValues,
    ...authValues,
    ...acceptValues,
    { label: '${timestamp}', value: '${timestamp}' },
    { label: '${nonce}', value: '${nonce}' },
    { label: '${signature}', value: '${signature}' }
  ]
})

// 变量提示
const variableHints = [
  { name: '${token}', desc: '认证令牌' },
  { name: '${apiKey}', desc: 'API密钥' },
  { name: '${timestamp}', desc: '时间戳' },
  { name: '${nonce}', desc: '随机字符串' },
  { name: '${signature}', desc: '签名值' },
  { name: '${credentials}', desc: 'Base64编码的凭据' }
]
</script>

<template>
  <div class="header-editor">
    <div class="editor-header">
      <h4>请求头配置</h4>
      <p>配置发送到厂商API的请求头信息，支持变量占位符</p>
    </div>

    <KeyValueEditor
      :model-value="modelValue"
      :placeholder="{ key: '请求头名称', value: '请求头值' }"
      :preset-options="presetHeaders"
      :value-templates="valueTemplates"
      @update:model-value="emit('update:modelValue', $event)"
      @change="emit('change', $event)"
    />

    <!-- 变量提示 -->
    <div class="variable-hints">
      <div class="hints-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 16v-4M12 8h.01"/>
        </svg>
        可用变量
      </div>
      <div class="hints-list">
        <div v-for="hint in variableHints" :key="hint.name" class="hint-item">
          <code>{{ hint.name }}</code>
          <span>{{ hint.desc }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.header-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.editor-header h4 {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.editor-header p {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.variable-hints {
  background: var(--color-bg-light);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 12px;
}

.hints-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.hints-title svg {
  width: 14px;
  height: 14px;
}

.hints-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hint-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.hint-item code {
  padding: 2px 6px;
  background: var(--color-surface);
  border-radius: 4px;
  font-family: var(--font-mono);
  color: var(--color-primary);
}

.hint-item span {
  color: var(--color-text-tertiary);
}
</style>

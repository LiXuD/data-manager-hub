<script setup lang="ts">
import { computed } from 'vue'
import { ElRadioGroup, ElRadioButton, ElFormItem } from 'element-plus'
import JsonEditor from '@/components/common/JsonEditor.vue'
import KeyValueEditor from '@/components/common/KeyValueEditor.vue'
import type { KeyValueItem } from '@/components/common/KeyValueEditor.vue'
import type { ContentType } from '@/types'

interface Props {
  modelValue: string
  contentType?: ContentType
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  contentType: 'application/json'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:contentType': [value: ContentType]
  'change': [value: string, contentType: ContentType]
}>()

// 当前内容类型
const currentContentType = computed(() => props.contentType)

// 解析 URL 编码格式
function parseUrlEncoded(content: string): KeyValueItem[] {
  if (!content) return []
  return content.split('&').map(pair => {
    const [key, value = ''] = pair.split('=')
    return {
      key: decodeURIComponent(key),
      value: decodeURIComponent(value.replace(/\+/g, ' ')),
      enabled: true
    }
  }).filter(item => item.key)
}

// 计算属性：JSON 内容
const jsonContent = computed(() => {
  if (props.contentType === 'application/json') {
    return props.modelValue
  }
  return ''
})

// 计算属性：表单项
const formItems = computed(() => {
  if (props.contentType === 'application/x-www-form-urlencoded') {
    return parseUrlEncoded(props.modelValue)
  }
  return []
})

// 计算属性：原始内容
const rawContent = computed(() => {
  if (props.contentType !== 'application/json' && props.contentType !== 'application/x-www-form-urlencoded') {
    return props.modelValue
  }
  return ''
})

// 序列化内容
function serializeContent(type: ContentType, json: string, form: KeyValueItem[], raw: string): string {
  switch (type) {
    case 'application/json':
      return json
    case 'application/x-www-form-urlencoded':
      return form
        .filter(item => item.enabled && item.key)
        .map(item => `${encodeURIComponent(item.key)}=${encodeURIComponent(item.value)}`)
        .join('&')
    default:
      return raw
  }
}

// 更新内容类型
function updateContentType(newType: ContentType) {
  // 切换类型时转换内容
  const currentContent = serializeContent(currentContentType.value, jsonContent.value, formItems.value, rawContent.value)
  let newContent = currentContent

  if (currentContent) {
    try {
      if (newType === 'application/json') {
        // 从 form-urlencoded 转换到 JSON
        if (formItems.value.length > 0) {
          const obj: Record<string, string> = {}
          formItems.value.filter(i => i.enabled && i.key).forEach(item => {
            obj[item.key] = item.value
          })
          newContent = JSON.stringify(obj, null, 2)
        }
      } else if (newType === 'application/x-www-form-urlencoded') {
        // 从 JSON 转换到 form-urlencoded
        if (jsonContent.value) {
          const obj = JSON.parse(jsonContent.value)
          const items = Object.entries(obj).map(([key, value]) => ({
            key,
            value: String(value),
            enabled: true
          }))
          newContent = items
            .filter(item => item.enabled && item.key)
            .map(item => `${encodeURIComponent(item.key)}=${encodeURIComponent(item.value)}`)
            .join('&')
        }
      }
    } catch {
      // 转换失败，保留原值
    }
  }

  emit('update:contentType', newType)
  emit('update:modelValue', newContent)
  emit('change', newContent, newType)
}

// 更新 JSON 内容
function updateJsonContent(value: string) {
  emit('update:modelValue', value)
  emit('change', value, currentContentType.value)
}

// 更新表单内容
function updateFormItems(items: KeyValueItem[]) {
  const content = items
    .filter(item => item.enabled && item.key)
    .map(item => `${encodeURIComponent(item.key)}=${encodeURIComponent(item.value)}`)
    .join('&')
  emit('update:modelValue', content)
  emit('change', content, currentContentType.value)
}

// 更新原始内容
function updateRawContent(value: string) {
  emit('update:modelValue', value)
  emit('change', value, currentContentType.value)
}

// Content-Type 选项
const contentTypeOptions = [
  { label: 'JSON', value: 'application/json' as const, desc: 'application/json' },
  { label: 'Form', value: 'application/x-www-form-urlencoded' as const, desc: 'application/x-www-form-urlencoded' },
  { label: 'Text', value: 'text/plain' as const, desc: 'text/plain' },
  { label: 'Raw', value: 'raw' as const, desc: '原始文本' }
]

// 预设请求体字段
const presetBodyFields = [
  { label: 'companyName', value: 'companyName' },
  { label: 'creditCode', value: 'creditCode' },
  { label: 'page', value: 'page' },
  { label: 'pageSize', value: 'pageSize' },
  { label: 'keyword', value: 'keyword' }
]

// 变量模板
const variableTemplates = [
  { label: '${timestamp}', value: '${timestamp}' },
  { label: '${nonce}', value: '${nonce}' },
  { label: '${sign}', value: '${sign}' },
  { label: '${userId}', value: '${userId}' }
]

// 变量提示
const variableHints = [
  { name: '${timestamp}', desc: '当前时间戳' },
  { name: '${nonce}', desc: '随机字符串' },
  { name: '${sign}', desc: '签名值' },
  { name: '${userId}', desc: '用户ID' },
  { name: '${requestId}', desc: '请求ID' },
  { name: '${callback}', desc: '回调地址' }
]

// 暴露方法
defineExpose({
  currentContentType
})
</script>

<template>
  <div class="request-body-editor">
    <div class="editor-header">
      <h4>请求体配置</h4>
      <p>配置发送到厂商API的请求体内容，支持变量占位符</p>
    </div>

    <!-- Content-Type 选择 -->
    <el-form-item label="Content-Type">
      <el-radio-group :model-value="currentContentType" @update:model-value="(val) => updateContentType(val as ContentType)">
        <el-radio-button
          v-for="opt in contentTypeOptions"
          :key="opt.value"
          :value="opt.value"
        >
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
      <div class="type-desc">{{ contentTypeOptions.find(o => o.value === currentContentType)?.desc }}</div>
    </el-form-item>

    <!-- JSON 编辑器 -->
    <template v-if="currentContentType === 'application/json'">
      <div class="body-section">
        <JsonEditor
          :model-value="jsonContent"
          placeholder='{"key": "value"}'
          :rows="10"
          @update:model-value="updateJsonContent"
        />
      </div>
    </template>

    <!-- Form 表单编辑器 -->
    <template v-else-if="currentContentType === 'application/x-www-form-urlencoded'">
      <div class="body-section">
        <KeyValueEditor
          :model-value="formItems"
          :placeholder="{ key: '字段名', value: '字段值' }"
          :preset-options="presetBodyFields"
          :value-templates="variableTemplates"
          @update:model-value="updateFormItems"
        />
      </div>
    </template>

    <!-- 原始文本 -->
    <template v-else>
      <div class="body-section">
        <textarea
          :value="rawContent"
          class="raw-textarea"
          placeholder="输入原始请求体内容"
          rows="10"
          @input="(e) => updateRawContent((e.target as HTMLTextAreaElement).value)"
        />
      </div>
    </template>

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
.request-body-editor {
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

.type-desc {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.body-section {
  min-height: 200px;
}

.raw-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  background: var(--color-bg);
  color: var(--color-text-primary);
}

.raw-textarea:focus {
  outline: none;
  border-color: var(--color-primary);
}

.raw-textarea::placeholder {
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

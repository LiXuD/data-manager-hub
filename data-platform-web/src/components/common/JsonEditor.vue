<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

interface Props {
  modelValue: string
  placeholder?: string
  rows?: number
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '请输入 JSON 格式内容',
  rows: 8,
  readonly: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'change': [value: string]
  'validate': [valid: boolean, error?: string]
}>()

// 直接使用 computed
const content = computed(() => props.modelValue)

// 本地状态
const error = ref('')
const isFormatted = ref(false)

// 验证 JSON
function validateJson(value: string): boolean {
  if (!value.trim()) {
    error.value = ''
    isFormatted.value = false
    emit('validate', true)
    return true
  }

  try {
    JSON.parse(value)
    error.value = ''
    emit('validate', true)
    return true
  } catch (e) {
    const match = (e as Error).message.match(/position (\d+)/)
    const position = match ? parseInt(match[1]) : null
    error.value = position
      ? `JSON 格式错误，位置 ${position}`
      : 'JSON 格式错误'
    emit('validate', false, error.value)
    return false
  }
}

// 更新内容
function updateContent(value: string) {
  emit('update:modelValue', value)
  emit('change', value)
  validateJson(value)
}

// 格式化 JSON
function formatJson() {
  if (!content.value.trim()) return

  try {
    const parsed = JSON.parse(content.value)
    const formatted = JSON.stringify(parsed, null, 2)
    emit('update:modelValue', formatted)
    emit('change', formatted)
    isFormatted.value = true
    ElMessage.success('格式化成功')
  } catch {
    ElMessage.error('JSON 格式错误，无法格式化')
  }
}

// 压缩 JSON
function minifyJson() {
  if (!content.value.trim()) return

  try {
    const parsed = JSON.parse(content.value)
    const minified = JSON.stringify(parsed)
    emit('update:modelValue', minified)
    emit('change', minified)
    isFormatted.value = false
    ElMessage.success('压缩成功')
  } catch {
    ElMessage.error('JSON 格式错误，无法压缩')
  }
}

// 清空
function clearContent() {
  emit('update:modelValue', '')
  emit('change', '')
  error.value = ''
  isFormatted.value = false
}

// 获取解析后的对象
function getParsedJson(): unknown {
  if (!content.value.trim()) return null
  try {
    return JSON.parse(content.value)
  } catch {
    return null
  }
}

// 设置内容
function setContent(value: string | object) {
  const newContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2)
  emit('update:modelValue', newContent)
  emit('change', newContent)
}

// 暴露方法
defineExpose({
  formatJson,
  minifyJson,
  clearContent,
  validateJson: () => validateJson(content.value),
  getParsedJson,
  setContent
})
</script>

<template>
  <div class="json-editor">
    <!-- 工具栏 -->
    <div class="json-toolbar">
      <div class="toolbar-left">
        <button
          type="button"
          class="tool-btn"
          @click="formatJson"
          :disabled="readonly || !content.trim()"
          title="格式化 (美化)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 6h16M4 12h16M4 18h10"/>
          </svg>
          格式化
        </button>
        <button
          type="button"
          class="tool-btn"
          @click="minifyJson"
          :disabled="readonly || !content.trim()"
          title="压缩 (单行)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 6h16M4 12h16M4 18h16"/>
          </svg>
          压缩
        </button>
        <button
          type="button"
          class="tool-btn"
          @click="clearContent"
          :disabled="readonly || !content.trim()"
          title="清空"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
          </svg>
          清空
        </button>
      </div>
      <div class="toolbar-right">
        <span v-if="error" class="error-badge">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 8v4M12 16h.01"/>
          </svg>
          {{ error }}
        </span>
        <span v-else-if="content.trim()" class="valid-badge">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
            <path d="M22 4L12 14.01l-3-3"/>
          </svg>
          格式正确
        </span>
      </div>
    </div>

    <!-- 编辑器 -->
    <div class="json-content" :class="{ 'has-error': error }">
      <textarea
        :value="content"
        :placeholder="placeholder"
        :rows="rows"
        :readonly="readonly"
        class="json-textarea"
        spellcheck="false"
        @input="(e) => updateContent((e.target as HTMLTextAreaElement).value)"
      />
      <div class="line-numbers" aria-hidden="true">
        <span v-for="n in Math.max(rows, content.split('\n').length)" :key="n">{{ n }}</span>
      </div>
    </div>

    <!-- 提示 -->
    <div class="json-hint">
      <span>支持标准 JSON 格式，变量占位符格式: ${variableName}</span>
    </div>
  </div>
</template>

<style scoped>
.json-editor {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.json-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--color-bg-light);
  border-bottom: 1px solid var(--color-border);
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.tool-btn:hover:not(:disabled) {
  background: var(--color-bg-light);
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tool-btn svg {
  width: 14px;
  height: 14px;
}

.error-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-danger);
}

.error-badge svg {
  width: 14px;
  height: 14px;
}

.valid-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-success);
}

.valid-badge svg {
  width: 14px;
  height: 14px;
}

.json-content {
  position: relative;
  background: var(--color-bg);
}

.json-content.has-error {
  background: rgba(239, 68, 68, 0.02);
}

.json-textarea {
  display: block;
  width: 100%;
  padding: 12px;
  padding-left: 48px;
  border: none;
  outline: none;
  resize: vertical;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  background: transparent;
  color: var(--color-text-primary);
  min-height: calc(1.6em * v-bind(rows) + 24px);
}

.json-textarea::placeholder {
  color: var(--color-text-tertiary);
}

.line-numbers {
  position: absolute;
  top: 0;
  left: 0;
  width: 36px;
  padding: 12px 8px;
  text-align: right;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-tertiary);
  background: var(--color-bg-light);
  border-right: 1px solid var(--color-border);
  user-select: none;
  pointer-events: none;
}

.line-numbers span {
  display: block;
}

.json-hint {
  padding: 8px 12px;
  background: var(--color-bg-light);
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-tertiary);
}
</style>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElButton, ElInput, ElSwitch, ElSelect, ElOption, ElTooltip, ElMessage } from 'element-plus'

export interface KeyValueItem {
  key: string
  value: string
  enabled: boolean
  description?: string
}

interface Props {
  modelValue: KeyValueItem[]
  placeholder?: {
    key?: string
    value?: string
  }
  presetOptions?: { label: string; value: string }[]
  valueTemplates?: { label: string; value: string }[]
  showEnable?: boolean
  showDescription?: boolean
  allowDuplicateKeys?: boolean
  maxItems?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  placeholder: () => ({ key: '键', value: '值' }),
  showEnable: true,
  showDescription: false,
  allowDuplicateKeys: false,
  maxItems: 50
})

const emit = defineEmits<{
  'update:modelValue': [value: KeyValueItem[]]
  'change': [value: KeyValueItem[]]
}>()

const items = ref<KeyValueItem[]>([])

// 同步外部值
watch(() => props.modelValue, (val) => {
  items.value = val ? [...val] : []
}, { immediate: true, deep: true })

// 监听内部变化并同步
watch(items, (val) => {
  emit('update:modelValue', val)
  emit('change', val)
}, { deep: true })

// 检查是否有重复键
const duplicateKeys = computed(() => {
  const keyCounts: Record<string, number> = {}
  items.value.forEach(item => {
    if (item.key) {
      keyCounts[item.key] = (keyCounts[item.key] || 0) + 1
    }
  })
  return Object.keys(keyCounts).filter(key => keyCounts[key] > 1)
})

// 添加新项
const addItem = () => {
  if (items.value.length >= props.maxItems) {
    ElMessage.warning(`最多添加 ${props.maxItems} 项`)
    return
  }
  items.value.push({
    key: '',
    value: '',
    enabled: true,
    description: ''
  })
}

// 删除项
const removeItem = (index: number) => {
  items.value.splice(index, 1)
}

// 复制项
const copyItem = (index: number) => {
  const item = items.value[index]
  items.value.splice(index + 1, 0, { ...item })
}

// 清空所有
const clearAll = () => {
  items.value = []
}

// 导入 JSON
const importJson = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const json = JSON.parse(text)
      if (typeof json === 'object' && json !== null) {
        items.value = Object.entries(json).map(([key, value]) => ({
          key,
          value: String(value),
          enabled: true
        }))
        ElMessage.success('导入成功')
      }
    } catch {
      ElMessage.error('JSON 格式错误')
    }
  }
  input.click()
}

// 导出 JSON
const exportJson = () => {
  const json: Record<string, string> = {}
  items.value.filter(item => item.enabled).forEach(item => {
    json[item.key] = item.value
  })
  const blob = new Blob([JSON.stringify(json, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'key-value-config.json'
  a.click()
  URL.revokeObjectURL(url)
}

// 获取 JSON 字符串
const getJsonString = () => {
  const json: Record<string, string> = {}
  items.value.filter(item => item.enabled && item.key).forEach(item => {
    json[item.key] = item.value
  })
  return JSON.stringify(json)
}

// 预设键选择
const handlePresetKey = (index: number, value: string) => {
  items.value[index].key = value
}

// 值模板选择
const handleValueTemplate = (index: number, value: string) => {
  items.value[index].value = value
}

// 暴露方法
defineExpose({
  getJsonString,
  clearAll,
  importJson,
  exportJson
})
</script>

<template>
  <div class="kv-editor">
    <!-- 工具栏 -->
    <div class="kv-toolbar">
      <el-button size="small" @click="addItem">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
          <path d="M12 5v14M5 12h14"/>
        </svg>
        添加
      </el-button>
      <el-button size="small" @click="importJson">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12"/>
        </svg>
        导入
      </el-button>
      <el-button size="small" @click="exportJson" :disabled="items.length === 0">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"/>
        </svg>
        导出
      </el-button>
      <el-button size="small" type="danger" plain @click="clearAll" :disabled="items.length === 0">
        清空
      </el-button>
    </div>

    <!-- 表头 -->
    <div class="kv-header">
      <span class="col-key">{{ placeholder.key || '键' }}</span>
      <span class="col-value">{{ placeholder.value || '值' }}</span>
      <span v-if="showEnable" class="col-enabled">启用</span>
      <span class="col-actions">操作</span>
    </div>

    <!-- 列表 -->
    <div class="kv-list">
      <div v-if="items.length === 0" class="kv-empty">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <path d="M12 8v8M8 12h8"/>
        </svg>
        <span>点击"添加"按钮开始配置</span>
      </div>

      <div
        v-for="(item, index) in items"
        :key="index"
        class="kv-item"
        :class="{ disabled: !item.enabled, 'has-error': duplicateKeys.includes(item.key) && !allowDuplicateKeys }"
      >
        <!-- 键输入 -->
        <div class="col-key">
          <el-select
            v-if="presetOptions && presetOptions.length > 0"
            v-model="item.key"
            filterable
            allow-create
            placeholder="选择或输入"
            size="small"
            @change="(val: string) => handlePresetKey(index, val)"
          >
            <el-option
              v-for="opt in presetOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-input
            v-else
            v-model="item.key"
            :placeholder="placeholder.key || '键'"
            size="small"
          />
          <el-tooltip v-if="duplicateKeys.includes(item.key) && !allowDuplicateKeys" content="存在重复键" placement="top">
            <svg class="error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 8v4M12 16h.01"/>
            </svg>
          </el-tooltip>
        </div>

        <!-- 值输入 -->
        <div class="col-value">
          <el-select
            v-if="valueTemplates && valueTemplates.length > 0"
            v-model="item.value"
            filterable
            allow-create
            placeholder="选择或输入"
            size="small"
            @change="(val: string) => handleValueTemplate(index, val)"
          >
            <el-option
              v-for="opt in valueTemplates"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-input
            v-else
            v-model="item.value"
            :placeholder="placeholder.value || '值'"
            size="small"
          />
        </div>

        <!-- 启用开关 -->
        <div v-if="showEnable" class="col-enabled">
          <el-switch v-model="item.enabled" size="small" />
        </div>

        <!-- 操作按钮 -->
        <div class="col-actions">
          <el-tooltip content="复制" placement="top">
            <el-button link size="small" @click="copyItem(index)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="9" y="9" width="13" height="13" rx="2"/>
                <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
              </svg>
            </el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link size="small" type="danger" @click="removeItem(index)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
              </svg>
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </div>

    <!-- 统计 -->
    <div v-if="items.length > 0" class="kv-footer">
      共 {{ items.length }} 项，已启用 {{ items.filter(i => i.enabled).length }} 项
    </div>
  </div>
</template>

<style scoped>
.kv-editor {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.kv-toolbar {
  display: flex;
  gap: 8px;
  padding: 12px;
  background: var(--color-bg-light);
  border-bottom: 1px solid var(--color-border);
}

.btn-icon {
  width: 14px;
  height: 14px;
  margin-right: 4px;
}

.kv-header {
  display: grid;
  grid-template-columns: 1fr 1fr auto auto;
  gap: 12px;
  padding: 8px 12px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.kv-list {
  max-height: 300px;
  overflow-y: auto;
}

.kv-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.kv-empty svg {
  width: 32px;
  height: 32px;
  margin-bottom: 8px;
  opacity: 0.5;
}

.kv-item {
  display: grid;
  grid-template-columns: 1fr 1fr auto auto;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-border);
  align-items: center;
  transition: background 0.2s;
}

.kv-item:last-child {
  border-bottom: none;
}

.kv-item:hover {
  background: var(--color-surface);
}

.kv-item.disabled {
  opacity: 0.5;
}

.kv-item.has-error {
  background: rgba(239, 68, 68, 0.05);
}

.col-key,
.col-value {
  position: relative;
}

.col-key :deep(.el-input),
.col-value :deep(.el-input),
.col-key :deep(.el-select),
.col-value :deep(.el-select) {
  width: 100%;
}

.col-enabled {
  width: 50px;
  text-align: center;
}

.col-actions {
  width: 60px;
  display: flex;
  gap: 4px;
  justify-content: flex-end;
}

.col-actions :deep(.el-button) {
  padding: 4px;
}

.col-actions svg {
  width: 16px;
  height: 16px;
}

.error-icon {
  position: absolute;
  right: -20px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  color: var(--color-danger);
}

.kv-footer {
  padding: 8px 12px;
  background: var(--color-bg-light);
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-tertiary);
}
</style>

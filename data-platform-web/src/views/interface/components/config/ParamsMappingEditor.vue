<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElButton, ElInput, ElSelect, ElOption, ElTabs, ElTabPane, ElMessage } from 'element-plus'
import type { MappingItem } from '@/types'

interface Props {
  requestMapping: MappingItem[]
  responseMapping: MappingItem[]
}

const props = withDefaults(defineProps<Props>(), {
  requestMapping: () => [],
  responseMapping: () => []
})

const emit = defineEmits<{
  'update:requestMapping': [value: MappingItem[]]
  'update:responseMapping': [value: MappingItem[]]
}>()

const activeTab = ref('request')

// 请求参数映射
const requestItems = ref<MappingItem[]>([])

// 响应字段映射
const responseItems = ref<MappingItem[]>([])

// 同步外部值
watch(() => props.requestMapping, (val) => {
  requestItems.value = val ? [...val] : []
}, { immediate: true, deep: true })

watch(() => props.responseMapping, (val) => {
  responseItems.value = val ? [...val] : []
}, { immediate: true, deep: true })

// 监听内部变化并同步
watch(requestItems, (val) => {
  emit('update:requestMapping', val)
}, { deep: true })

watch(responseItems, (val) => {
  emit('update:responseMapping', val)
}, { deep: true })

// 转换类型选项
const transformOptions = [
  { label: '无转换', value: 'none' },
  { label: 'JSONPath', value: 'jsonPath' },
  { label: '表达式', value: 'expression' }
]

// 添加请求映射项
const addRequestMappingItem = () => {
  requestItems.value.push({
    sourceField: '',
    targetField: '',
    transformType: 'none'
  })
}

// 添加响应映射项
const addResponseMappingItem = () => {
  responseItems.value.push({
    sourceField: '',
    targetField: '',
    transformType: 'none'
  })
}

// 删除请求映射项
const removeRequestMappingItem = (index: number) => {
  requestItems.value.splice(index, 1)
}

// 删除响应映射项
const removeResponseMappingItem = (index: number) => {
  responseItems.value.splice(index, 1)
}

// 导出 JSON
const exportMapping = () => {
  const mapping = {
    request: requestItems.value.reduce((acc, item) => {
      if (item.sourceField && item.targetField) {
        acc[item.sourceField] = item.targetField
      }
      return acc
    }, {} as Record<string, string>),
    response: responseItems.value.reduce((acc, item) => {
      if (item.sourceField && item.targetField) {
        acc[item.sourceField] = item.targetField
      }
      return acc
    }, {} as Record<string, string>)
  }

  const blob = new Blob([JSON.stringify(mapping, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'params-mapping.json'
  a.click()
  URL.revokeObjectURL(url)
}

// 从 JSON 导入
const importMapping = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const json = JSON.parse(text)
      if (json.request) {
        requestItems.value = Object.entries(json.request).map(([source, target]) => ({
          sourceField: source,
          targetField: String(target),
          transformType: 'none' as const
        }))
      }
      if (json.response) {
        responseItems.value = Object.entries(json.response).map(([source, target]) => ({
          sourceField: source,
          targetField: String(target),
          transformType: 'none' as const
        }))
      }
      ElMessage.success('导入成功')
    } catch {
      ElMessage.error('JSON 格式错误')
    }
  }
  input.click()
}

// 获取 JSON 格式输出
const getMappingJson = () => {
  return {
    request: requestItems.value.reduce((acc, item) => {
      if (item.sourceField && item.targetField) {
        acc[item.sourceField] = item.targetField
      }
      return acc
    }, {} as Record<string, string>),
    response: responseItems.value.reduce((acc, item) => {
      if (item.sourceField && item.targetField) {
        acc[item.sourceField] = item.targetField
      }
      return acc
    }, {} as Record<string, string>)
  }
}

defineExpose({
  getMappingJson,
  exportMapping,
  importMapping
})
</script>

<template>
  <div class="mapping-editor">
    <div class="editor-header">
      <h4>参数映射配置</h4>
      <p>配置内部字段与厂商字段的映射关系</p>
    </div>

    <!-- 工具栏 -->
    <div class="mapping-toolbar">
      <el-button size="small" @click="importMapping">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12"/>
        </svg>
        导入
      </el-button>
      <el-button size="small" @click="exportMapping">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3"/>
        </svg>
        导出
      </el-button>
    </div>

    <el-tabs v-model="activeTab" class="mapping-tabs">
      <!-- 请求参数映射 -->
      <el-tab-pane label="请求参数映射" name="request">
        <div class="mapping-section">
          <div class="section-desc">
            <span class="arrow">内部字段</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="arrow-icon">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
            <span class="arrow">厂商字段</span>
          </div>

          <div class="mapping-table">
            <div class="mapping-row header">
              <span class="col-source">内部字段名</span>
              <span class="col-target">厂商字段名</span>
              <span class="col-actions">操作</span>
            </div>

            <div v-if="requestItems.length === 0" class="mapping-empty">
              <span>暂无映射配置</span>
            </div>

            <div v-for="(item, index) in requestItems" :key="index" class="mapping-row">
              <div class="col-source">
                <el-input v-model="item.sourceField" placeholder="如: companyName" size="small" />
              </div>
              <div class="col-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </div>
              <div class="col-target">
                <el-input v-model="item.targetField" placeholder="如: entName" size="small" />
              </div>
              <div class="col-actions">
                <el-button link size="small" type="danger" @click="removeRequestMappingItem(index)">
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <el-button size="small" @click="addRequestMappingItem" class="add-btn">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
              <path d="M12 5v14M5 12h14"/>
            </svg>
            添加映射
          </el-button>
        </div>
      </el-tab-pane>

      <!-- 响应字段映射 -->
      <el-tab-pane label="响应字段映射" name="response">
        <div class="mapping-section">
          <div class="section-desc">
            <span class="arrow">厂商字段</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="arrow-icon">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
            <span class="arrow">内部字段</span>
          </div>

          <div class="mapping-table">
            <div class="mapping-row header">
              <span class="col-source">厂商字段名</span>
              <span class="col-target">内部字段名</span>
              <span class="col-transform">转换类型</span>
              <span class="col-actions">操作</span>
            </div>

            <div v-if="responseItems.length === 0" class="mapping-empty">
              <span>暂无映射配置</span>
            </div>

            <div v-for="(item, index) in responseItems" :key="index" class="mapping-row">
              <div class="col-source">
                <el-input v-model="item.sourceField" placeholder="如: data.result" size="small" />
              </div>
              <div class="col-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </div>
              <div class="col-target">
                <el-input v-model="item.targetField" placeholder="如: result" size="small" />
              </div>
              <div class="col-transform">
                <el-select v-model="item.transformType" size="small" style="width: 100%">
                  <el-option
                    v-for="opt in transformOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </div>
              <div class="col-actions">
                <el-button link size="small" type="danger" @click="removeResponseMappingItem(index)">
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <el-button size="small" @click="addResponseMappingItem" class="add-btn">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="btn-icon">
              <path d="M12 5v14M5 12h14"/>
            </svg>
            添加映射
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 提示 -->
    <div class="mapping-hints">
      <div class="hint-item">
        <strong>请求参数映射：</strong>将内部系统使用的字段名转换为厂商要求的字段名
      </div>
      <div class="hint-item">
        <strong>响应字段映射：</strong>将厂商返回的字段名转换为内部系统统一的字段名，支持 JSONPath 表达式（如 data.result）
      </div>
    </div>
  </div>
</template>

<style scoped>
.mapping-editor {
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

.mapping-toolbar {
  display: flex;
  gap: 8px;
}

.btn-icon {
  width: 14px;
  height: 14px;
  margin-right: 4px;
}

.mapping-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.mapping-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-desc {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 8px 12px;
  background: var(--color-bg-light);
  border-radius: 6px;
}

.arrow {
  font-weight: 500;
}

.arrow-icon {
  width: 16px;
  height: 16px;
  color: var(--color-primary);
}

.mapping-table {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.mapping-row {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto;
  gap: 8px;
  padding: 8px 12px;
  align-items: center;
  border-bottom: 1px solid var(--color-border);
}

.mapping-row:last-child {
  border-bottom: none;
}

.mapping-row.header {
  background: var(--color-bg-light);
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.mapping-row.header .col-actions {
  width: 60px;
}

.mapping-empty {
  padding: 24px;
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.col-source,
.col-target {
  flex: 1;
}

.col-transform {
  width: 120px;
}

.col-arrow {
  width: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.col-arrow svg {
  width: 16px;
  height: 16px;
  color: var(--color-text-tertiary);
}

.col-actions {
  width: 60px;
  text-align: right;
}

.add-btn {
  align-self: flex-start;
}

.mapping-hints {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--color-bg-light);
  border-radius: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.hint-item strong {
  color: var(--color-text-primary);
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElInput, ElSelect, ElOption, ElTabs, ElTabPane, ElSwitch, ElMessage } from 'element-plus'
import type { RequestMappingItem, ResponseMappingItem } from '@/types'

interface Props {
  requestMapping: RequestMappingItem[]
  responseMapping: ResponseMappingItem[]
}

const props = withDefaults(defineProps<Props>(), {
  requestMapping: () => [],
  responseMapping: () => []
})

const emit = defineEmits<{
  'update:requestMapping': [value: RequestMappingItem[]]
  'update:responseMapping': [value: ResponseMappingItem[]]
}>()

const activeTab = ref('request')

const requestItems = computed(() => props.requestMapping || [])
const responseItems = computed(() => props.responseMapping || [])

const requestTransformOptions = [
  { label: '无转换', value: 'none' },
  { label: '转大写', value: 'uppercase' },
  { label: '转小写', value: 'lowercase' },
  { label: '去空格', value: 'trim' }
]

const responseTransformOptions = [
  { label: '无转换', value: 'none' },
  { label: '转字符串', value: 'toString' },
  { label: '转数字', value: 'toNumber' }
]

const sourceTypeOptions = [
  { label: '普通字段', value: 'field' },
  { label: 'JSONPath', value: 'jsonPath' }
]

function updateRequestMapping(newItems: RequestMappingItem[]) {
  emit('update:requestMapping', newItems)
}

function updateResponseMapping(newItems: ResponseMappingItem[]) {
  emit('update:responseMapping', newItems)
}

function addRequestMappingItem() {
  const newItems = [...requestItems.value, {
    targetField: '',
    sourceVar: '',
    required: true,
    transformType: 'none' as const
  }]
  updateRequestMapping(newItems)
}

function addResponseMappingItem() {
  const newItems = [...responseItems.value, {
    targetField: '',
    sourcePath: '',
    sourceType: 'field' as const,
    transformType: 'none' as const
  }]
  updateResponseMapping(newItems)
}

function updateRequestItemField(index: number, field: keyof RequestMappingItem, value: any) {
  const newItems = [...requestItems.value]
  newItems[index] = { ...newItems[index], [field]: value }
  updateRequestMapping(newItems)
}

function updateResponseItemField(index: number, field: keyof ResponseMappingItem, value: any) {
  const newItems = [...responseItems.value]
  newItems[index] = { ...newItems[index], [field]: value }
  updateResponseMapping(newItems)
}

function removeRequestMappingItem(index: number) {
  const newItems = [...requestItems.value]
  newItems.splice(index, 1)
  updateRequestMapping(newItems)
}

function removeResponseMappingItem(index: number) {
  const newItems = [...responseItems.value]
  newItems.splice(index, 1)
  updateResponseMapping(newItems)
}

function exportMapping() {
  const mapping = {
    requestMapping: requestItems.value,
    responseMapping: responseItems.value
  }
  const blob = new Blob([JSON.stringify(mapping, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'params-mapping.json'
  a.click()
  URL.revokeObjectURL(url)
}

function importMapping() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const json = JSON.parse(text)
      if (json.requestMapping) {
        updateRequestMapping(json.requestMapping)
      }
      if (json.responseMapping) {
        updateResponseMapping(json.responseMapping)
      }
      ElMessage.success('导入成功')
    } catch {
      ElMessage.error('JSON 格式错误')
    }
  }
  input.click()
}
</script>

<template>
  <div class="mapping-editor">
    <div class="editor-header">
      <h4>参数映射配置</h4>
      <p>配置内部字段与厂商字段的映射关系</p>
    </div>

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
            <span class="arrow">内部变量</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="arrow-icon">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
            <span class="arrow">厂商字段</span>
          </div>

          <div class="mapping-table">
            <div class="mapping-row header request-header">
              <span class="col-source">内部变量名</span>
              <span class="col-arrow"></span>
              <span class="col-target">厂商字段名</span>
              <span class="col-default">默认值</span>
              <span class="col-required">必填</span>
              <span class="col-transform">转换</span>
              <span class="col-actions">操作</span>
            </div>

            <div v-if="requestItems.length === 0" class="mapping-empty">
              <span>暂无映射配置</span>
            </div>

            <div v-for="(item, index) in requestItems" :key="index" class="mapping-row">
              <div class="col-source">
                <el-input
                  :model-value="item.sourceVar"
                  placeholder="如: entName"
                  size="small"
                  @update:model-value="(val: string) => updateRequestItemField(index, 'sourceVar', val)"
                />
              </div>
              <div class="col-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </div>
              <div class="col-target">
                <el-input
                  :model-value="item.targetField"
                  placeholder="如: keyword"
                  size="small"
                  @update:model-value="(val: string) => updateRequestItemField(index, 'targetField', val)"
                />
              </div>
              <div class="col-default">
                <el-input
                  :model-value="item.defaultValue"
                  placeholder="可选"
                  size="small"
                  @update:model-value="(val: string) => updateRequestItemField(index, 'defaultValue', val)"
                />
              </div>
              <div class="col-required">
                <el-switch
                  :model-value="item.required !== false"
                  size="small"
                  @update:model-value="(val: string | number | boolean) => updateRequestItemField(index, 'required', !!val)"
                />
              </div>
              <div class="col-transform">
                <el-select
                  :model-value="item.transformType || 'none'"
                  size="small"
                  style="width: 100%"
                  @update:model-value="(val: string) => updateRequestItemField(index, 'transformType', val)"
                >
                  <el-option
                    v-for="opt in requestTransformOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
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
            <div class="mapping-row header response-header">
              <span class="col-source">来源路径</span>
              <span class="col-arrow"></span>
              <span class="col-target">目标字段名</span>
              <span class="col-type">类型</span>
              <span class="col-default">默认值</span>
              <span class="col-transform">转换</span>
              <span class="col-actions">操作</span>
            </div>

            <div v-if="responseItems.length === 0" class="mapping-empty">
              <span>暂无映射配置</span>
            </div>

            <div v-for="(item, index) in responseItems" :key="index" class="mapping-row">
              <div class="col-source">
                <el-input
                  :model-value="item.sourcePath"
                  :placeholder="item.sourceType === 'jsonPath' ? '如: $.data.name' : '如: ent_name'"
                  size="small"
                  @update:model-value="(val: string) => updateResponseItemField(index, 'sourcePath', val)"
                />
              </div>
              <div class="col-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </div>
              <div class="col-target">
                <el-input
                  :model-value="item.targetField"
                  placeholder="如: companyName"
                  size="small"
                  @update:model-value="(val: string) => updateResponseItemField(index, 'targetField', val)"
                />
              </div>
              <div class="col-type">
                <el-select
                  :model-value="item.sourceType || 'field'"
                  size="small"
                  style="width: 100%"
                  @update:model-value="(val: string) => updateResponseItemField(index, 'sourceType', val)"
                >
                  <el-option
                    v-for="opt in sourceTypeOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </div>
              <div class="col-default">
                <el-input
                  :model-value="item.defaultValue"
                  placeholder="可选"
                  size="small"
                  @update:model-value="(val: any) => updateResponseItemField(index, 'defaultValue', val)"
                />
              </div>
              <div class="col-transform">
                <el-select
                  :model-value="item.transformType || 'none'"
                  size="small"
                  style="width: 100%"
                  @update:model-value="(val: string) => updateResponseItemField(index, 'transformType', val)"
                >
                  <el-option
                    v-for="opt in responseTransformOptions"
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

    <div class="mapping-hints">
      <div class="hint-item">
        <strong>请求参数映射：</strong>将内部系统使用的变量名转换为厂商要求的字段名，支持默认值和值转换
      </div>
      <div class="hint-item">
        <strong>响应字段映射：</strong>将厂商返回的字段名转换为内部系统统一的字段名。普通字段支持点号分隔路径（如 data.name），JSONPath 支持 $.data.list[0].name 格式
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

.request-header,
.mapping-row:not(.header):has(.col-required) {
  grid-template-columns: 1fr 24px 1fr 80px 50px 100px 60px;
}

.response-header,
.mapping-row:not(.header):has(.col-type) {
  grid-template-columns: 1fr 24px 1fr 100px 80px 100px 60px;
}

.mapping-empty {
  padding: 24px;
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: 13px;
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

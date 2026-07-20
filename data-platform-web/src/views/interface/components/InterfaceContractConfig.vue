<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getInterfaceContract,
  importInterfaceSchema,
  saveInterfaceContract
} from '@/api/interface'
import type { ApiInterface, InterfaceContract, InterfaceParam } from '@/types'

interface Props {
  modelValue: boolean
  interfaceData?: ApiInterface | null
}

interface LocalField extends InterfaceParam {
  _key: string
  children: LocalField[]
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'success'])

const loading = ref(false)
const saving = ref(false)
const activeDirection = ref<'request' | 'response'>('request')
const contract = ref<InterfaceContract | null>(null)
const requestFields = ref<LocalField[]>([])
const responseFields = ref<LocalField[]>([])
const draggingKey = ref<string | null>(null)

const activeFields = computed(() => activeDirection.value === 'request' ? requestFields.value : responseFields.value)
const activeTitle = computed(() => activeDirection.value === 'request' ? '请求参数 params' : '响应数据 data')

watch(() => props.modelValue, async visible => {
  if (!visible || !props.interfaceData?.id) return
  activeDirection.value = 'request'
  await loadContract()
})

const createKey = () => `${Date.now()}_${Math.random().toString(36).slice(2)}`

const toLocal = (fields: InterfaceParam[] = []): LocalField[] => fields.map(field => ({
  ...field,
  arrayItemType: field.paramType === 'array'
    ? (field.arrayItemType || ((field.children?.length || 0) > 0 ? 'object' : undefined))
    : undefined,
  _key: createKey(),
  children: toLocal(field.children || [])
}))

const loadContract = async () => {
  loading.value = true
  try {
    const data = await getInterfaceContract(props.interfaceData!.id)
    contract.value = data
    requestFields.value = toLocal(data.requestFields)
    responseFields.value = toLocal(data.responseFields)
  } catch (error) {
    console.error('加载接口契约失败:', error)
    ElMessage.error('加载接口契约失败')
  } finally {
    loading.value = false
  }
}

const emptyField = (): LocalField => ({
  _key: createKey(),
  paramName: '',
  paramType: 'string',
  arrayItemType: undefined,
  required: false,
  description: '',
  defaultValue: '',
  exampleValue: '',
  constraintConfig: '',
  sort: 0,
  children: []
})

const addRoot = () => {
  activeFields.value.push(emptyField())
  syncSort(activeFields.value)
}

const addChild = (row: LocalField) => {
  if (!['object', 'array'].includes(String(row.paramType))) {
    row.paramType = 'object'
  }
  if (row.paramType === 'array') row.arrayItemType = 'object'
  row.children.push(emptyField())
  syncSort(row.children)
}

const cloneField = (field: LocalField): LocalField => ({
  ...field,
  id: undefined,
  parentId: undefined,
  paramName: `${field.paramName}_copy`,
  _key: createKey(),
  children: field.children.map(cloneField)
})

const findContainer = (fields: LocalField[], key: string): { list: LocalField[]; index: number } | null => {
  const index = fields.findIndex(item => item._key === key)
  if (index >= 0) return { list: fields, index }
  for (const field of fields) {
    const found = findContainer(field.children, key)
    if (found) return found
  }
  return null
}

const copyField = (row: LocalField) => {
  const found = findContainer(activeFields.value, row._key)
  if (!found) return
  found.list.splice(found.index + 1, 0, cloneField(row))
  syncSort(found.list)
}

const deleteField = (row: LocalField) => {
  const found = findContainer(activeFields.value, row._key)
  if (!found) return
  found.list.splice(found.index, 1)
  syncSort(found.list)
}

const moveField = (row: LocalField, offset: number) => {
  const found = findContainer(activeFields.value, row._key)
  if (!found) return
  const target = found.index + offset
  if (target < 0 || target >= found.list.length) return
  const [item] = found.list.splice(found.index, 1)
  found.list.splice(target, 0, item)
  syncSort(found.list)
}

const startDrag = (event: DragEvent, row: LocalField) => {
  draggingKey.value = row._key
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

const dropField = (event: DragEvent, targetRow: LocalField) => {
  event.preventDefault()
  if (!draggingKey.value || draggingKey.value === targetRow._key) return
  const source = findContainer(activeFields.value, draggingKey.value)
  const target = findContainer(activeFields.value, targetRow._key)
  if (!source || !target || source.list !== target.list) {
    ElMessage.warning('仅支持在同一层级内拖拽排序')
    return
  }
  const [item] = source.list.splice(source.index, 1)
  const targetIndex = source.index < target.index ? target.index - 1 : target.index
  source.list.splice(targetIndex, 0, item)
  syncSort(source.list)
  draggingKey.value = null
}

const syncSort = (fields: LocalField[]) => {
  fields.forEach((field, index) => {
    field.sort = index
    syncSort(field.children)
  })
}

const validateFields = (fields: LocalField[], path: string, errors: string[]) => {
  const names = new Set<string>()
  fields.forEach((field, index) => {
    const current = `${path}[${index + 1}]`
    if (!field.paramName.trim()) errors.push(`${current} 参数名不能为空`)
    else if (!/^[A-Za-z_][A-Za-z0-9_-]{0,63}$/.test(field.paramName.trim())) {
      errors.push(`${current} 参数名格式无效，应以字母或下划线开头且最长64位`)
    }
    if (names.has(field.paramName.trim())) errors.push(`${path} 参数名重复: ${field.paramName}`)
    names.add(field.paramName.trim())
    if (field.constraintConfig) {
      try {
        const parsed = JSON.parse(field.constraintConfig)
        if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
          errors.push(`${current} 约束必须是JSON对象`)
        } else {
          validateConstraints(parsed, String(field.paramType), current, errors)
        }
      } catch {
        errors.push(`${current} 约束JSON格式错误`)
      }
    }
    if (field.children.length && !['object', 'array'].includes(String(field.paramType))) {
      errors.push(`${current} 只有object或array可包含子字段`)
    }
    if (field.paramType === 'array') {
      const itemType = field.children.length ? 'object' : field.arrayItemType
      if (itemType && !['string', 'integer', 'number', 'boolean', 'object'].includes(itemType)) {
        errors.push(`${current} 数组元素类型不受支持`)
      }
      if (field.children.length && itemType !== 'object') {
        errors.push(`${current} 包含子字段时数组元素类型必须为object`)
      }
    }
    validateConfiguredValue(field.defaultValue, String(field.paramType || 'string'), field, `${current} 默认值`, errors)
    validateConfiguredValue(field.exampleValue, String(field.paramType || 'string'), field, `${current} 示例值`, errors)
    validateFields(field.children, `${current}.${field.paramName || 'children'}`, errors)
  })
}

const parseConfiguredValue = (raw: string | undefined, type: string): { valid: boolean; value?: any } => {
  if (!raw) return { valid: true }
  if (type === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return { valid: true, value: typeof parsed === 'string' ? parsed : raw }
    } catch {
      return { valid: true, value: raw }
    }
  }
  try {
    const value = JSON.parse(raw)
    const valid = type === 'integer' ? Number.isInteger(value)
      : type === 'number' ? typeof value === 'number'
        : type === 'boolean' ? typeof value === 'boolean'
          : type === 'object' ? Boolean(value) && !Array.isArray(value) && typeof value === 'object'
            : type === 'array' ? Array.isArray(value) : false
    return { valid, value }
  } catch {
    return { valid: false }
  }
}

const validateConfiguredValue = (raw: string | undefined, type: string, field: LocalField,
  path: string, errors: string[]) => {
  if (!raw) return
  const parsed = parseConfiguredValue(raw, type)
  if (!parsed.valid) {
    errors.push(`${path} 类型必须为 ${type}`)
    return
  }
  let constraints: Record<string, any> = {}
  try { constraints = field.constraintConfig ? JSON.parse(field.constraintConfig) : {} } catch { return }
  const value = parsed.value
  if (Array.isArray(constraints.enum)
    && !constraints.enum.some((allowed: any) => JSON.stringify(allowed) === JSON.stringify(value))) {
    errors.push(`${path} 不在 enum 允许值范围内`)
  }
  if (typeof value === 'string') {
    if (Number.isInteger(constraints.minLength) && value.length < constraints.minLength) errors.push(`${path} 长度不能小于 ${constraints.minLength}`)
    if (Number.isInteger(constraints.maxLength) && value.length > constraints.maxLength) errors.push(`${path} 长度不能大于 ${constraints.maxLength}`)
    if (typeof constraints.pattern === 'string') {
      try {
        if (!new RegExp(constraints.pattern).test(value)) errors.push(`${path} 不符合 pattern 约束`)
      } catch {
        // 正则语法错误由约束配置校验统一展示。
      }
    }
  }
  if (typeof value === 'number') {
    if (typeof constraints.minimum === 'number' && value < constraints.minimum) errors.push(`${path} 不能小于 ${constraints.minimum}`)
    if (typeof constraints.maximum === 'number' && value > constraints.maximum) errors.push(`${path} 不能大于 ${constraints.maximum}`)
  }
  if (Array.isArray(value)) {
    if (Number.isInteger(constraints.minItems) && value.length < constraints.minItems) errors.push(`${path} 长度不能小于 ${constraints.minItems}`)
    if (Number.isInteger(constraints.maxItems) && value.length > constraints.maxItems) errors.push(`${path} 长度不能大于 ${constraints.maxItems}`)
    const itemType = field.children.length ? 'object' : field.arrayItemType
    value.forEach((item, index) => {
      if (!itemType) return
      const matches = itemType === 'string' ? typeof item === 'string'
        : itemType === 'integer' ? Number.isInteger(item)
          : itemType === 'number' ? typeof item === 'number'
            : itemType === 'boolean' ? typeof item === 'boolean'
              : Boolean(item) && !Array.isArray(item) && typeof item === 'object'
      if (!matches) errors.push(`${path}[${index}] 类型必须为 ${itemType}`)
    })
  }
}

const validateConstraints = (constraints: Record<string, any>, type: string, path: string, errors: string[]) => {
  const applicable: Record<string, string[]> = {
    string: ['enum', 'pattern', 'minLength', 'maxLength', 'format'],
    integer: ['enum', 'minimum', 'maximum'],
    number: ['enum', 'minimum', 'maximum'],
    boolean: ['enum'],
    object: ['enum'],
    array: ['enum', 'minItems', 'maxItems']
  }
  const supportedFormats = ['date', 'date-time', 'email', 'uri', 'uuid', 'ipv4', 'ipv6']
  Object.entries(constraints).forEach(([key, value]) => {
    if (!applicable[type]?.includes(key)) errors.push(`${path} 约束 ${key} 不适用于 ${type}`)
    if (key === 'enum' && (!Array.isArray(value) || !value.length)) errors.push(`${path} enum 必须是非空数组`)
    if (['minimum', 'maximum'].includes(key) && typeof value !== 'number') errors.push(`${path} ${key} 必须是数字`)
    if (['minLength', 'maxLength', 'minItems', 'maxItems'].includes(key)
      && (!Number.isInteger(value) || value < 0)) errors.push(`${path} ${key} 必须是非负整数`)
    if (key === 'pattern') {
      if (typeof value !== 'string') errors.push(`${path} pattern 必须是字符串`)
      else try { new RegExp(value) } catch { errors.push(`${path} pattern 不是有效正则表达式`) }
    }
    if (key === 'format' && !supportedFormats.includes(value)) errors.push(`${path} format 不受支持`)
  })
  if (typeof constraints.minimum === 'number' && typeof constraints.maximum === 'number'
    && constraints.minimum > constraints.maximum) errors.push(`${path} minimum 不能大于 maximum`)
  if (Number.isInteger(constraints.minLength) && Number.isInteger(constraints.maxLength)
    && constraints.minLength > constraints.maxLength) errors.push(`${path} minLength 不能大于 maxLength`)
  if (Number.isInteger(constraints.minItems) && Number.isInteger(constraints.maxItems)
    && constraints.minItems > constraints.maxItems) errors.push(`${path} minItems 不能大于 maxItems`)
}

const validationErrors = computed(() => {
  const errors: string[] = []
  validateFields(requestFields.value, '请求参数', errors)
  validateFields(responseFields.value, '响应参数', errors)
  return errors
})

const sanitize = (fields: LocalField[], direction: 'REQUEST' | 'RESPONSE'): InterfaceParam[] => fields.map((field, index) => ({
  id: field.id,
  direction,
  paramName: field.paramName.trim(),
  paramType: field.paramType || 'string',
  arrayItemType: field.paramType === 'array'
    ? (field.children.length ? 'object' : field.arrayItemType)
    : undefined,
  required: Boolean(field.required),
  description: field.description?.trim() || undefined,
  defaultValue: field.defaultValue || undefined,
  exampleValue: field.exampleValue || undefined,
  validationRule: field.validationRule || undefined,
  constraintConfig: field.constraintConfig || undefined,
  sort: index,
  children: sanitize(field.children, direction)
}))

const save = async () => {
  if (validationErrors.value.length) {
    ElMessage.error(validationErrors.value[0])
    return
  }
  saving.value = true
  try {
    const data = await saveInterfaceContract(props.interfaceData!.id, {
      requestFields: sanitize(requestFields.value, 'REQUEST'),
      responseFields: sanitize(responseFields.value, 'RESPONSE')
    })
    contract.value = data
    requestFields.value = toLocal(data.requestFields)
    responseFields.value = toLocal(data.responseFields)
    ElMessage.success('接口契约已保存，文档已同步更新')
    emit('success')
  } catch (error) {
    console.error('保存接口契约失败:', error)
  } finally {
    saving.value = false
  }
}

const importSchema = async () => {
  try {
    await ElMessageBox.confirm('导入会使用现有请求/响应 Schema 覆盖结构化字段，是否继续？', '导入现有 Schema', {
      type: 'warning'
    })
    const data = await importInterfaceSchema(props.interfaceData!.id)
    contract.value = data
    requestFields.value = toLocal(data.requestFields)
    responseFields.value = toLocal(data.responseFields)
    ElMessage.success('Schema 已转换为结构化契约')
  } catch (error) {
    if (error !== 'cancel') console.error('导入Schema失败:', error)
  }
}

const exampleForFields = (fields: LocalField[]): Record<string, any> => {
  const result: Record<string, any> = {}
  fields.forEach(field => {
    if (field.exampleValue) {
      try { result[field.paramName] = JSON.parse(field.exampleValue) } catch { result[field.paramName] = field.exampleValue }
      return
    }
    if (field.defaultValue) {
      try { result[field.paramName] = JSON.parse(field.defaultValue) } catch { result[field.paramName] = field.defaultValue }
      return
    }
    switch (field.paramType) {
      case 'integer': result[field.paramName] = 1; break
      case 'number': result[field.paramName] = 1.0; break
      case 'boolean': result[field.paramName] = true; break
      case 'array': result[field.paramName] = field.children.length ? [exampleForFields(field.children)] : []; break
      case 'object': result[field.paramName] = exampleForFields(field.children); break
      default: result[field.paramName] = `<${field.paramName}>`
    }
  })
  return result
}

const requestPreview = computed(() => JSON.stringify(exampleForFields(requestFields.value), null, 2))
const responsePreview = computed(() => JSON.stringify(exampleForFields(responseFields.value), null, 2))

const close = () => emit('update:modelValue', false)
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="`内部调用契约 · ${interfaceData?.interfaceName || ''}`"
    size="1180px"
    @close="close"
  >
    <div v-loading="loading" class="contract-editor">
      <el-alert type="info" :closable="false" show-icon>
        配置内部系统调用平台时 params 中的请求字段，以及成功响应 data 中的字段。保存后会同步生成 JSON Schema 和 OpenAPI 文档。
      </el-alert>

      <el-alert v-if="validationErrors.length" type="error" :closable="false" show-icon>
        <template #title>保存前校验发现 {{ validationErrors.length }} 个问题</template>
        <ul class="validation-errors">
          <li v-for="error in validationErrors.slice(0, 10)" :key="error">{{ error }}</li>
        </ul>
      </el-alert>

      <div class="toolbar">
        <el-radio-group v-model="activeDirection">
          <el-radio-button value="request">请求参数</el-radio-button>
          <el-radio-button value="response">响应参数</el-radio-button>
        </el-radio-group>
        <div>
          <el-button @click="importSchema">导入现有 Schema</el-button>
          <el-button type="primary" @click="addRoot">新增根字段</el-button>
        </div>
      </div>

      <h4>{{ activeTitle }}</h4>
      <el-table
        :data="activeFields"
        row-key="_key"
        default-expand-all
        :tree-props="{ children: 'children' }"
        border
        empty-text="暂未配置字段"
      >
        <el-table-column label="排序" width="58" align="center">
          <template #default="{ row }">
            <span
              class="drag-handle"
              draggable="true"
              title="拖拽排序"
              @dragstart="startDrag($event, row)"
              @dragover.prevent
              @drop="dropField($event, row)"
            >⋮⋮</span>
          </template>
        </el-table-column>
        <el-table-column label="字段名" min-width="170">
          <template #default="{ row }">
            <el-input v-model="row.paramName" placeholder="fieldName" />
          </template>
        </el-table-column>
        <el-table-column label="类型" width="150">
          <template #default="{ row }">
            <el-select v-model="row.paramType">
              <el-option v-for="type in ['string','integer','number','boolean','object','array']" :key="type" :label="type" :value="type" />
            </el-select>
            <el-select v-if="row.paramType === 'array'" v-model="row.arrayItemType" size="small" class="item-type-select" clearable placeholder="元素: 任意">
              <el-option v-for="type in ['string','integer','number','boolean','object']" :key="type" :label="`元素: ${type}`" :value="type" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="必填" width="70" align="center">
          <template #default="{ row }"><el-switch v-model="row.required" /></template>
        </el-table-column>
        <el-table-column label="说明" min-width="180">
          <template #default="{ row }"><el-input v-model="row.description" placeholder="字段含义" /></template>
        </el-table-column>
        <el-table-column label="默认值" width="130">
          <template #default="{ row }"><el-input v-model="row.defaultValue" placeholder="JSON或文本" /></template>
        </el-table-column>
        <el-table-column label="示例值" width="130">
          <template #default="{ row }"><el-input v-model="row.exampleValue" placeholder="JSON或文本" /></template>
        </el-table-column>
        <el-table-column label="约束" width="100">
          <template #default="{ row }">
            <el-popover placement="left" :width="360" trigger="click">
              <template #reference><el-button link type="primary">编辑约束</el-button></template>
              <p class="constraint-tip">支持 enum、pattern、minimum、maximum、minLength、maxLength、minItems、maxItems、format。</p>
              <el-input v-model="row.constraintConfig" type="textarea" :rows="6" placeholder='{"minLength": 1, "maxLength": 64}' />
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="addChild(row)">子字段</el-button>
            <el-button link @click="copyField(row)">复制</el-button>
            <el-button link @click="moveField(row, -1)">上移</el-button>
            <el-button link @click="moveField(row, 1)">下移</el-button>
            <el-button link type="danger" @click="deleteField(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="preview-grid">
        <div class="preview-block">
          <h4>请求参数 params 示例</h4>
          <pre>{{ requestPreview }}</pre>
        </div>
        <div class="preview-block">
          <h4>响应数据 data 示例</h4>
          <pre>{{ responsePreview }}</pre>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="close">关闭</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存契约</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.contract-editor { display: flex; flex-direction: column; gap: 18px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; }
h4 { margin: 0; color: var(--color-text-primary); }
.constraint-tip { margin: 0 0 10px; color: var(--color-text-secondary); font-size: 12px; }
.drag-handle { cursor: grab; user-select: none; color: var(--color-text-secondary); font-weight: 700; }
.item-type-select { margin-top: 6px; }
.validation-errors { margin: 8px 0 0; padding-left: 20px; }
.preview-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.preview-block pre {
  margin: 10px 0 0;
  padding: 16px;
  max-height: 260px;
  overflow: auto;
  border-radius: 8px;
  background: var(--color-bg-secondary);
  color: var(--color-text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
@media (max-width: 900px) {
  .preview-grid { grid-template-columns: 1fr; }
}
</style>

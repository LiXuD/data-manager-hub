<script setup lang="ts">
import { computed, ref } from 'vue'
import type { JsonSchemaNode } from '@/types'
import {
  orderedSchemaProperties,
  readSecretReference,
  schemaFieldVisible,
  schemaFieldRequired,
  schemaDefault,
  secretFieldRepresentation,
  writeSecretReference
} from '@/utils/connector'

defineOptions({ name: 'JsonSchemaField' })

const props = withDefaults(defineProps<{
  modelValue?: unknown
  schema: JsonSchemaNode
  fieldName?: string
  label?: string
  required?: boolean
  secretOptions?: string[]
  disabled?: boolean
}>(), {
  label: '',
  fieldName: '',
  required: false,
  secretOptions: () => [],
  disabled: false
})

const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()
const properties = computed(() => orderedSchemaProperties(props.schema))
const showAdvanced = ref(false)
const managedFields = computed(() => new Set(props.schema['x-platform-managed'] || []))
const visibleProperties = computed(() => properties.value.filter(([key, child]) =>
  !managedFields.value.has(key) && schemaFieldVisible(child, props.modelValue, key, props.schema)
))
const advancedProperties = computed(() => visibleProperties.value.filter(([, child]) => child['x-ui-advanced']))
const displayedGroups = computed(() => {
  const fields = visibleProperties.value.filter(([, child]) => showAdvanced.value || !child['x-ui-advanced'])
  const groups = new Map<string, Array<[string, JsonSchemaNode]>>()
  for (const field of fields) {
    const group = field[1]['x-ui-group'] || ''
    groups.set(group, [...(groups.get(group) || []), field])
  }
  return [...groups.entries()].map(([name, entries]) => ({ name, entries }))
})
const requiredKeys = computed(() => new Set(props.schema.required || []))
const secretRepresentation = computed(() => secretFieldRepresentation(props.schema, props.fieldName))
const secretSelector = computed(() => Boolean(secretRepresentation.value))
const selectedSecretRef = computed(() => readSecretReference(props.modelValue))

function updateSecretRef(value: unknown) {
  if (secretRepresentation.value) {
    emit('update:modelValue', writeSecretReference(secretRepresentation.value, value))
  }
}

function updateObject(key: string, value: unknown) {
  const current = props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue)
    ? props.modelValue as Record<string, unknown>
    : {}
  emit('update:modelValue', { ...current, [key]: value })
}

function itemValue(index: number) {
  return Array.isArray(props.modelValue) ? props.modelValue[index] : undefined
}

function updateItem(index: number, value: unknown) {
  const next = Array.isArray(props.modelValue) ? [...props.modelValue] : []
  next[index] = value
  emit('update:modelValue', next)
}

function addItem() {
  const next = Array.isArray(props.modelValue) ? [...props.modelValue] : []
  next.push(schemaDefault(props.schema.items || { type: 'string' }))
  emit('update:modelValue', next)
}

function removeItem(index: number) {
  const next = Array.isArray(props.modelValue) ? [...props.modelValue] : []
  next.splice(index, 1)
  emit('update:modelValue', next)
}

function primitiveValue(value: unknown): string | number | boolean | undefined {
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value
  return undefined
}

function enumValue(value: unknown): string | number | boolean {
  return primitiveValue(value) ?? String(value)
}

function groupLabel(value: string) {
  return ({ request: '请求配置', authentication: '认证配置', response: '响应处理' } as Record<string, string>)[value] || value
}
</script>

<template>
  <el-form-item v-if="secretSelector" :label="label" :required="required" class="schema-field">
    <el-select
      :model-value="selectedSecretRef"
      filterable
      clearable
      :disabled="disabled"
      placeholder="选择密钥引用（不保存明文）"
      style="width: 100%"
      @update:model-value="updateSecretRef"
    >
      <el-option v-for="item in secretOptions" :key="item" :label="item" :value="item" />
    </el-select>
    <div v-if="schema['x-help-text'] || schema.description" class="field-help">{{ schema['x-help-text'] || schema.description }}</div>
  </el-form-item>

  <div v-else-if="schema.type === 'object' || schema.properties" class="schema-object">
    <div v-if="label" class="schema-group-title">{{ label }}</div>
    <section v-for="group in displayedGroups" :key="group.name || 'default'" class="schema-ui-group">
      <div v-if="group.name" class="schema-ui-group-label">{{ groupLabel(group.name) }}</div>
      <JsonSchemaField
        v-for="[key, child] in group.entries"
        :key="key"
        :model-value="(modelValue as Record<string, unknown> | undefined)?.[key]"
        :schema="child"
        :field-name="key"
        :label="child.title || key"
        :required="requiredKeys.has(key) || schemaFieldRequired(key, schema, modelValue)"
        :secret-options="secretOptions"
        :disabled="disabled"
        @update:model-value="value => updateObject(key, value)"
      />
    </section>
    <el-button v-if="advancedProperties.length" link :disabled="disabled" @click="showAdvanced = !showAdvanced">
      {{ showAdvanced ? '收起高级字段' : `显示高级字段（${advancedProperties.length}）` }}
    </el-button>
  </div>

  <el-form-item v-else-if="schema.type === 'array'" :label="label" :required="required" class="schema-field schema-array">
    <div class="array-list">
      <div v-for="(_, index) in (Array.isArray(modelValue) ? modelValue : [])" :key="index" class="array-row">
        <JsonSchemaField
          :model-value="itemValue(index)"
          :schema="schema.items || { type: 'string' }"
          :field-name="fieldName"
          :label="`第 ${index + 1} 项`"
          :secret-options="secretOptions"
          :disabled="disabled"
          @update:model-value="value => updateItem(index, value)"
        />
        <el-button type="danger" link :disabled="disabled" @click="removeItem(index)">删除</el-button>
      </div>
      <el-button plain :disabled="disabled" @click="addItem">添加一项</el-button>
    </div>
    <div v-if="schema['x-help-text'] || schema.description" class="field-help">{{ schema['x-help-text'] || schema.description }}</div>
  </el-form-item>

  <el-form-item v-else :label="label" :required="required" class="schema-field">
    <el-select
      v-if="schema.enum"
      :model-value="primitiveValue(modelValue)"
      clearable
      :disabled="disabled"
      style="width: 100%"
      @update:model-value="value => emit('update:modelValue', value)"
    >
      <el-option v-for="item in schema.enum" :key="String(item)" :label="String(item)" :value="enumValue(item)" />
    </el-select>
    <el-switch
      v-else-if="schema.type === 'boolean'"
      :model-value="Boolean(modelValue)"
      :disabled="disabled"
      @update:model-value="value => emit('update:modelValue', value)"
    />
    <el-input-number
      v-else-if="schema.type === 'integer' || schema.type === 'number'"
      :model-value="typeof modelValue === 'number' ? modelValue : undefined"
      :step="schema.type === 'integer' ? 1 : 0.1"
      :precision="schema.type === 'integer' ? 0 : undefined"
      :min="schema.minimum"
      :max="schema.maximum"
      :disabled="disabled"
      style="width: 100%"
      @update:model-value="value => emit('update:modelValue', value)"
    />
    <el-input
      v-else
      :model-value="String(modelValue ?? '')"
      :type="schema['x-ui-widget'] === 'textarea' ? 'textarea' : 'text'"
      :rows="schema['x-ui-widget'] === 'textarea' ? 4 : undefined"
      :placeholder="schema['x-placeholder-source'] || schema.description"
      :maxlength="schema.maxLength"
      :minlength="schema.minLength"
      :disabled="disabled"
      @update:model-value="value => emit('update:modelValue', value)"
    />
    <div v-if="schema['x-help-text'] || schema.description" class="field-help">{{ schema['x-help-text'] || schema.description }}</div>
  </el-form-item>
</template>

<style scoped>
.schema-object { border-left: 2px solid var(--color-border); padding-left: 16px; margin-bottom: 14px; }
.schema-group-title { color: var(--color-text-primary); font-weight: 600; margin: 8px 0 14px; }
.schema-ui-group + .schema-ui-group { margin-top: 16px; }
.schema-ui-group-label { color: var(--color-text-secondary); font-size: 12px; font-weight: 600; margin: 4px 0 12px; }
.schema-field { margin-bottom: 16px; }
.field-help { width: 100%; color: var(--color-text-secondary); font-size: 12px; line-height: 1.5; margin-top: 4px; }
.array-list { width: 100%; display: grid; gap: 10px; }
.array-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: start; padding: 10px; border: 1px solid var(--color-border); border-radius: 8px; }
.array-row :deep(.el-form-item) { margin-bottom: 0; }
</style>

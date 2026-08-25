<script setup lang="ts">
import type { JsonSchemaNode } from '@/types'
import JsonSchemaField from './JsonSchemaField.vue'

withDefaults(defineProps<{
  modelValue: Record<string, unknown>
  schema: JsonSchemaNode
  secretOptions?: string[]
  disabled?: boolean
}>(), { secretOptions: () => [], disabled: false })

const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()
</script>

<template>
  <el-form label-position="top" class="json-schema-form">
    <JsonSchemaField
      :model-value="modelValue"
      :schema="schema"
      :secret-options="secretOptions"
      :disabled="disabled"
      @update:model-value="value => emit('update:modelValue', (value || {}) as Record<string, unknown>)"
    />
  </el-form>
</template>

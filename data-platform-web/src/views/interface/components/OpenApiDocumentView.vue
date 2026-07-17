<script setup lang="ts">
import { computed } from 'vue'
import type { OpenApiDocument } from '@/types'

const props = defineProps<{ document: OpenApiDocument }>()
defineEmits<{ download: [format: 'json' | 'yaml'] }>()

const commonRequestFields = computed(() => {
  const schema = props.document.openapi?.components?.schemas?.QueryRequest || {}
  const required = new Set<string>(schema.required || [])
  return Object.entries<Record<string, any>>(schema.properties || {})
    .filter(([name]) => name !== 'params')
    .map(([name, field]) => ({
      name,
      type: Array.isArray(field.type) ? field.type.join(' | ') : (field.type || '-'),
      required: required.has(name),
      fixedValue: field.const ?? field.default ?? '-',
      description: field.description || (field.const !== undefined ? '固定接口编码' : '')
    }))
})
</script>

<template>
  <div class="doc-view">
    <section class="hero">
      <div>
        <div class="eyebrow">{{ document.contract.interfaceCode }}</div>
        <h1>{{ document.contract.interfaceName }}</h1>
        <p>{{ document.contract.description || '暂无接口说明' }}</p>
      </div>
      <div class="download-actions">
        <el-button @click="$emit('download', 'json')">下载 JSON</el-button>
        <el-button type="primary" @click="$emit('download', 'yaml')">下载 YAML</el-button>
      </div>
    </section>

    <el-card>
      <template #header><strong>调用信息</strong></template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="服务地址">{{ document.baseUrl }}</el-descriptions-item>
        <el-descriptions-item label="认证方式">X-Api-Key 或 Authorization: Bearer &lt;API_KEY&gt;</el-descriptions-item>
        <el-descriptions-item label="单笔接口">POST /openapi/v1/query</el-descriptions-item>
        <el-descriptions-item label="批量接口">POST /openapi/v1/batch-query</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <template #header><strong>平台公共请求参数</strong></template>
      <el-table :data="commonRequestFields">
        <el-table-column prop="name" label="字段" min-width="150" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column label="必填" width="80"><template #default="{ row }">{{ row.required ? '是' : '否' }}</template></el-table-column>
        <el-table-column prop="fixedValue" label="固定值/默认值" min-width="150" />
        <el-table-column prop="description" label="说明" min-width="180" />
      </el-table>
    </el-card>

    <el-card>
      <template #header><strong>请求参数 params</strong></template>
      <el-table :data="document.contract.requestFields" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column prop="paramName" label="字段" min-width="180" />
        <el-table-column prop="paramType" label="类型" width="110" />
        <el-table-column label="必填" width="80"><template #default="{ row }">{{ row.required ? '是' : '否' }}</template></el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column prop="defaultValue" label="默认值" width="130" />
        <el-table-column prop="exampleValue" label="示例" width="130" />
      </el-table>
    </el-card>

    <el-card>
      <template #header><strong>响应参数 data</strong></template>
      <el-table :data="document.contract.responseFields" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column prop="paramName" label="字段" min-width="180" />
        <el-table-column prop="paramType" label="类型" width="110" />
        <el-table-column label="必含" width="80"><template #default="{ row }">{{ row.required ? '是' : '否' }}</template></el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column prop="exampleValue" label="示例" width="150" />
      </el-table>
    </el-card>

    <div class="two-columns">
      <el-card>
        <template #header><strong>Curl 示例</strong></template>
        <pre>{{ document.curl }}</pre>
      </el-card>
      <el-card>
        <template #header><strong>错误码</strong></template>
        <el-table :data="document.errorCodes">
          <el-table-column prop="code" label="状态码" width="90" />
          <el-table-column prop="description" label="说明" />
        </el-table>
      </el-card>
    </div>

    <el-card>
      <template #header><strong>OpenAPI 3.1 预览</strong></template>
      <pre class="openapi-preview">{{ JSON.stringify(document.openapi, null, 2) }}</pre>
    </el-card>
  </div>
</template>

<style scoped>
.doc-view { display: flex; flex-direction: column; gap: 20px; color: var(--color-text-primary); }
.doc-view :deep(.el-card),
.doc-view :deep(.el-card__header) { color: var(--color-text-primary); }
.hero { display: flex; justify-content: space-between; gap: 24px; align-items: flex-start; }
.hero h1 { margin: 4px 0 8px; font-size: 28px; }
.hero p { margin: 0; color: var(--color-text-secondary); }
.eyebrow { color: var(--color-primary); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.download-actions { display: flex; white-space: nowrap; }
.two-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
pre { margin: 0; padding: 16px; overflow: auto; border-radius: 8px; background: var(--color-bg-secondary); color: var(--color-text-primary); white-space: pre-wrap; }
.openapi-preview { max-height: 520px; white-space: pre; }
@media (max-width: 900px) {
  .hero { flex-direction: column; }
  .two-columns { grid-template-columns: 1fr; }
}
</style>

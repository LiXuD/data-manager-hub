<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { downloadManagedOpenApi, getManagedOpenApiDocument } from '@/api/openapi-docs'
import type { OpenApiDocument } from '@/types'
import OpenApiDocumentView from './components/OpenApiDocumentView.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const document = ref<OpenApiDocument | null>(null)
const interfaceId = Number(route.params.id)

const load = async () => {
  if (!Number.isFinite(interfaceId)) {
    ElMessage.error('接口ID无效')
    return
  }
  loading.value = true
  try {
    document.value = await getManagedOpenApiDocument(interfaceId)
  } catch (error) {
    console.error('加载接口文档失败:', error)
  } finally {
    loading.value = false
  }
}

const download = async (format: 'json' | 'yaml') => {
  const blob = await downloadManagedOpenApi(interfaceId, format)
  const url = URL.createObjectURL(blob)
  const anchor = window.document.createElement('a')
  anchor.href = url
  anchor.download = `${document.value?.contract.interfaceCode || 'openapi'}.${format === 'yaml' ? 'yaml' : 'json'}`
  anchor.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="back-row"><el-button link @click="router.push('/interface')">← 返回接口管理</el-button></div>
    <OpenApiDocumentView v-if="document" :document="document" @download="download" />
    <el-empty v-else-if="!loading" description="接口文档不存在" />
  </div>
</template>

<style scoped>
.page-container { max-width: 1400px; margin: 0 auto; min-height: 400px; }
.back-row { margin-bottom: 14px; }
</style>

<script setup lang="ts">
import axios from 'axios'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { OpenApiDocument } from '@/types'
import OpenApiDocumentView from '@/views/interface/components/OpenApiDocumentView.vue'

interface InterfaceItem {
  id: number
  interfaceCode: string
  interfaceName: string
  description?: string
}

const apiKey = ref('')
const loading = ref(false)
const interfaces = ref<InterfaceItem[]>([])
const document = ref<OpenApiDocument | null>(null)

const headers = () => ({ 'X-Api-Key': apiKey.value.trim() })

const loadInterfaces = async () => {
  if (!apiKey.value.trim()) {
    ElMessage.warning('请输入 API Key')
    return
  }
  loading.value = true
  document.value = null
  try {
    const response = await axios.get('/openapi/v1/docs/interfaces', { headers: headers() })
    if (response.data?.code !== 200) throw new Error(response.data?.msg || '加载失败')
    interfaces.value = response.data.data || []
  } catch (error: any) {
    interfaces.value = []
    ElMessage.error(error.response?.data?.msg || error.message || 'API Key 验证失败')
  } finally {
    loading.value = false
  }
}

const openDocument = async (item: InterfaceItem) => {
  loading.value = true
  try {
    const response = await axios.get(`/openapi/v1/docs/interfaces/${encodeURIComponent(item.interfaceCode)}`, {
      headers: headers()
    })
    if (response.data?.code !== 200) throw new Error(response.data?.msg || '加载失败')
    document.value = response.data.data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.msg || error.message || '加载接口文档失败')
  } finally {
    loading.value = false
  }
}

const download = async (format: 'json' | 'yaml') => {
  if (!document.value) return
  const apiCode = document.value.contract.interfaceCode
  const response = await axios.get(`/openapi/v1/docs/interfaces/${encodeURIComponent(apiCode)}/openapi`, {
    headers: headers(),
    params: { format },
    responseType: 'blob'
  })
  const url = URL.createObjectURL(response.data)
  const anchor = window.document.createElement('a')
  anchor.href = url
  anchor.download = `${apiCode}.${format === 'yaml' ? 'yaml' : 'json'}`
  anchor.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <main class="portal" v-loading="loading">
    <header class="portal-header">
      <div>
        <div class="brand">DATA MANAGER HUB</div>
        <h1>内部接口文档</h1>
        <p>使用调用方 API Key 查看已授权接口。凭证仅保存在当前页面内存中。</p>
      </div>
      <div class="key-form">
        <el-input v-model="apiKey" type="password" show-password placeholder="输入 X-Api-Key" @keyup.enter="loadInterfaces" />
        <el-button type="primary" @click="loadInterfaces">验证并加载</el-button>
      </div>
    </header>

    <div class="content-grid">
      <aside class="catalog">
        <h3>已授权接口</h3>
        <button v-for="item in interfaces" :key="item.id" class="catalog-item" type="button" @click="openDocument(item)">
          <strong>{{ item.interfaceName }}</strong>
          <code>{{ item.interfaceCode }}</code>
          <span>{{ item.description || '暂无说明' }}</span>
        </button>
        <el-empty v-if="!interfaces.length" description="验证 API Key 后显示接口" :image-size="80" />
      </aside>
      <section class="document-panel">
        <OpenApiDocumentView v-if="document" :document="document" @download="download" />
        <el-empty v-else description="请选择一个接口查看文档" />
      </section>
    </div>
  </main>
</template>

<style scoped>
.portal { min-height: 100vh; padding: 36px; background: var(--color-bg-primary); color: var(--color-text-primary); }
.portal-header { max-width: 1500px; margin: 0 auto 28px; display: flex; justify-content: space-between; gap: 32px; align-items: flex-end; }
.brand { color: var(--color-primary); letter-spacing: .14em; font-size: 12px; font-weight: 700; }
.portal-header h1 { margin: 8px 0; font-size: 34px; }
.portal-header p { margin: 0; color: var(--color-text-secondary); }
.key-form { width: min(520px, 100%); display: flex; gap: 10px; }
.content-grid { max-width: 1500px; margin: 0 auto; display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 24px; }
.catalog { padding: 18px; align-self: start; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-bg-secondary); }
.catalog h3 { margin: 0 0 14px; }
.catalog-item { width: 100%; padding: 13px; margin-bottom: 10px; text-align: left; border: 1px solid var(--color-border); border-radius: 9px; background: var(--color-bg-primary); color: inherit; cursor: pointer; }
.catalog-item:hover { border-color: var(--color-primary); }
.catalog-item strong, .catalog-item code, .catalog-item span { display: block; }
.catalog-item code { margin: 5px 0; color: var(--color-primary); }
.catalog-item span { color: var(--color-text-secondary); font-size: 12px; }
.document-panel { min-width: 0; }
@media (max-width: 900px) {
  .portal { padding: 20px; }
  .portal-header { flex-direction: column; align-items: stretch; }
  .content-grid { grid-template-columns: 1fr; }
}
</style>

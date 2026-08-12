<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getConnectorMigrations } from '@/api/connector-migration'
import type { VendorConnectorMigration } from '@/types'

const loading = ref(false)
const rows = ref<VendorConnectorMigration[]>([])
const state = ref('')

function stateType(value: string) {
  if (value === 'STABLE' || value === 'READY' || value === 'TEST_PASSED') return 'success'
  if (value === 'FAILED' || value === 'BLOCKED') return 'danger'
  if (value === 'OBSERVING') return 'warning'
  if (value === 'ROLLED_BACK') return 'info'
  return 'primary'
}

async function load() {
  loading.value = true
  try {
    rows.value = (await getConnectorMigrations(state.value || undefined)).data || []
  } catch (error) {
    console.warn('加载厂商连接器迁移历史失败', error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container migration-page">
    <div class="page-header migration-hero">
      <div>
        <div class="eyebrow">STAGE 5 · READ ONLY</div>
        <h1>厂商连接器迁移历史</h1>
        <p>旧运行时已经退役。本页面仅保留历史迁移事实，用于审计和解释既有调用。</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="state" clearable placeholder="全部状态" @change="load">
          <el-option v-for="item in ['PREPARED','VALIDATED','TEST_PASSED','OBSERVING','READY','STABLE','FAILED','BLOCKED','ROLLED_BACK']" :key="item" :label="item" :value="item" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="迁移控制面已冻结"
      description="系统只接受 PLUGIN 运行模式；新增、校验、测试、发布、完成和回滚等旧迁移动作均已下线。连接器版本管理请在厂商接口配置中完成。"
    />

    <el-table v-loading="loading" :data="rows" class="migration-table" empty-text="没有迁移历史">
      <el-table-column prop="vendorConfigId" label="配置" width="90" />
      <el-table-column prop="vendorId" label="厂商" width="90" />
      <el-table-column prop="interfaceId" label="接口" width="90" />
      <el-table-column label="状态" width="125">
        <template #default="{ row }"><el-tag :type="stateType(row.state)">{{ row.state }}</el-tag><div class="muted">任务 V{{ row.recordVersion }}</div></template>
      </el-table-column>
      <el-table-column label="草稿 / 发布" min-width="145">
        <template #default="{ row }"><div>草稿 V{{ row.draftVersion ?? '—' }}</div><div>流水线 V{{ row.publishedVersionNo ?? '—' }}</div></template>
      </el-table-column>
      <el-table-column label="观察调用" min-width="160">
        <template #default="{ row }"><div>{{ row.observedCalls }} / {{ row.minimumCalls }}</div><div class="muted">成功 {{ row.observedSuccesses }} · 失败 {{ row.observedFailures }}</div></template>
      </el-table-column>
      <el-table-column label="质量门禁" min-width="185">
        <template #default="{ row }"><div>错误率 {{ (row.observedErrorRate * 100).toFixed(2) }}%</div><div>P95 {{ row.observedP95DurationMs }} ms</div><div>计费覆盖 {{ (row.observedBillingCoverageRate * 100).toFixed(1) }}%</div></template>
      </el-table-column>
      <el-table-column label="缓存 / 计费" min-width="170">
        <template #default="{ row }"><div>命中 {{ row.observedCacheHits }} · 实时 {{ row.observedRealtimeCalls }}</div><div>计费事实 {{ row.observedPostedBillingEvents }}/{{ row.observedBillingEvents }}</div><div>金额 {{ row.observedBillingAmount }}</div></template>
      </el-table-column>
      <el-table-column label="安全错误" min-width="180">
        <template #default="{ row }"><div>{{ row.safeErrorCode || '—' }}</div><div class="digest">{{ row.safeErrorDigest }}</div></template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="最后更新" min-width="170" />
    </el-table>
  </div>
</template>

<style scoped>
.migration-page { padding:24px; display:grid; gap:18px; }
.migration-hero { display:flex; justify-content:space-between; align-items:flex-end; padding:24px 28px; border:1px solid var(--color-border); border-radius:16px; background:linear-gradient(125deg,rgba(0,184,148,.13),transparent 55%),var(--color-bg-card); }
.migration-hero h1 { margin:4px 0 6px; }
.migration-hero p { margin:0; color:var(--color-text-secondary); }
.eyebrow { color:#00b894; font:600 11px var(--font-mono); letter-spacing:.16em; }
.hero-actions { display:flex; gap:8px; }
.hero-actions .el-select { width:155px; }
.migration-table { border:1px solid var(--color-border); border-radius:12px; }
.muted { color:var(--color-text-secondary); font-size:11px; }
.digest { max-width:160px; overflow:hidden; text-overflow:ellipsis; color:var(--color-text-secondary); font:10px var(--font-mono); }
@media(max-width:900px){.migration-hero{align-items:flex-start;flex-direction:column;gap:14px}.hero-actions{flex-wrap:wrap}}
</style>

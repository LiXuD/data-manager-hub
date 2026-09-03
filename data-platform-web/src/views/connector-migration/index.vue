<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  completeConnectorMigration,
  getConnectorLegacyInventory,
  getConnectorMigrations,
  getInvalidPreparedMigrations,
  observeConnectorMigration,
  prepareConnectorMigration,
  repairInvalidPreparedMigrations,
  rollbackConnectorMigration,
  startConnectorMigrationObservation,
  type ConnectorLegacyInventory,
  type ConnectorLegacyInventoryEntry
} from '@/api/connector-migration'
import { useUserStore } from '@/stores/user'
import type { VendorConnectorMigration } from '@/types'

const loading = ref(false)
const rows = ref<VendorConnectorMigration[]>([])
const inventory = ref<ConnectorLegacyInventory | null>(null)
const invalidPrepared = ref<Awaited<ReturnType<typeof getInvalidPreparedMigrations>>['data']>([])
const state = ref('')
const busyAction = ref<string | null>(null)
const userStore = useUserStore()
const canMigrate = computed(() => userStore.hasPermission('connector-plugin:migrate'))

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
    const [migrationResponse, inventoryResponse, auditResponse] = await Promise.all([
      getConnectorMigrations(state.value || undefined),
      getConnectorLegacyInventory(),
      getInvalidPreparedMigrations()
    ])
    rows.value = migrationResponse.data || []
    inventory.value = inventoryResponse.data || null
    invalidPrepared.value = auditResponse.data || []
  } catch {
    console.warn('加载厂商连接器迁移历史失败')
  } finally {
    loading.value = false
  }
}

async function repairInvalidPrepared() {
  if (!canMigrate.value || invalidPrepared.value.length === 0) return
  await ElMessageBox.confirm(
    `将把 ${invalidPrepared.value.length} 条不可安全推进的 PREPARED 记录以 CAS 方式标记为 BLOCKED，保留历史与错误摘要。是否继续？`,
    '修复无效迁移准备记录',
    { type: 'warning' }
  )
  await repairInvalidPreparedMigrations()
  await load()
}

function isBusy(action: string, vendorConfigId: number) {
  return busyAction.value === `${action}:${vendorConfigId}`
}

function canPrepare(entry: ConnectorLegacyInventoryEntry) {
  return entry.active?.classification === 'LOSSLESS_CONVERTIBLE'
}

function prepareReason(entry: ConnectorLegacyInventoryEntry) {
  return entry.active?.reasons.map(reason => reason.safeMessage).filter(Boolean).join('；')
    || '当前 Legacy 配置不满足安全迁移资格'
}

async function runAction(
  action: 'prepare' | 'start' | 'observe' | 'complete' | 'rollback',
  row: VendorConnectorMigration | ConnectorLegacyInventoryEntry
) {
  if (!canMigrate.value) return
  if (action === 'prepare' && 'active' in row
      && row.active?.classification !== 'LOSSLESS_CONVERTIBLE') return
  const vendorConfigId = row.vendorConfigId
  busyAction.value = `${action}:${vendorConfigId}`
  try {
    if (action === 'prepare') await prepareConnectorMigration(vendorConfigId)
    if (action === 'start' && 'recordVersion' in row) {
      await startConnectorMigrationObservation(vendorConfigId, { expectedRecordVersion: row.recordVersion })
    }
    if (action === 'observe' && 'recordVersion' in row) {
      await observeConnectorMigration(vendorConfigId, { expectedRecordVersion: row.recordVersion })
    }
    if (action === 'complete' && 'recordVersion' in row) {
      await completeConnectorMigration(vendorConfigId, { expectedRecordVersion: row.recordVersion })
    }
    if (action === 'rollback' && 'recordVersion' in row) {
      await rollbackConnectorMigration(vendorConfigId, { expectedRecordVersion: row.recordVersion })
    }
    await load()
  } catch {
    console.warn('执行厂商连接器迁移动作失败')
  } finally {
    busyAction.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container migration-page">
    <div class="page-header migration-hero">
      <div>
        <div class="eyebrow">STAGE 5 · CONTROLLED ROLLOUT</div>
        <h1>厂商连接器迁移历史</h1>
        <p>先清点 Legacy 厂商，再绑定 SIMPLE 版本并观察真实请求、错误、缓存和计费事实。</p>
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
      title="阶段 5 受控迁移"
      description="准备和观察动作只保存版本哈希与聚合事实；开始观察前必须确认两个 Access 实例均已 READY，异常时通过不可变版本回滚。"
    />

    <section v-if="invalidPrepared.length" class="section-card repair-card">
      <div class="section-heading">
        <div>
          <div class="section-kicker">DRY-RUN REPAIR REPORT</div>
          <h2>发现不可推进的 PREPARED 记录</h2>
        </div>
        <el-button v-if="canMigrate" type="warning" @click="repairInvalidPrepared">安全标记 BLOCKED</el-button>
      </div>
      <el-table :data="invalidPrepared" size="small">
        <el-table-column prop="migrationId" label="迁移记录" width="100" />
        <el-table-column prop="vendorConfigId" label="配置" width="90" />
        <el-table-column prop="classification" label="分类" width="240" />
        <el-table-column label="安全原因">
          <template #default="{ row }">{{ row.reasonCodes.join('、') }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="section-card">
      <div class="section-heading">
        <div>
          <div class="section-kicker">PRODUCTION INVENTORY</div>
          <h2>Legacy 厂商清点</h2>
        </div>
        <span v-if="inventory" class="muted">共 {{ inventory.total }} 个配置</span>
      </div>
      <el-table v-loading="loading" :data="inventory?.items || []" empty-text="没有待清点的 Legacy 配置">
        <el-table-column prop="vendorConfigId" label="配置" width="90" />
        <el-table-column prop="vendorCode" label="厂商" width="150" />
        <el-table-column prop="dataTypeCode" label="数据类型" min-width="160" />
        <el-table-column label="活动版本分类" min-width="220">
          <template #default="{ row }">
            <el-tag v-if="row.active" :type="row.active.classification === 'LOSSLESS_CONVERTIBLE' ? 'success' : 'warning'">
              {{ row.active.classification }}
            </el-tag>
            <span v-else class="muted">无活动 Legacy 版本</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              v-if="canMigrate && row.active && canPrepare(row)"
              type="primary"
              link
              :loading="isBusy('prepare', row.vendorConfigId)"
              @click="runAction('prepare', row)"
            >准备迁移</el-button>
            <span v-else-if="canMigrate && row.active" class="muted">
              不可准备：{{ prepareReason(row) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </section>

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
      <el-table-column label="推进" min-width="250" fixed="right">
        <template #default="{ row }">
          <template v-if="canMigrate">
            <el-button
              v-if="['PREPARED','VALIDATED','TEST_PASSED'].includes(row.state)"
              type="primary"
              link
              :loading="isBusy('start', row.vendorConfigId)"
              @click="runAction('start', row)"
            >开始观察</el-button>
            <el-button
              v-if="row.state === 'OBSERVING'"
              type="primary"
              link
              :loading="isBusy('observe', row.vendorConfigId)"
              @click="runAction('observe', row)"
            >刷新观察</el-button>
            <el-button
              v-if="row.state === 'READY'"
              type="success"
              link
              :loading="isBusy('complete', row.vendorConfigId)"
              @click="runAction('complete', row)"
            >完成迁移</el-button>
            <el-button
              v-if="['OBSERVING','READY','FAILED','STABLE'].includes(row.state)"
              type="warning"
              link
              :loading="isBusy('rollback', row.vendorConfigId)"
              @click="runAction('rollback', row)"
            >回滚</el-button>
          </template>
          <span v-else class="muted">无迁移权限</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.migration-page { padding:24px; display:grid; gap:18px; }
.section-card { padding:20px; border:1px solid var(--color-border); border-radius:12px; background:var(--color-bg-card); }
.section-heading { display:flex; justify-content:space-between; align-items:flex-end; margin-bottom:14px; }
.section-heading h2 { margin:3px 0 0; font-size:18px; }
.section-kicker { color:var(--color-text-secondary); font:600 10px var(--font-mono); letter-spacing:.14em; }
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

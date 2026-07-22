<template>
  <div class="billing-workspace">
    <div class="workspace-toolbar">
      <div>
        <h3>版本化计费方案</h3>
        <p>模板负责计算方式，方案绑定厂商、接口、响应字段和生效版本。</p>
      </div>
      <div class="toolbar-actions">
        <el-button v-if="canManage" :loading="reviewing" @click="handleReviewContracts">检查契约变更</el-button>
        <el-button v-if="canManage" :loading="accruing" @click="handleAccrue">补提周期费用</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增计费方案</el-button>
      </div>
    </div>

    <el-table :data="plans" v-loading="loading" stripe>
      <el-table-column label="方案 / 版本" min-width="210">
        <template #default="{ row }">
          <div class="primary-cell">{{ row.planName }}</div>
          <div class="secondary-cell">{{ row.planCode }} · v{{ row.version }}</div>
        </template>
      </el-table-column>
      <el-table-column label="厂商 / 接口" min-width="220">
        <template #default="{ row }">
          <div>{{ row.vendorName }}</div>
          <div class="secondary-cell">{{ row.interfaceName }} · {{ row.interfaceCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="模板" width="145">
        <template #default="{ row }">{{ templateName(row.templateCode) }}</template>
      </el-table-column>
      <el-table-column label="计费方向" width="130">
        <template #default="{ row }">{{ row.accountingPurpose === 'INTERNAL_CHARGEBACK' ? '内部核算' : '厂商应付' }}</template>
      </el-table-column>
      <el-table-column label="生效区间" min-width="190">
        <template #default="{ row }">
          <div>{{ formatTime(row.effectiveFrom) }}</div>
          <div class="secondary-cell">至 {{ row.effectiveTo ? formatTime(row.effectiveTo) : '长期' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canManage && editable(row)" type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canManage && (editable(row) || row.status === 'NEEDS_REVIEW')" type="success" link @click="handlePublish(row)">{{ row.status === 'NEEDS_REVIEW' ? '复核发布' : '发布' }}</el-button>
          <el-button v-if="canManage && !editable(row)" type="primary" link @click="handleNextVersion(row)">新版本</el-button>
          <el-button link @click="openSimulation(row)">模拟</el-button>
          <el-button v-if="canManage && editable(row)" type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="ledger-header">
      <div>
        <h3>计费事件账本</h3>
        <p>每次计量、周期费用与冲正均保留独立事件和定价快照。</p>
      </div>
      <div class="ledger-filters">
        <el-select v-model="eventPurpose" style="width: 150px" @change="fetchEvents">
          <el-option label="厂商应付" value="VENDOR_PAYABLE" />
          <el-option label="内部核算" value="INTERNAL_CHARGEBACK" />
        </el-select>
        <el-select v-model="eventStatus" placeholder="全部状态" clearable style="width: 150px" @change="fetchEvents">
          <el-option label="已入账" value="POSTED" />
          <el-option label="待复核" value="PENDING_REVIEW" />
        </el-select>
      </div>
    </div>
    <el-table :data="events" v-loading="eventLoading" stripe>
      <el-table-column prop="requestId" label="请求号" min-width="190" show-overflow-tooltip />
      <el-table-column label="事件" width="105">
        <template #default="{ row }">{{ eventTypeName(row.eventType) }}</template>
      </el-table-column>
      <el-table-column label="方案" min-width="160">
        <template #default="{ row }">{{ row.planCode }} · v{{ row.planVersion }}</template>
      </el-table-column>
      <el-table-column prop="interfaceCode" label="接口" min-width="150" />
      <el-table-column label="数量" width="120">
        <template #default="{ row }">{{ Number(row.quantity || 0).toLocaleString() }} {{ row.unit }}</template>
      </el-table-column>
      <el-table-column label="金额" width="125">
        <template #default="{ row }"><span class="amount-cell">{{ row.currency }} {{ Number(row.finalAmount || 0).toFixed(4) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><el-tag :type="row.status === 'PENDING_REVIEW' ? 'warning' : 'success'" size="small">{{ row.status === 'PENDING_REVIEW' ? '待复核' : '已入账' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="发生时间" width="170">
        <template #default="{ row }">{{ formatTime(row.callTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canReverse && row.eventType !== 'REVERSAL'" type="danger" link @click="handleReverse(row)">冲正</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination-row">
      <el-pagination v-model:current-page="eventPage" v-model:page-size="eventPageSize" :total="eventTotal"
        layout="total, prev, pager, next" @current-change="fetchEvents" />
    </div>

    <el-dialog v-model="wizardVisible" :title="planForm.id ? '编辑计费方案' : '新增计费方案'" width="1080px" destroy-on-close>
      <el-steps :active="step" finish-status="success" align-center class="plan-steps">
        <el-step title="选择模板" />
        <el-step title="绑定对象" />
        <el-step title="计量口径" />
        <el-step title="价格策略" />
        <el-step title="生效与校验" />
      </el-steps>

      <div v-show="step === 0" class="step-pane template-grid">
        <button v-for="item in templates" :key="item.templateCode" type="button"
          class="template-option" :class="{ selected: planForm.templateCode === item.templateCode }"
          @click="selectTemplate(item.templateCode)">
          <span class="template-title">{{ item.templateName }}</span>
          <span>{{ item.description }}</span>
        </button>
      </div>

      <el-form v-show="step === 1" :model="planForm" label-width="110px" class="step-pane two-column-form">
        <el-form-item label="方案名称" required><el-input v-model="planForm.planName" /></el-form-item>
        <el-form-item label="计费方向" required>
          <el-select v-model="planForm.accountingPurpose" style="width:100%">
            <el-option label="厂商应付" value="VENDOR_PAYABLE" />
            <el-option label="内部核算" value="INTERNAL_CHARGEBACK" />
          </el-select>
        </el-form-item>
        <el-form-item label="厂商" required>
          <el-select v-model="planForm.vendorId" style="width:100%" @change="handleVendorChange">
            <el-option v-for="vendor in cacheStore.vendorOptions" :key="vendor.id" :label="vendor.vendorName" :value="Number(vendor.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口" required>
          <el-select v-model="planForm.interfaceId" :disabled="!planForm.vendorId" style="width:100%" @change="handleInterfaceChange">
            <el-option v-for="item in interfaces" :key="item.id" :label="`${item.interfaceName} (${item.interfaceCode})`" :value="Number(item.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="币种"><el-input v-model="planForm.currency" maxlength="3" /></el-form-item>
        <el-form-item label="时区"><el-input v-model="planForm.timezone" /></el-form-item>
      </el-form>

      <div v-show="step === 2" class="step-pane">
        <el-alert title="仅保存选中的最小计量事实，不会把厂商完整响应传入计费域。" type="info" :closable="false" />
        <div class="section-title-row"><h4>收费成立条件</h4><el-button type="primary" link @click="addCondition">+ 添加条件</el-button></div>
        <div v-if="!planForm.metering.conditions.length" class="empty-hint">未配置条件时，成功响应默认可计费。</div>
        <div v-for="(condition, index) in planForm.metering.conditions" :key="index" class="condition-row">
          <el-select v-model="condition.path" placeholder="选择响应字段" filterable @change="path => bindConditionField(index, path)">
            <el-option v-for="field in responseFields" :key="field.path" :label="`${field.label} · ${field.path}`" :value="field.path" />
          </el-select>
          <el-select v-model="condition.operator" placeholder="判断方式">
            <el-option v-for="operator in operators" :key="operator.value" :label="operator.label" :value="operator.value" />
          </el-select>
          <el-input v-if="needsExpected(condition.operator)" v-model="condition.expectedValue" :placeholder="condition.operator.includes('IN') ? '多个值用逗号分隔' : '期望值'" />
          <el-input v-else model-value="无需期望值" disabled />
          <el-button type="danger" link @click="removeCondition(index)">删除</el-button>
        </div>
        <div class="metering-options">
          <label>条件关系<el-radio-group v-model="planForm.metering.logic"><el-radio-button value="AND">全部满足</el-radio-button><el-radio-button value="OR">任一满足</el-radio-button></el-radio-group></label>
          <label>字段缺失<el-select v-model="planForm.metering.missingFieldPolicy"><el-option label="进入待复核" value="PENDING_REVIEW" /><el-option label="不计费" value="NOT_BILLABLE" /><el-option label="仍计费" value="BILLABLE" /><el-option label="报错" value="ERROR" /></el-select></label>
          <label>缓存命中<el-select v-model="planForm.metering.cacheBillingPolicy"><el-option label="免费" value="FREE" /><el-option label="同价" value="SAME_PRICE" /><el-option label="自定义价格" value="CUSTOM" /></el-select></label>
          <label>累计范围<el-select v-model="planForm.metering.aggregationScope"><el-option label="厂商+接口" value="VENDOR_INTERFACE" /><el-option label="租户" value="TENANT" /><el-option label="调用方" value="CALLER" /></el-select></label>
        </div>
        <h4>计费数量</h4>
        <div class="quantity-row">
          <el-select v-model="planForm.metering.quantity.type" @change="handleQuantityType">
            <el-option label="固定数量" value="FIXED" />
            <el-option label="响应数值" value="FACT" />
            <el-option label="数组长度" value="ARRAY_SIZE" />
            <el-option label="调用耗时" value="DURATION" />
          </el-select>
          <el-select v-if="['FACT','ARRAY_SIZE'].includes(planForm.metering.quantity.type)" v-model="planForm.metering.quantity.path" placeholder="选择数量字段" filterable @change="bindQuantityField">
            <el-option v-for="field in quantityFields" :key="field.path" :label="`${field.label} · ${field.path}`" :value="field.path" />
          </el-select>
          <el-input-number v-if="planForm.metering.quantity.type === 'FIXED'" v-model="planForm.metering.quantity.fixedValue" :min="0.000001" :precision="6" />
          <el-input v-model="planForm.metering.quantity.unit" placeholder="单位，如 CALL / ITEM" />
        </div>
      </div>

      <el-form v-show="step === 3" :model="planForm" label-width="130px" class="step-pane two-column-form">
        <template v-if="['PER_CALL','PER_ITEM','DURATION','TIERED'].includes(planForm.templateCode)">
          <el-form-item label="基础单价" required><el-input-number v-model="planForm.pricing.unitPrice" :min="0" :precision="8" style="width:100%" /></el-form-item>
        </template>
        <template v-if="['PACKAGE_COUNT','FLAT_PERIOD'].includes(planForm.templateCode)">
          <el-form-item label="周期固定费用" required><el-input-number v-model="planForm.pricing.packageFee" :min="0" :precision="8" style="width:100%" /></el-form-item>
        </template>
        <template v-if="planForm.templateCode === 'PACKAGE_COUNT'">
          <el-form-item label="套餐包含量" required><el-input-number v-model="planForm.pricing.includedUnits" :min="1" style="width:100%" /></el-form-item>
          <el-form-item label="超额单价" required><el-input-number v-model="planForm.pricing.overageUnitPrice" :min="0" :precision="8" style="width:100%" /></el-form-item>
        </template>
        <template v-if="planForm.templateCode === 'DURATION'">
          <el-form-item label="时长单位"><el-select v-model="planForm.pricing.durationUnit" style="width:100%"><el-option label="毫秒" value="MILLISECOND" /><el-option label="秒" value="SECOND" /><el-option label="分钟" value="MINUTE" /><el-option label="小时" value="HOUR" /></el-select></el-form-item>
          <el-form-item label="取整方式"><el-select v-model="planForm.pricing.durationRounding" style="width:100%"><el-option label="向上取整" value="CEILING" /><el-option label="向下取整" value="FLOOR" /><el-option label="四舍五入" value="HALF_UP" /></el-select></el-form-item>
        </template>
        <el-form-item v-if="planForm.metering.cacheBillingPolicy === 'CUSTOM'" label="缓存自定义单价"><el-input-number v-model="planForm.pricing.cacheUnitPrice" :min="0" :precision="8" style="width:100%" /></el-form-item>
        <div v-if="planForm.templateCode === 'TIERED'" class="tier-block">
          <div class="section-title-row"><h4>累进阶梯</h4><el-button type="primary" link @click="addTier">+ 添加阶梯</el-button></div>
          <div v-for="(tier, index) in planForm.tiers" :key="index" class="tier-row">
            <el-input-number v-model="tier.tierMin" disabled />
            <el-input-number v-if="index < planForm.tiers.length - 1" v-model="tier.tierMax" :min="tier.tierMin + 1" @change="syncTier(index)" />
            <el-input v-else model-value="无上限" disabled />
            <el-input-number v-model="tier.unitPrice" :min="0" :precision="8" placeholder="阶梯单价" />
            <el-input-number v-model="tier.discount" :min="0.01" :max="1" :step="0.05" :precision="2" />
            <el-button type="danger" link :disabled="planForm.tiers.length === 1" @click="removeTier(index)">删除</el-button>
          </div>
        </div>
      </el-form>

      <el-form v-show="step === 4" :model="planForm" label-width="140px" class="step-pane two-column-form">
        <el-form-item label="结算周期"><el-select v-model="planForm.settlementCycle" style="width:100%"><el-option label="日" value="DAY" /><el-option label="月" value="MONTH" /><el-option label="年" value="YEAR" /></el-select></el-form-item>
        <el-form-item label="生效时间" required><el-date-picker v-model="planForm.effectiveFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="失效时间"><el-date-picker v-model="planForm.effectiveTo" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" clearable style="width:100%" /></el-form-item>
        <el-form-item label="失败不收费"><el-switch v-model="planForm.adjustment.noChargeOnFailure" /></el-form-item>
        <el-form-item label="响应契约必须有效"><el-switch v-model="planForm.adjustment.requireValidContract" /></el-form-item>
        <el-form-item label="SLA价格补偿"><el-switch v-model="planForm.adjustment.slaEnabled" /></el-form-item>
        <template v-if="planForm.adjustment.slaEnabled">
          <el-form-item label="SLA阈值(ms)"><el-input-number v-model="planForm.adjustment.slaThresholdMs" :min="0" style="width:100%" /></el-form-item>
          <el-form-item label="每100ms补偿率"><el-input-number v-model="planForm.adjustment.compensationRatePer100Ms" :min="0" :max="1" :step="0.01" :precision="4" style="width:100%" /></el-form-item>
        </template>
        <div class="review-summary">
          <strong>{{ templateName(planForm.templateCode) }}</strong>
          <span>{{ selectedVendorName }} / {{ selectedInterfaceName }}</span>
          <span>{{ planForm.metering.conditions.length }} 个收费条件 · {{ quantityTypeName(planForm.metering.quantity.type) }}</span>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="wizardVisible = false">取消</el-button>
        <el-button v-if="step > 0" @click="step--">上一步</el-button>
        <el-button v-if="step < 4" type="primary" @click="nextStep">下一步</el-button>
        <el-button v-else-if="canManage" :loading="saving" @click="saveDraft(false)">保存草稿</el-button>
        <el-button v-if="canManage && step === 4" type="success" :loading="publishing" @click="saveAndPublish">校验并发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="simulationVisible" title="计费模拟器" width="720px">
      <el-form label-width="130px">
        <el-form-item label="调用成功"><el-switch v-model="simulation.charge.success" /></el-form-item>
        <el-form-item label="命中缓存"><el-switch v-model="simulation.charge.cached" /></el-form-item>
        <el-form-item label="契约有效"><el-switch v-model="simulation.charge.responseContractValid" /></el-form-item>
        <el-form-item label="调用耗时(ms)"><el-input-number v-model="simulation.charge.latencyMs" :min="0" /></el-form-item>
        <el-form-item label="账期已有用量"><el-input-number v-model="simulation.usageBefore" :min="0" /></el-form-item>
        <el-form-item label="计量事实(JSON)"><el-input v-model="simulationFacts" type="textarea" :rows="5" placeholder='例如 {"resultCount": 3}' /></el-form-item>
      </el-form>
      <el-alert v-if="simulationResult" :title="simulationResult.valid ? '模拟通过' : '模拟未通过'" :type="simulationResult.valid ? 'success' : 'error'" :closable="false">
        <template #default>
          <div class="simulation-result">计费：{{ simulationResult.billable ? '是' : '否' }}，数量 {{ simulationResult.quantity }}，最终金额 {{ simulationResult.finalAmount }}</div>
          <div v-for="item in simulationResult.decisions" :key="item">{{ item }}</div>
          <div v-for="item in simulationResult.errors" :key="item" class="error-text">{{ item }}</div>
        </template>
      </el-alert>
      <template #footer><el-button @click="simulationVisible = false">关闭</el-button><el-button type="primary" :loading="simulating" @click="runSimulation">运行模拟</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInterfaceContract, getInterfaceOptions } from '@/api/interface'
import { useCacheStore } from '@/stores/cache'
import { useUserStore } from '@/stores/user'
import { extractPageData } from '@/utils/pagination'
import type { ApiInterface, InterfaceParam } from '@/types'
import {
  accrueBillingPlans, createBillingPlan, createBillingPlanVersion, deleteBillingPlan,
  getBillingEvents, getBillingPlans, getBillingTemplates, publishBillingPlan,
  reviewBillingContracts, reverseBillingEvent, simulateBillingPlan, updateBillingPlan,
  validateBillingPlan, type BillingCondition, type BillingEvent, type BillingPlan,
  type BillingTemplate
} from '@/api/billing'

interface FieldOption { id?: number; path: string; label: string; type: string }

const cacheStore = useCacheStore()
const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('billing:manage'))
const canReverse = computed(() => userStore.hasPermission('billing:reverse'))
const loading = ref(false)
const eventLoading = ref(false)
const reviewing = ref(false)
const accruing = ref(false)
const saving = ref(false)
const publishing = ref(false)
const simulating = ref(false)
const plans = ref<BillingPlan[]>([])
const templates = ref<BillingTemplate[]>([])
const events = ref<BillingEvent[]>([])
const interfaces = ref<ApiInterface[]>([])
const responseFields = ref<FieldOption[]>([])
const eventStatus = ref('')
const eventPurpose = ref('VENDOR_PAYABLE')
const eventPage = ref(1)
const eventPageSize = ref(10)
const eventTotal = ref(0)
const wizardVisible = ref(false)
const simulationVisible = ref(false)
const step = ref(0)
const simulationPlanId = ref<number>()
const simulationFacts = ref('{}')
const simulationResult = ref<any>(null)

const localDateTime = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
}

const defaultPlan = (): BillingPlan => ({
  planName: '', templateCode: 'PER_CALL', accountingPurpose: 'VENDOR_PAYABLE', currency: 'CNY',
  timezone: 'Asia/Shanghai', settlementCycle: 'MONTH', effectiveFrom: localDateTime(),
  pricing: { unitPrice: 0, packageFee: 0, includedUnits: 0, overageUnitPrice: 0,
    tierMode: 'GRADUATED', durationUnit: 'SECOND', durationRounding: 'CEILING', carryOver: false },
  metering: { logic: 'AND', conditions: [], missingFieldPolicy: 'PENDING_REVIEW',
    cacheBillingPolicy: 'FREE', aggregationScope: 'VENDOR_INTERFACE',
    quantity: { type: 'FIXED', alias: 'quantity', source: 'NORMALIZED_RESPONSE', extraction: 'VALUE', fixedValue: 1, unit: 'CALL' } },
  adjustment: { noChargeOnFailure: true, requireValidContract: false, slaEnabled: false },
  tiers: []
})

const planForm = reactive<BillingPlan>(defaultPlan())
const simulation = reactive({ usageBefore: 0, charge: { success: true, cached: false, responseContractValid: true, latencyMs: 100 } })
const operators = [
  ['EQ', '等于'], ['NE', '不等于'], ['IN', '属于'], ['NOT_IN', '不属于'], ['GT', '大于'], ['GTE', '大于等于'],
  ['LT', '小于'], ['LTE', '小于等于'], ['EXISTS', '字段存在'], ['NOT_EXISTS', '字段不存在'], ['EMPTY', '为空'],
  ['NOT_EMPTY', '非空'], ['TRUE', '为真'], ['FALSE', '为假']
].map(([value, label]) => ({ value, label }))
const builtinTemplates: BillingTemplate[] = [
  { id: -1, templateCode: 'PER_CALL', templateVersion: 1, templateName: '按次计费', category: 'USAGE', description: '有效调用数量乘以单价', supportsQuantity: true, supportsCycle: false, status: 'ACTIVE' },
  { id: -2, templateCode: 'TIERED', templateVersion: 1, templateName: '阶梯计费', category: 'USAGE', description: '按账期累计用量执行累进阶梯', supportsQuantity: true, supportsCycle: true, status: 'ACTIVE' },
  { id: -3, templateCode: 'PACKAGE_COUNT', templateVersion: 1, templateName: '包次计费', category: 'PACKAGE', description: '周期套餐费包含固定用量，超额另计', supportsQuantity: true, supportsCycle: true, status: 'ACTIVE' },
  { id: -4, templateCode: 'FLAT_PERIOD', templateVersion: 1, templateName: '包周期计费', category: 'PACKAGE', description: '按日、月或年收取固定费用', supportsQuantity: false, supportsCycle: true, status: 'ACTIVE' },
  { id: -5, templateCode: 'PER_ITEM', templateVersion: 1, templateName: '按返回数据量', category: 'USAGE', description: '按响应数值或数组长度计费', supportsQuantity: true, supportsCycle: false, status: 'ACTIVE' },
  { id: -6, templateCode: 'DURATION', templateVersion: 1, templateName: '按时长计费', category: 'USAGE', description: '按调用耗时折算秒、分钟或小时', supportsQuantity: true, supportsCycle: false, status: 'ACTIVE' }
]

const selectedVendorName = computed(() => cacheStore.vendorOptions.find(item => Number(item.id) === planForm.vendorId)?.vendorName || '-')
const selectedInterfaceName = computed(() => interfaces.value.find(item => Number(item.id) === planForm.interfaceId)?.interfaceName || planForm.interfaceName || '-')
const quantityFields = computed(() => responseFields.value.filter(field =>
  planForm.metering.quantity.type === 'ARRAY_SIZE' ? field.type === 'array' : ['integer', 'number'].includes(field.type)))

const fetchPlans = async () => {
  loading.value = true
  try { plans.value = (await getBillingPlans()).data || [] } catch { plans.value = [] } finally { loading.value = false }
}

const fetchEvents = async () => {
  eventLoading.value = true
  try {
    const response = await getBillingEvents({ page: eventPage.value, pageSize: eventPageSize.value,
      accountingPurpose: eventPurpose.value, status: eventStatus.value || undefined })
    const page = extractPageData<BillingEvent>(response)
    events.value = page.list
    eventTotal.value = page.total
  } catch {
    events.value = []
    eventTotal.value = 0
  } finally { eventLoading.value = false }
}

const loadInterfaces = async (vendorId?: number) => {
  interfaces.value = vendorId ? ((await getInterfaceOptions({ vendorId, status: 'active' })).data || []) : []
}

const loadContract = async (interfaceId?: number) => {
  responseFields.value = []
  if (!interfaceId) return
  const contract = await getInterfaceContract(interfaceId)
  const flatten = (items: InterfaceParam[], parent = '$.data') => {
    items.forEach(item => {
      const path = `${parent}.${item.paramName}`
      responseFields.value.push({ id: item.id, path, label: item.description || item.paramName, type: item.paramType || 'string' })
      flatten(item.children || [], path)
    })
  }
  flatten(contract.responseFields || [])
}

const openCreate = () => {
  Object.assign(planForm, defaultPlan())
  interfaces.value = []
  responseFields.value = []
  step.value = 0
  wizardVisible.value = true
}

const openEdit = async (row: BillingPlan) => {
  await loadInterfaces(row.vendorId)
  await loadContract(row.interfaceId)
  const copy = JSON.parse(JSON.stringify(row)) as BillingPlan
  copy.metering.conditions = (copy.metering.conditions || []).map(condition => ({
    ...condition,
    expectedValue: Array.isArray(condition.expectedValue) ? condition.expectedValue.join(',') : condition.expectedValue
  }))
  Object.assign(planForm, copy)
  step.value = 1
  wizardVisible.value = true
}

const handleVendorChange = async () => {
  planForm.interfaceId = undefined
  responseFields.value = []
  await loadInterfaces(planForm.vendorId)
}

const handleInterfaceChange = async () => {
  planForm.metering.conditions = []
  planForm.metering.quantity.path = undefined
  await loadContract(planForm.interfaceId)
}

const selectTemplate = (code: string) => {
  planForm.templateCode = code
  if (code === 'DURATION') {
    Object.assign(planForm.metering.quantity, { type: 'DURATION', unit: 'SECOND', path: undefined })
  } else if (code === 'PER_ITEM') {
    Object.assign(planForm.metering.quantity, { type: 'FACT', unit: 'ITEM', path: undefined })
  } else {
    Object.assign(planForm.metering.quantity, { type: 'FIXED', fixedValue: 1, unit: 'CALL', path: undefined })
  }
  if (code === 'TIERED' && !planForm.tiers.length) {
    planForm.tiers = [{ tierMin: 0, tierMax: 100000, unitPrice: 0, discount: 1 }, { tierMin: 100000, unitPrice: 0, discount: 0.9 }]
  }
}

const addCondition = () => planForm.metering.conditions.push({
  alias: `condition_${planForm.metering.conditions.length + 1}`, source: 'NORMALIZED_RESPONSE', path: '', extraction: 'VALUE', operator: 'EQ', expectedValue: ''
})
const removeCondition = (index: number) => planForm.metering.conditions.splice(index, 1)
const bindConditionField = (index: number, path: string) => {
  const field = responseFields.value.find(item => item.path === path)
  Object.assign(planForm.metering.conditions[index], { fieldId: field?.id, alias: `condition_${index + 1}`, extraction: 'VALUE' })
}
const bindQuantityField = (path: string) => {
  const field = responseFields.value.find(item => item.path === path)
  Object.assign(planForm.metering.quantity, { fieldId: field?.id, alias: 'quantity', extraction: planForm.metering.quantity.type === 'ARRAY_SIZE' ? 'ARRAY_SIZE' : 'VALUE' })
}
const handleQuantityType = () => {
  planForm.metering.quantity.path = undefined
  planForm.metering.quantity.fieldId = undefined
  planForm.metering.quantity.extraction = planForm.metering.quantity.type === 'ARRAY_SIZE' ? 'ARRAY_SIZE' : 'VALUE'
  if (planForm.metering.quantity.type === 'DURATION') planForm.metering.quantity.unit = 'SECOND'
}
const needsExpected = (operator: string) => !['EXISTS', 'NOT_EXISTS', 'EMPTY', 'NOT_EMPTY', 'TRUE', 'FALSE'].includes(operator)

const addTier = () => {
  const previous = planForm.tiers[planForm.tiers.length - 1]
  if (!previous) return planForm.tiers.push({ tierMin: 0, discount: 1, unitPrice: planForm.pricing.unitPrice })
  const nextMin = previous.tierMin + 100000
  previous.tierMax = nextMin
  planForm.tiers.push({ tierMin: nextMin, discount: Math.max(0.01, previous.discount - 0.1), unitPrice: previous.unitPrice })
}
const removeTier = (index: number) => {
  planForm.tiers.splice(index, 1)
  planForm.tiers.forEach((tier, i) => { tier.tierMin = i === 0 ? 0 : Number(planForm.tiers[i - 1].tierMax); if (i === planForm.tiers.length - 1) tier.tierMax = undefined })
}
const syncTier = (index: number) => { if (planForm.tiers[index + 1] && planForm.tiers[index].tierMax != null) planForm.tiers[index + 1].tierMin = Number(planForm.tiers[index].tierMax) }

const nextStep = async () => {
  if (step.value === 0 && !planForm.templateCode) return ElMessage.warning('请选择计费模板')
  if (step.value === 1 && (!planForm.planName || !planForm.vendorId || !planForm.interfaceId)) return ElMessage.warning('请完整绑定方案、厂商和接口')
  if (step.value === 2 && ['FACT', 'ARRAY_SIZE'].includes(planForm.metering.quantity.type) && !planForm.metering.quantity.path) return ElMessage.warning('请选择计费数量字段')
  step.value++
}

const payload = (): BillingPlan => {
  const copy = JSON.parse(JSON.stringify(planForm)) as BillingPlan
  copy.currency = copy.currency.toUpperCase()
  copy.metering.conditions = copy.metering.conditions.map((condition: BillingCondition) => {
    if (!needsExpected(condition.operator)) return { ...condition, expectedValue: undefined }
    if (['IN', 'NOT_IN'].includes(condition.operator) && typeof condition.expectedValue === 'string') {
      return { ...condition, expectedValue: condition.expectedValue.split(',').map(value => value.trim()).filter(Boolean) }
    }
    return condition
  })
  if (copy.tiers.length) copy.tiers[copy.tiers.length - 1].tierMax = undefined
  return copy
}

const saveDraft = async (quiet: boolean) => {
  saving.value = true
  try {
    const response = planForm.id ? await updateBillingPlan(planForm.id, payload()) : await createBillingPlan(payload())
    Object.assign(planForm, response.data)
    if (!quiet) {
      ElMessage.success('草稿已保存')
      wizardVisible.value = false
    }
    await fetchPlans()
    return response.data
  } finally { saving.value = false }
}

const saveAndPublish = async () => {
  publishing.value = true
  try {
    const saved = await saveDraft(true)
    if (!saved?.id) return
    const validation = await validateBillingPlan(saved.id)
    if (!validation.data.valid) return ElMessage.error(validation.data.errors.join('；'))
    await publishBillingPlan(saved.id)
    ElMessage.success('方案已发布并按生效时间解析')
    wizardVisible.value = false
    await fetchPlans()
  } finally { publishing.value = false }
}

const handlePublish = async (row: BillingPlan) => {
  if (!row.id) return
  const validation = await validateBillingPlan(row.id)
  if (!validation.data.valid) return ElMessage.error(validation.data.errors.join('；'))
  await publishBillingPlan(row.id)
  ElMessage.success('发布成功')
  fetchPlans()
}

const handleNextVersion = async (row: BillingPlan) => {
  if (!row.id) return
  const response = await createBillingPlanVersion(row.id)
  ElMessage.success(`已创建 v${response.data.version} 草稿`)
  await fetchPlans()
  openEdit(response.data)
}

const handleDelete = async (row: BillingPlan) => {
  if (!row.id) return
  await ElMessageBox.confirm('只会删除当前草稿，已发布版本和历史账本不会受影响。', '删除草稿', { type: 'warning' })
  await deleteBillingPlan(row.id)
  ElMessage.success('草稿已删除')
  fetchPlans()
}

const openSimulation = (row: BillingPlan) => {
  simulationPlanId.value = row.id
  simulationFacts.value = '{}'
  simulationResult.value = null
  Object.assign(simulation, { usageBefore: 0, charge: { success: true, cached: false, responseContractValid: true, latencyMs: 100 } })
  simulationVisible.value = true
}

const runSimulation = async () => {
  if (!simulationPlanId.value) return
  let facts: Record<string, unknown>
  try { facts = JSON.parse(simulationFacts.value || '{}') } catch { return ElMessage.error('计量事实必须是合法JSON对象') }
  simulating.value = true
  try {
    const response = await simulateBillingPlan(simulationPlanId.value, { usageBefore: simulation.usageBefore, charge: { ...simulation.charge, meteringFacts: facts } })
    simulationResult.value = response.data
  } finally { simulating.value = false }
}

const handleReviewContracts = async () => {
  reviewing.value = true
  try {
    const { data } = await reviewBillingContracts()
    ElMessage.success(`检查 ${data.checked} 个方案，${data.needsReview} 个需复核`)
    fetchPlans()
  } finally { reviewing.value = false }
}
const handleAccrue = async () => {
  accruing.value = true
  try { const { data } = await accrueBillingPlans(); ElMessage.success(`已生成 ${data.created} 条周期费用事件`); fetchEvents() } finally { accruing.value = false }
}
const handleReverse = async (event: BillingEvent) => {
  const { value } = await ElMessageBox.prompt('请输入冲正原因。原始事件不会删除，将追加一条负向事件。', '冲正计费事件', { inputValidator: value => Boolean(value?.trim()) || '冲正原因不能为空', type: 'warning' })
  await reverseBillingEvent(event.id, { requestId: `REVERSAL-${event.requestId}-${Date.now()}`, reason: value })
  ElMessage.success('冲正事件已入账')
  fetchEvents()
}

const templateName = (code: string) => templates.value.find(item => item.templateCode === code)?.templateName || code
const editable = (row: BillingPlan) => row.status === 'DRAFT'
const statusMeta = (status?: string) => ({
  DRAFT: { label: '草稿', type: 'info' }, PUBLISHED: { label: '待生效', type: 'primary' }, ACTIVE: { label: '生效中', type: 'success' },
  EXPIRED: { label: '已失效', type: 'info' }, DISABLED: { label: '已停用', type: 'info' }, NEEDS_REVIEW: { label: '契约待复核', type: 'warning' }
}[status || ''] || { label: status || '-', type: 'info' }) as { label: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }
const eventTypeName = (type: string) => ({ USAGE: '用量', RECURRING_FEE: '周期费', REVERSAL: '冲正' }[type] || type)
const quantityTypeName = (type: string) => ({ FIXED: '固定数量', FACT: '响应数值', ARRAY_SIZE: '数组长度', DURATION: '调用耗时' }[type] || type)
const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 16) : '-'

onMounted(async () => {
  await cacheStore.loadAll()
  try {
    const templateResponse = await getBillingTemplates()
    templates.value = templateResponse.data?.length ? templateResponse.data : builtinTemplates
  } catch {
    templates.value = builtinTemplates
  }
  await Promise.all([fetchPlans(), fetchEvents()])
})
</script>

<style scoped>
.billing-workspace { display: flex; flex-direction: column; gap: 18px; }
.workspace-toolbar, .ledger-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.workspace-toolbar h3, .ledger-header h3 { margin: 0 0 5px; font-size: 17px; color: var(--color-text-primary); }
.workspace-toolbar p, .ledger-header p { margin: 0; font-size: 13px; color: var(--color-text-tertiary); }
.toolbar-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; }
.ledger-filters { display: flex; gap: 10px; }
.primary-cell { color: var(--color-text-primary); font-weight: 600; }
.secondary-cell { margin-top: 3px; color: var(--color-text-tertiary); font-size: 12px; }
.amount-cell { color: var(--color-primary); font-family: var(--font-mono); font-weight: 600; }
.ledger-header { margin-top: 10px; padding-top: 22px; border-top: 1px solid var(--color-border); }
.pagination-row { display: flex; justify-content: flex-end; }
.plan-steps { margin: 4px 20px 28px; }
.step-pane { min-height: 390px; padding: 22px 18px 6px; border-top: 1px solid var(--color-border); }
.template-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; align-content: start; }
.template-option { min-height: 126px; padding: 20px; text-align: left; border: 1px solid var(--color-border); border-radius: 10px; background: var(--color-bg-card); color: var(--color-text-secondary); cursor: pointer; transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease; }
.template-option:hover { border-color: var(--color-primary); transform: translateY(-1px); }
.template-option.selected { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 16%, transparent); }
.template-title { display: block; margin-bottom: 10px; color: var(--color-text-primary); font-size: 16px; font-weight: 650; }
.template-option span:last-child { font-size: 13px; line-height: 1.65; }
.two-column-form { display: grid; grid-template-columns: 1fr 1fr; column-gap: 24px; align-content: start; }
.condition-row { display: grid; grid-template-columns: minmax(250px, 2fr) 130px minmax(160px, 1fr) 48px; gap: 10px; margin-bottom: 10px; align-items: center; }
.section-title-row { display: flex; align-items: center; justify-content: space-between; margin: 18px 0 10px; }
.section-title-row h4, .step-pane > h4 { margin: 0; color: var(--color-text-primary); }
.empty-hint { padding: 22px; text-align: center; color: var(--color-text-tertiary); background: var(--color-bg-page); border-radius: 8px; }
.metering-options { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 20px 0; padding: 18px; background: var(--color-bg-page); border-radius: 9px; }
.metering-options label { display: flex; flex-direction: column; gap: 8px; color: var(--color-text-secondary); font-size: 12px; }
.quantity-row { display: grid; grid-template-columns: 180px minmax(220px, 1fr) 180px; gap: 12px; margin-top: 12px; }
.tier-block { grid-column: 1 / -1; }
.tier-row { display: grid; grid-template-columns: 1fr 1fr 1fr 130px 48px; gap: 10px; margin-bottom: 10px; }
.tier-row :deep(.el-input-number), .quantity-row :deep(.el-input-number) { width: 100%; }
.review-summary { grid-column: 1 / -1; display: flex; flex-direction: column; gap: 7px; margin-top: 8px; padding: 18px 22px; border-left: 3px solid var(--color-primary); background: var(--color-bg-page); color: var(--color-text-secondary); }
.review-summary strong { color: var(--color-text-primary); font-size: 16px; }
.simulation-result { margin-bottom: 8px; font-weight: 600; }
.error-text { color: var(--el-color-danger); }
@media (max-width: 900px) {
  .template-grid, .two-column-form { grid-template-columns: 1fr; }
  .metering-options { grid-template-columns: 1fr 1fr; }
  .condition-row { grid-template-columns: 1fr 1fr; }
  .quantity-row, .tier-row { grid-template-columns: 1fr 1fr; }
  .workspace-toolbar, .ledger-header { flex-direction: column; }
}
</style>

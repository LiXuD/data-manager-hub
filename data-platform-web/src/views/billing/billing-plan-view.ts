import type { BillingPlan } from '@/api/billing'

export type BillingPlanTemporalState = 'DRAFT' | 'SCHEDULED' | 'CURRENT' | 'EXPIRED' | 'DISABLED'

export interface BillingPlanGroup {
  key: string
  vendorName: string
  vendorCode: string
  interfaceName: string
  interfaceCode: string
  accountingPurpose: BillingPlan['accountingPurpose']
  versions: BillingPlan[]
  currentPlans: BillingPlan[]
  scheduledPlans: BillingPlan[]
  draftPlans: BillingPlan[]
  historyPlans: BillingPlan[]
  adjustmentSource?: BillingPlan
  hasConflict: boolean
}

const effectiveStatuses = new Set(['PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW'])

export const billingPlanTemporalState = (
  plan: BillingPlan,
  now: Date = new Date()
): BillingPlanTemporalState => {
  if (plan.status === 'DRAFT') return 'DRAFT'
  if (plan.status === 'DISABLED') return 'DISABLED'
  if (plan.status === 'EXPIRED') return 'EXPIRED'

  const effectiveFrom = new Date(plan.effectiveFrom)
  if (effectiveFrom.getTime() > now.getTime()) return 'SCHEDULED'
  if (plan.effectiveTo && new Date(plan.effectiveTo).getTime() <= now.getTime()) return 'EXPIRED'
  return 'CURRENT'
}

export const groupBillingPlans = (
  plans: BillingPlan[],
  now: Date = new Date()
): BillingPlanGroup[] => {
  const groups = new Map<string, BillingPlan[]>()
  for (const plan of plans) {
    const key = [
      plan.vendorId ?? plan.vendorCode ?? '-',
      plan.interfaceId ?? plan.interfaceCode ?? '-',
      plan.accountingPurpose
    ].join('::')
    groups.set(key, [...(groups.get(key) || []), plan])
  }

  return [...groups.entries()].map(([key, versions]) => {
    const sorted = [...versions].sort((left, right) => Number(right.version || 0) - Number(left.version || 0))
    const currentPlans = sorted.filter(plan =>
      effectiveStatuses.has(plan.status || '') && billingPlanTemporalState(plan, now) === 'CURRENT')
    const scheduledPlans = sorted.filter(plan =>
      effectiveStatuses.has(plan.status || '') && billingPlanTemporalState(plan, now) === 'SCHEDULED')
    const draftPlans = sorted.filter(plan => plan.status === 'DRAFT')
    const historyPlans = sorted.filter(plan => ['EXPIRED', 'DISABLED'].includes(billingPlanTemporalState(plan, now)))
    const reference = currentPlans[0] || scheduledPlans[0] || draftPlans[0] || sorted[0]

    return {
      key,
      vendorName: reference.vendorName || '-',
      vendorCode: reference.vendorCode || '-',
      interfaceName: reference.interfaceName || '-',
      interfaceCode: reference.interfaceCode || '-',
      accountingPurpose: reference.accountingPurpose,
      versions: sorted,
      currentPlans,
      scheduledPlans,
      draftPlans,
      historyPlans,
      adjustmentSource: currentPlans[0] || scheduledPlans[0] || historyPlans[0],
      hasConflict: currentPlans.length > 1
    }
  }).sort((left, right) =>
    `${left.vendorName}\u0000${left.interfaceName}\u0000${left.accountingPurpose}`
      .localeCompare(`${right.vendorName}\u0000${right.interfaceName}\u0000${right.accountingPurpose}`, 'zh-CN'))
}

export const normalizeBillingPlanForSubmit = (plan: BillingPlan): BillingPlan => {
  const normalized = JSON.parse(JSON.stringify(plan)) as BillingPlan
  if (normalized.templateCode === 'PER_CALL') {
    Object.assign(normalized.metering.quantity, {
      type: 'FIXED',
      alias: 'quantity',
      source: 'NORMALIZED_RESPONSE',
      extraction: 'VALUE',
      fixedValue: 1,
      unit: 'CALL',
      fieldId: undefined,
      path: undefined
    })
  }
  return normalized
}

const money = (value?: number) => {
  const amount = Number(value || 0)
  return amount.toFixed(8).replace(/\.0+$/, '.00').replace(/(\.\d*?)0+$/, '$1')
}

const currency = (code: string) => code === 'CNY' ? '¥' : `${code} `

export const billingPricingPreview = (plan: BillingPlan) => {
  const symbol = currency(plan.currency)
  const unitPrice = money(plan.pricing.unitPrice)
  switch (plan.templateCode) {
    case 'PER_CALL':
      return `1 次 × ${symbol}${unitPrice} = ${symbol}${unitPrice} / 次调用`
    case 'PER_ITEM':
      return `返回数量 × ${symbol}${unitPrice} / ${plan.metering.quantity.unit || 'ITEM'}`
    case 'DURATION':
      return `折算时长 × ${symbol}${unitPrice} / ${plan.metering.quantity.unit || '时间单位'}`
    case 'TIERED':
      return `账期累计数量按 ${plan.tiers.length} 档阶梯计价`
    case 'PACKAGE_COUNT':
      return `${symbol}${money(plan.pricing.packageFee)} / ${plan.settlementCycle}，含 ${plan.pricing.includedUnits || 0} 次，超额 ${symbol}${money(plan.pricing.overageUnitPrice)} / 次`
    case 'FLAT_PERIOD':
      return `${symbol}${money(plan.pricing.packageFee)} / ${plan.settlementCycle}`
    default:
      return `${symbol}${unitPrice}`
  }
}

export const billingPricingWarnings = (plan: BillingPlan) => {
  const warnings: string[] = []
  if (plan.templateCode === 'PER_CALL'
      && (plan.metering.quantity.type !== 'FIXED' || Number(plan.metering.quantity.fixedValue) !== 1)) {
    warnings.push('按次计费数量将固定为 1，不能使用自定义倍数。')
  }
  const hasPrice = Number(plan.pricing.unitPrice || 0) > 0
    || Number(plan.pricing.packageFee || 0) > 0
    || Number(plan.pricing.overageUnitPrice || 0) > 0
  if (/免费/.test(plan.planName) && hasPrice) {
    warnings.push('方案名称包含“免费”，但当前价格不为零，请确认名称或价格。')
  }
  return warnings
}

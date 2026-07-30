import { describe, expect, it } from 'vitest'
import type { BillingPlan } from '@/api/billing'
import {
  billingPlanTemporalState,
  billingPricingPreview,
  billingPricingWarnings,
  groupBillingPlans,
  normalizeBillingPlanForSubmit
} from '../billing-plan-view'

const plan = (overrides: Partial<BillingPlan> = {}): BillingPlan => ({
  id: 1,
  planCode: 'PLAN-A',
  version: 1,
  planName: '接口计费方案',
  vendorId: 1,
  vendorCode: 'VENDOR',
  vendorName: '测试厂商',
  interfaceId: 10,
  interfaceCode: 'INTERFACE-A',
  interfaceName: '接口 A',
  templateCode: 'PER_CALL',
  accountingPurpose: 'VENDOR_PAYABLE',
  currency: 'CNY',
  timezone: 'Asia/Shanghai',
  settlementCycle: 'MONTH',
  status: 'ACTIVE',
  effectiveFrom: '2026-01-01T00:00:00',
  pricing: {
    unitPrice: 0.5, packageFee: 0, includedUnits: 0, overageUnitPrice: 0,
    tierMode: 'GRADUATED', durationUnit: 'SECOND', durationRounding: 'CEILING', carryOver: false
  },
  metering: {
    logic: 'AND', conditions: [], missingFieldPolicy: 'PENDING_REVIEW', cacheBillingPolicy: 'FREE',
    aggregationScope: 'VENDOR_INTERFACE',
    quantity: { type: 'FIXED', alias: 'quantity', source: 'NORMALIZED_RESPONSE', extraction: 'VALUE', fixedValue: 1, unit: 'CALL' }
  },
  adjustment: { noChargeOnFailure: true, requireValidContract: false, slaEnabled: false },
  tiers: [],
  ...overrides
})

describe('billing plan management view model', () => {
  const now = new Date('2026-07-30T12:00:00')

  it('groups plans by vendor, interface and accounting purpose instead of active status', () => {
    const groups = groupBillingPlans([
      plan(),
      plan({ id: 2, planCode: 'PLAN-B', interfaceId: 11, interfaceCode: 'INTERFACE-B', interfaceName: '接口 B' })
    ], now)

    expect(groups).toHaveLength(2)
    expect(groups.map(group => group.interfaceCode)).toEqual(['INTERFACE-A', 'INTERFACE-B'])
  })

  it('derives expired state from the effective window even if stored status is active', () => {
    expect(billingPlanTemporalState(plan({ effectiveTo: '2026-07-01T00:00:00' }), now)).toBe('EXPIRED')
  })

  it('detects multiple current versions in the same billing dimension', () => {
    const groups = groupBillingPlans([plan(), plan({ id: 2, planCode: 'PLAN-B', version: 2 })], now)

    expect(groups[0].hasConflict).toBe(true)
    expect(groups[0].currentPlans).toHaveLength(2)
  })

  it('normalizes per-call quantity to one before saving', () => {
    const normalized = normalizeBillingPlanForSubmit(plan({
      metering: {
        ...plan().metering,
        quantity: { ...plan().metering.quantity, type: 'FIXED', fixedValue: 20, unit: 'ITEM', path: '$.data.count', fieldId: 9 }
      }
    }))

    expect(normalized.metering.quantity).toMatchObject({ type: 'FIXED', fixedValue: 1, unit: 'CALL' })
    expect(normalized.metering.quantity.path).toBeUndefined()
    expect(normalized.metering.quantity.fieldId).toBeUndefined()
  })

  it('shows the per-call equation and warns about misleading free names', () => {
    const pricedFreePlan = plan({ planName: '免费方案' })

    expect(billingPricingPreview(pricedFreePlan)).toBe('1 次 × ¥0.5 = ¥0.5 / 次调用')
    expect(billingPricingWarnings(pricedFreePlan)).toContain('方案名称包含“免费”，但当前价格不为零，请确认名称或价格。')
  })
})

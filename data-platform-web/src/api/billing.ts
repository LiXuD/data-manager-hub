import { request } from '@/utils/request'
import type { PageParams, BillingRecordDTO } from '@/types'

export const getBillingList = (params: PageParams & { tenantId?: number; vendorId?: number; startDate?: string; endDate?: string }) => {
  return request.get('/billing/list', { params })
}

export const getBillingById = (id: number) => {
  return request.get<{ data: BillingRecordDTO }>(`/billing/${id}`)
}

export const getBillingStats = (params: { tenantId?: number; startDate?: string; endDate?: string }) => {
  return request.get<{ data: { totalCost: number; totalCallCount: number; days: number } }>('/billing/stats', { params })
}

export const exportBilling = (params: { startDate?: string; endDate?: string; vendorId?: number }) => {
  return request.get<Blob>('/billing/export', { params, responseType: 'blob' })
}

export interface BillingTemplate {
  id: number
  templateCode: string
  templateVersion: number
  templateName: string
  category: string
  description?: string
  supportsQuantity: boolean
  supportsCycle: boolean
  status: string
}

export interface BillingCondition {
  alias: string
  source: 'NORMALIZED_RESPONSE' | 'REQUEST' | 'METADATA'
  fieldId?: number
  path: string
  extraction: 'VALUE' | 'ARRAY_SIZE' | 'EXISTS'
  operator: string
  expectedValue?: any
}

export interface BillingPlan {
  id?: number
  planCode?: string
  version?: number
  planName: string
  vendorId?: number
  vendorCode?: string
  vendorName?: string
  interfaceId?: number
  interfaceCode?: string
  interfaceName?: string
  templateCode: string
  accountingPurpose: 'VENDOR_PAYABLE' | 'INTERNAL_CHARGEBACK'
  currency: string
  timezone: string
  settlementCycle: 'DAY' | 'MONTH' | 'YEAR'
  status?: string
  effectiveFrom: string
  effectiveTo?: string
  contractFingerprint?: string
  pricing: {
    unitPrice: number
    packageFee: number
    includedUnits: number
    overageUnitPrice: number
    cacheUnitPrice?: number
    tierMode: 'GRADUATED' | 'VOLUME'
    durationUnit: 'MILLISECOND' | 'SECOND' | 'MINUTE' | 'HOUR'
    durationRounding: 'CEILING' | 'FLOOR' | 'HALF_UP'
    carryOver: boolean
  }
  metering: {
    logic: 'AND' | 'OR'
    conditions: BillingCondition[]
    quantity: {
      type: 'FIXED' | 'FACT' | 'ARRAY_SIZE' | 'DURATION'
      alias: string
      source: 'NORMALIZED_RESPONSE' | 'REQUEST' | 'METADATA'
      fieldId?: number
      path?: string
      extraction: 'VALUE' | 'ARRAY_SIZE'
      fixedValue: number
      unit: string
    }
    missingFieldPolicy: 'PENDING_REVIEW' | 'NOT_BILLABLE' | 'BILLABLE' | 'ERROR'
    cacheBillingPolicy: 'FREE' | 'SAME_PRICE' | 'CUSTOM'
    aggregationScope: 'VENDOR_INTERFACE' | 'TENANT' | 'CALLER'
  }
  adjustment: {
    noChargeOnFailure: boolean
    requireValidContract: boolean
    slaEnabled: boolean
    slaThresholdMs?: number
    compensationRatePer100Ms?: number
  }
  tiers: Array<{
    id?: number
    tierMin: number
    tierMax?: number
    unitPrice?: number
    discount: number
    sortOrder?: number
  }>
}

export interface BillingEvent {
  id: number
  requestId: string
  eventType: string
  originalEventId?: number
  planCode: string
  planVersion: number
  templateCode: string
  accountingPurpose: string
  tenantId?: number
  callerId?: number
  vendorName?: string
  vendorId: number
  interfaceCode: string
  quantity: number
  unit: string
  finalAmount: number
  currency: string
  status: string
  decisionDetail?: string
  callTime: string
}

export const getBillingTemplates = () =>
  request.get<{ data: BillingTemplate[] }>('/billing/template/list')

export const getBillingPlans = () =>
  request.get<{ data: BillingPlan[] }>('/billing/plan/list')

export const getBillingPlan = (id: number) =>
  request.get<{ data: BillingPlan }>(`/billing/plan/${id}`)

export const createBillingPlan = (data: BillingPlan) =>
  request.post<{ data: BillingPlan }>('/billing/plan', data)

export const updateBillingPlan = (id: number, data: BillingPlan) =>
  request.put<{ data: BillingPlan }>(`/billing/plan/${id}`, data)

export const createBillingPlanVersion = (id: number) =>
  request.post<{ data: BillingPlan }>(`/billing/plan/${id}/next-version`)

export const validateBillingPlan = (id: number) =>
  request.post<{ data: { valid: boolean; errors: string[] } }>(`/billing/plan/${id}/validate`)

export const simulateBillingPlan = (id: number, data: {
  charge: Record<string, unknown>
  usageBefore: number
}) => request.post<{ data: {
  valid: boolean
  billable: boolean
  quantity: number
  usageBefore: number
  baseAmount: number
  adjustmentAmount: number
  finalAmount: number
  matchedTier?: string
  decisions: string[]
  errors: string[]
} }>(`/billing/plan/${id}/simulate`, data)

export const publishBillingPlan = (id: number) =>
  request.post<{ data: BillingPlan }>(`/billing/plan/${id}/publish`)

export const deleteBillingPlan = (id: number) =>
  request.delete<void>(`/billing/plan/${id}`)

export const reviewBillingContracts = () =>
  request.post<{ data: { checked: number; initialized: number; needsReview: number; unavailable: number } }>('/billing/plan/review-contracts')

export const accrueBillingPlans = (date?: string) =>
  request.post<{ data: { created: number } }>('/billing/plan/accrue', undefined, { params: { date } })

export const getBillingEvents = (params: PageParams & {
  tenantId?: number
  vendorId?: number
  interfaceId?: number
  accountingPurpose?: string
  status?: string
  startTime?: string
  endTime?: string
}) => request.get('/billing/event/list', { params })

export const getBillingEventStats = (params: {
  tenantId?: number
  vendorId?: number
  interfaceId?: number
  accountingPurpose?: string
  startTime?: string
  endTime?: string
}) => request.get('/billing/event/stats', { params })

export const reverseBillingEvent = (id: number, data: { requestId: string; reason: string }) =>
  request.post<{ data: BillingEvent }>(`/billing/event/${id}/reverse`, data)

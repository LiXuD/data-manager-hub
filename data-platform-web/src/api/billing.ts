import { request } from '@/utils/request'
import type { PageParams, BillingRuleDTO, BillingRecordDTO } from '@/types'

export const getBillingList = (params: PageParams & { vendorId?: number; startDate?: string; endDate?: string }) => {
  return request.get('/billing/list', { params })
}

export const getBillingById = (id: number) => {
  return request.get<{ data: BillingRecordDTO }>(`/billing/${id}`)
}

export const getBillingStats = (params: { startDate?: string; endDate?: string }) => {
  return request.get<{ data: { totalAmount: number; totalCalls: number; avgPrice: number; overdueCount: number } }>('/billing/stats', { params })
}

export const exportBilling = (params: { startDate: string; endDate: string; vendorId?: number }) => {
  return request.get<void>('/billing/export', { params })
}

// 计费规则
export const getBillingRuleList = (params: PageParams & { vendorId?: number; dataTypeId?: number }) => {
  return request.get('/billing/rule/list', { params })
}

export const createBillingRule = (data: Partial<BillingRuleDTO>) => {
  return request.post<{ data: BillingRuleDTO }>('/billing/rule', data)
}

export const updateBillingRule = (id: number, data: Partial<BillingRuleDTO>) => {
  return request.put<{ data: BillingRuleDTO }>(`/billing/rule/${id}`, data)
}

export const deleteBillingRule = (id: number) => {
  return request.delete<void>(`/billing/rule/${id}`)
}

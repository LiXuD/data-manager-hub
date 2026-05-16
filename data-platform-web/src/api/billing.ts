import { request } from '@/utils/request'
import type { PageParams, BillingRuleDTO } from '@/types'

export const getBillingList = (params: PageParams & { vendorId?: number; startDate?: string; endDate?: string }) => {
  return request.get('/billing/list', { params })
}

export const getBillingById = (id: number) => {
  return request.get(`/billing/${id}`)
}

export const getBillingStats = (params: { startDate?: string; endDate?: string }) => {
  return request.get('/billing/stats', { params })
}

export const exportBilling = (params: { startDate: string; endDate: string; vendorId?: number }) => {
  return request.get('/billing/export', { params })
}

// 计费规则
export const getBillingRuleList = (params: PageParams & { vendorId?: number; dataTypeId?: number }) => {
  return request.get('/billing/rule/list', { params })
}

export const createBillingRule = (data: Partial<BillingRuleDTO>) => {
  return request.post('/billing/rule', data)
}

export const updateBillingRule = (id: number, data: Partial<BillingRuleDTO>) => {
  return request.put(`/billing/rule/${id}`, data)
}

export const deleteBillingRule = (id: number) => {
  return request.delete(`/billing/rule/${id}`)
}

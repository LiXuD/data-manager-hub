import request from '@/utils/request'
import type { PageParams } from '@/types'

export interface BillingRule {
  id: number
  vendorId: number
  vendorName: string
  dataTypeId: number
  dataTypeName: string
  pricePerCall: number
  minPrice: number
  maxPrice: number
  discountThreshold: number
  discountRate: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface BillingRecord {
  id: number
  tenantId: number
  tenantName: string
  callerId: number
  callerName: string
  vendorId: number
  vendorName: string
  dataType: string
  callCount: number
  unitPrice: number
  totalAmount: number
  billingDate: string
  status: string
}

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

export const createBillingRule = (data: Partial<BillingRule>) => {
  return request.post('/billing/rule', data)
}

export const updateBillingRule = (id: number, data: Partial<BillingRule>) => {
  return request.put(`/billing/rule/${id}`, data)
}

export const deleteBillingRule = (id: number) => {
  return request.delete(`/billing/rule/${id}`)
}
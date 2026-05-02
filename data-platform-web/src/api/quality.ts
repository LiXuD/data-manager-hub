import request from '@/utils/request'

export interface QualityRule {
  id: number
  ruleName: string
  ruleType: string
  dataType: string
  checkExpression: string
  threshold?: string
  severity: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface QualityScore {
  id: number
  dataType: string
  dataId: number
  score: number
  passCount: number
  failCount: number
  issueSummary?: string
  checkedAt: string
}

export const addQualityRule = (data: Partial<QualityRule>) => {
  return request.post('/quality/rules', data)
}

export const getQualityRules = (dataType?: string) => {
  return request.get('/quality/rules', { params: { dataType } })
}

export const checkQuality = (dataType: string, dataId: number) => {
  return request.post('/quality/check', null, { params: { dataType, dataId } })
}

export const getQualityHistory = (dataType: string, dataId: number) => {
  return request.get('/quality/history', { params: { dataType, dataId } })
}
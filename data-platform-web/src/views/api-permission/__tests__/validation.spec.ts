import { describe, expect, it } from 'vitest'
import type { ApplicationDraft } from '@/api/api-permission'
import { getDraftValidationError } from '../validation'

const completeDraft = (): ApplicationDraft => ({
  requestType: 'OPEN',
  callerId: 2,
  apiKeyId: 7,
  interfaceIds: [11],
  businessPurpose: '用于信贷审批业务的数据查询验证',
  businessScene: '贷前审批',
  expectedDailyCalls: 1000,
  requestedExpireAt: '2026-12-31T23:59:59',
  ticketNo: '',
  cacheEnabled: false,
  requestedCacheDays: undefined
})

describe('getDraftValidationError', () => {
  const now = new Date('2026-07-29T10:00:00')

  it('保存草稿时不强制要求有效截止时间', () => {
    const draft = completeDraft()
    draft.requestedExpireAt = ''

    expect(getDraftValidationError(draft, false, now)).toBeNull()
  })

  it('提交时明确提示缺少有效截止时间', () => {
    const draft = completeDraft()
    draft.requestedExpireAt = ''

    expect(getDraftValidationError(draft, true, now)).toBe('请选择期望有效截止时间')
  })

  it('提交时允许超过 365 天的有效期', () => {
    const draft = completeDraft()
    draft.requestedExpireAt = '2036-07-30T10:00:00'

    expect(getDraftValidationError(draft, true, now)).toBeNull()
  })

  it('提交时拒绝过去的有效截止时间', () => {
    const draft = completeDraft()
    draft.requestedExpireAt = '2026-07-29T09:59:59'

    expect(getDraftValidationError(draft, true, now)).toBe('申请有效截止时间必须晚于当前时间')
  })

  it('返回具体的业务用途长度提示', () => {
    const draft = completeDraft()
    draft.businessPurpose = '太短'

    expect(getDraftValidationError(draft, false, now)).toBe('业务用途长度必须在 10 到 1000 字之间')
  })
})

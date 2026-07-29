import type { ApplicationDraft } from '@/api/api-permission'

export const getDraftValidationError = (
  draft: ApplicationDraft,
  submitting: boolean,
  now = new Date()
): string | null => {
  if (!draft.callerId) return '请选择内部系统'
  if (!draft.apiKeyId) return '请选择 API Key'
  if (draft.interfaceIds.length === 0) return '请选择至少一个申请接口'

  const businessPurpose = draft.businessPurpose.trim()
  if (businessPurpose.length < 10 || businessPurpose.length > 1000) {
    return '业务用途长度必须在 10 到 1000 字之间'
  }

  const businessScene = draft.businessScene.trim()
  if (!businessScene || businessScene.length > 200) {
    return '业务场景不能为空且不能超过 200 字'
  }

  if (draft.expectedDailyCalls < 1 || draft.expectedDailyCalls > 100000000) {
    return '预计日调用量必须在 1 到 100000000 之间'
  }

  if (submitting) {
    if (!draft.requestedExpireAt) return '请选择期望有效截止时间'

    const requestedExpireAt = new Date(draft.requestedExpireAt)
    if (Number.isNaN(requestedExpireAt.getTime())
      || requestedExpireAt <= now) {
      return '申请有效截止时间必须晚于当前时间'
    }
  }

  if (draft.cacheEnabled
    && (!draft.requestedCacheDays
      || draft.requestedCacheDays < 1
      || draft.requestedCacheDays > 365)) {
    return '申请缓存时效必须在 1 到 365 天之间'
  }

  return null
}

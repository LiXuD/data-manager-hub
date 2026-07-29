import { describe, expect, it } from 'vitest'
import {
  ALERT_STATUS,
  API_KEY_STATUS,
  BILLING_STATUS,
  CALL_STATUS,
  COMMON_STATUS,
  ENABLE_STATUS,
  GRAY_RULE_STATUS
} from '../status'

describe('frontend status persistence contracts', () => {
  it('keeps code-enum values aligned with lowercase backend database codes', () => {
    const persistedStatuses = [
      ...Object.values(COMMON_STATUS),
      ...Object.values(ENABLE_STATUS),
      ...Object.values(CALL_STATUS),
      ...Object.values(GRAY_RULE_STATUS),
      ...Object.values(API_KEY_STATUS),
      ...Object.values(BILLING_STATUS),
      ...Object.values(ALERT_STATUS)
    ]

    expect(persistedStatuses).toEqual(persistedStatuses.map(status => status.toLowerCase()))
    expect(COMMON_STATUS).toEqual({ ACTIVE: 'active', INACTIVE: 'inactive' })
    expect(API_KEY_STATUS).toEqual({ ACTIVE: 'active', EXPIRED: 'expired', REVOKED: 'revoked' })
  })
})

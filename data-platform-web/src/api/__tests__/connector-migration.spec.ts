import { beforeEach, describe, expect, it, vi } from 'vitest'
const mockedRequest = vi.hoisted(() => ({
  get: vi.fn()
}))

vi.mock('@/utils/request', () => ({ request: mockedRequest }))

import { getConnectorMigrations } from '../connector-migration'

describe('connector migration API contracts', () => {
  beforeEach(() => vi.clearAllMocks())

  it('exposes only the read-only migration history contract', () => {
    getConnectorMigrations('OBSERVING')
    expect(mockedRequest.get).toHaveBeenCalledWith('/vendor/connector-migration', {
      params: { state: 'OBSERVING' }
    })
  })
})

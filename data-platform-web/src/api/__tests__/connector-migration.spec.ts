import { beforeEach, describe, expect, it, vi } from 'vitest'
const mockedRequest = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn()
}))

vi.mock('@/utils/request', () => ({ request: mockedRequest }))

import {
  completeConnectorMigration,
  getConnectorLegacyInventory,
  getConnectorMigrations,
  observeConnectorMigration,
  prepareConnectorMigration,
  rollbackConnectorMigration,
  startConnectorMigrationObservation
} from '../connector-migration'

describe('connector migration API contracts', () => {
  beforeEach(() => vi.clearAllMocks())

  it('matches inventory and controlled migration action contracts', () => {
    getConnectorMigrations('OBSERVING')
    getConnectorLegacyInventory()
    prepareConnectorMigration(42)
    startConnectorMigrationObservation(42, { expectedRecordVersion: 0 })
    observeConnectorMigration(42, { expectedRecordVersion: 1 })
    completeConnectorMigration(42, { expectedRecordVersion: 2 })
    rollbackConnectorMigration(42, { expectedRecordVersion: 3 })

    expect(mockedRequest.get).toHaveBeenCalledWith('/vendor/connector-migration', {
      params: { state: 'OBSERVING' }
    })
    expect(mockedRequest.get).toHaveBeenCalledWith('/vendor/config/connector-spec/inventory', {
      params: { page: 1, pageSize: 50 }
    })
    expect(mockedRequest.post.mock.calls).toEqual([
      ['/vendor/connector-migration/42/prepare'],
      ['/vendor/connector-migration/42/start-observation', { expectedRecordVersion: 0 }],
      ['/vendor/connector-migration/42/observe', { expectedRecordVersion: 1 }],
      ['/vendor/connector-migration/42/complete', { expectedRecordVersion: 2 }],
      ['/vendor/connector-migration/42/rollback', { expectedRecordVersion: 3 }]
    ])
  })
})

import { request } from '@/utils/request'
import type {
  ConnectorApiResponse,
  VendorConnectorMigration
} from '@/types'

export interface ConnectorLegacyInventoryReason {
  code: string
  stepIndex?: number
  stageKey?: string
  safeMessage: string
}

export interface ConnectorLegacyInventoryCandidate {
  connectorVersionId: number
  versionRole: 'ACTIVE' | 'DRAFT'
  versionNo?: number
  draftVersion?: number
  authoringMode: 'ADVANCED_LEGACY'
  classification: 'LOSSLESS_CONVERTIBLE' | 'REQUIRES_DEDICATED_PLUGIN' | 'MUST_REMAIN_LEGACY'
  reasons: ConnectorLegacyInventoryReason[]
}

export interface ConnectorLegacyInventoryEntry {
  vendorConfigId: number
  vendorCode: string
  dataTypeCode: string
  active?: ConnectorLegacyInventoryCandidate
  draft?: ConnectorLegacyInventoryCandidate
}

export interface ConnectorLegacyInventory {
  total: number
  page: number
  pageSize: number
  pageSummary: {
    vendorConfigCount: number
    legacyDraftCount: number
    legacyActiveCount: number
    losslessConvertibleCount: number
    requiresDedicatedPluginCount: number
    mustRemainLegacyCount: number
  }
  items: ConnectorLegacyInventoryEntry[]
}

export interface ConnectorMigrationStartRequest {
  expectedRecordVersion: number
  minimumObservationMinutes?: number
  minimumCalls?: number
  maximumErrorRate?: number
  maximumP95DurationMs?: number
  minimumBillingCoverageRate?: number
}

export interface ConnectorMigrationObserveRequest {
  expectedRecordVersion: number
  endedAt?: string
}

export interface ConnectorMigrationActionRequest {
  expectedRecordVersion: number
}

const path = '/vendor/connector-migration'

export const getConnectorMigrations = (state?: string) =>
  request.get<ConnectorApiResponse<VendorConnectorMigration[]>>(path, { params: state ? { state } : undefined })

export const getConnectorLegacyInventory = (page = 1, pageSize = 50) =>
  request.get<ConnectorApiResponse<ConnectorLegacyInventory>>('/vendor/config/connector-spec/inventory', {
    params: { page, pageSize }
  })

export const prepareConnectorMigration = (vendorConfigId: number) =>
  request.post<ConnectorApiResponse<VendorConnectorMigration>>(`${path}/${vendorConfigId}/prepare`)

export const startConnectorMigrationObservation = (
  vendorConfigId: number,
  payload: ConnectorMigrationStartRequest
) => request.post<ConnectorApiResponse<VendorConnectorMigration>>(
  `${path}/${vendorConfigId}/start-observation`, payload
)

export const observeConnectorMigration = (
  vendorConfigId: number,
  payload: ConnectorMigrationObserveRequest
) => request.post<ConnectorApiResponse<VendorConnectorMigration>>(
  `${path}/${vendorConfigId}/observe`, payload
)

export const completeConnectorMigration = (
  vendorConfigId: number,
  payload: ConnectorMigrationActionRequest
) => request.post<ConnectorApiResponse<VendorConnectorMigration>>(
  `${path}/${vendorConfigId}/complete`, payload
)

export const rollbackConnectorMigration = (
  vendorConfigId: number,
  payload: ConnectorMigrationActionRequest
) => request.post<ConnectorApiResponse<VendorConnectorMigration>>(
  `${path}/${vendorConfigId}/rollback`, payload
)

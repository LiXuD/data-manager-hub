import { request } from '@/utils/request'
import type {
  ConnectorApiResponse,
  ConnectorPipelineStep,
  ConnectorTestResult,
  ConnectorValidationResult,
  VendorConnectorDraft,
  VendorConnectorVersion
} from '@/types'

const path = (configId: number) => `/vendor/config/${configId}/connector`

export const getActiveVendorConnector = (configId: number) =>
  request.get<ConnectorApiResponse<VendorConnectorVersion | null>>(path(configId))

export const getVendorConnectorDraft = (configId: number) =>
  request.get<ConnectorApiResponse<VendorConnectorDraft>>(`${path(configId)}/draft`)

export const saveVendorConnectorDraft = (configId: number, expectedDraftVersion: number, pipelineSnapshot: ConnectorPipelineStep[]) =>
  request.put<ConnectorApiResponse<VendorConnectorDraft>>(`${path(configId)}/draft`, { expectedDraftVersion, pipelineSnapshot })

export const validateVendorConnector = (configId: number) =>
  request.post<ConnectorApiResponse<ConnectorValidationResult>>(`${path(configId)}/validate`)

export const testVendorConnector = (configId: number, params: Record<string, unknown>) =>
  request.post<ConnectorApiResponse<ConnectorTestResult>>(`${path(configId)}/test`, { params })

export const publishVendorConnector = (configId: number, expectedDraftVersion: number) =>
  request.post<ConnectorApiResponse<VendorConnectorVersion>>(`${path(configId)}/publish`, { expectedDraftVersion })

export const getVendorConnectorVersions = (configId: number) =>
  request.get<ConnectorApiResponse<VendorConnectorVersion[]>>(`${path(configId)}/versions`)

export const rollbackVendorConnector = (configId: number, version: number, expectedConnectorVersion: number) =>
  request.post<ConnectorApiResponse<VendorConnectorVersion>>(`${path(configId)}/rollback/${version}`, { expectedConnectorVersion })

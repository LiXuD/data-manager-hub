import { request } from '@/utils/request'
import type {
  ConnectorApiResponse,
  ConnectorExecutionPlan,
  ConnectorSpec,
  ConnectorSpecCatalog,
  ConnectorSpecCatalogVersion,
  ConnectorSpecConversionPreview,
  ConnectorSpecDraftView,
  ConnectorSpecHistory,
  ConnectorSpecUpgradePreview,
  ConnectorSpecValidationResult,
  ConnectorSpecVersion,
  ConnectorTestResult
} from '@/types'

const path = (configId: number) => `/vendor/config/${configId}/connector-spec`

export const getConnectorSpecCatalog = (configId: number) =>
  request.get<ConnectorApiResponse<ConnectorSpecCatalog>>(`${path(configId)}/catalog`)

export const getConnectorSpecCatalogVersions = (configId: number, pluginId: string) =>
  request.get<ConnectorApiResponse<ConnectorSpecCatalogVersion[]>>(
    `${path(configId)}/catalog/${encodeURIComponent(pluginId)}/versions`
  )

export const getConnectorSpecDraft = (configId: number) =>
  request.get<ConnectorApiResponse<ConnectorSpecDraftView>>(`${path(configId)}/draft`)

export const saveConnectorSpecDraft = (
  configId: number,
  expectedDraftVersion: number,
  connectorSpec: ConnectorSpec
) => request.put<ConnectorApiResponse<ConnectorSpecDraftView>>(`${path(configId)}/draft`, {
  expectedDraftVersion,
  connectorSpec
})

export const validateConnectorSpecDraft = (configId: number) =>
  request.post<ConnectorApiResponse<ConnectorSpecValidationResult>>(`${path(configId)}/validate`)

export const testConnectorSpecDraft = (configId: number, params: Record<string, unknown>) =>
  request.post<ConnectorApiResponse<ConnectorTestResult>>(`${path(configId)}/test`, { params })

export const publishConnectorSpecDraft = (configId: number, expectedDraftVersion: number) =>
  request.post<ConnectorApiResponse<ConnectorSpecVersion>>(`${path(configId)}/publish`, {
    expectedDraftVersion
  })

export const getConnectorSpecHistory = (configId: number) =>
  request.get<ConnectorApiResponse<ConnectorSpecHistory>>(`${path(configId)}/versions`)

export const getConnectorExecutionPlan = (configId: number, version?: number) =>
  request.get<ConnectorApiResponse<ConnectorExecutionPlan>>(`${path(configId)}/execution-plan`, {
    params: version == null ? undefined : { version }
  })

export const rollbackConnectorSpecVersion = (
  configId: number,
  version: number,
  expectedConnectorVersion: number
) => request.post<ConnectorApiResponse<ConnectorSpecVersion>>(
  `${path(configId)}/rollback/${version}`,
  { expectedConnectorVersion }
)

export const previewConnectorSpecConversion = (configId: number) =>
  request.post<ConnectorApiResponse<ConnectorSpecConversionPreview>>(
    `${path(configId)}/convert-preview`
  )

export const convertLegacyConnectorSpec = (configId: number, expectedDraftVersion: number) =>
  request.post<ConnectorApiResponse<ConnectorSpecDraftView>>(`${path(configId)}/convert`, {
    expectedDraftVersion
  })

export const previewConnectorSpecUpgrade = (
  configId: number,
  expectedDraftVersion: number,
  targetPluginVersion: string
) => request.post<ConnectorApiResponse<ConnectorSpecUpgradePreview>>(
  `${path(configId)}/upgrade-preview`,
  { expectedDraftVersion, targetPluginVersion }
)

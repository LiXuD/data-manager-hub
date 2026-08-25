import { request } from '@/utils/request'
import type {
  ConnectorApiResponse,
  ConnectorPlugin,
  ConnectorPluginActivationSummary,
  ConnectorPluginImportRequest,
  ConnectorPluginVersion
} from '@/types'

const base = '/connector-plugin'

export const getConnectorPlugins = () =>
  request.get<ConnectorApiResponse<ConnectorPlugin[]>>(base)

export const getConnectorPlugin = (pluginId: string) =>
  request.get<ConnectorApiResponse<ConnectorPlugin>>(`${base}/${encodeURIComponent(pluginId)}`)

export const getConnectorPluginVersions = (pluginId: string) =>
  request.get<ConnectorApiResponse<ConnectorPluginVersion[]>>(`${base}/${encodeURIComponent(pluginId)}/versions`)

export const importConnectorPluginVersion = (data: ConnectorPluginImportRequest) =>
  request.post<ConnectorApiResponse<ConnectorPluginVersion>>(`${base}/versions/import`, data)

export const verifyConnectorPluginVersion = (pluginId: string, version: string) =>
  request.post<ConnectorApiResponse<ConnectorPluginVersion>>(`${base}/${encodeURIComponent(pluginId)}/versions/${encodeURIComponent(version)}/verify`)

export const stageConnectorPluginVersion = (pluginId: string, version: string) =>
  request.post<ConnectorApiResponse<ConnectorPluginActivationSummary>>(`${base}/${encodeURIComponent(pluginId)}/versions/${encodeURIComponent(version)}/stage`)

export const getConnectorPluginActivation = (pluginId: string, version: string) =>
  request.get<ConnectorApiResponse<ConnectorPluginActivationSummary>>(`${base}/${encodeURIComponent(pluginId)}/versions/${encodeURIComponent(version)}/activation`)

export const activateConnectorPluginVersion = (pluginId: string, version: string) =>
  request.post<ConnectorApiResponse<ConnectorPluginVersion>>(`${base}/${encodeURIComponent(pluginId)}/versions/${encodeURIComponent(version)}/activate`)

export const disableConnectorPluginVersion = (pluginId: string, version: string) =>
  request.post<ConnectorApiResponse<ConnectorPluginVersion>>(`${base}/${encodeURIComponent(pluginId)}/versions/${encodeURIComponent(version)}/disable`)

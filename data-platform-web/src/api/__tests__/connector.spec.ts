import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockedRequest = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn()
}))

vi.mock('@/utils/request', () => ({ request: mockedRequest }))

import {
  activateConnectorPluginVersion,
  disableConnectorPluginVersion,
  getConnectorPlugin,
  getConnectorPluginActivation,
  getConnectorPlugins,
  getConnectorPluginVersions,
  importConnectorPluginVersion,
  stageConnectorPluginVersion,
  verifyConnectorPluginVersion
} from '../connector-plugin'
import {
  getActiveVendorConnector,
  getVendorConnectorDraft,
  getVendorConnectorVersions,
  publishVendorConnector,
  rollbackVendorConnector,
  saveVendorConnectorDraft,
  testVendorConnector,
  validateVendorConnector
} from '../vendor-connector'
import {
  convertLegacyConnectorSpec,
  getConnectorExecutionPlan,
  getConnectorSpecCatalog,
  getConnectorSpecCatalogVersions,
  getConnectorSpecDraft,
  getConnectorSpecHistory,
  previewConnectorSpecConversion,
  previewConnectorSpecUpgrade,
  publishConnectorSpecDraft,
  rollbackConnectorSpecVersion,
  saveConnectorSpecDraft,
  testConnectorSpecDraft,
  validateConnectorSpecDraft
} from '../connector-spec'

describe('connector management API contracts', () => {
  beforeEach(() => vi.clearAllMocks())

  it('matches every connector-plugin controller route', () => {
    getConnectorPlugins()
    getConnectorPlugin('demo/plugin')
    getConnectorPluginVersions('demo/plugin')
    importConnectorPluginVersion({ artifactUri: 'https://repo.test/plugin.jar', expectedSha256: 'abc', detachedSignature: 'sig', signingKeyId: 'key-1' })
    verifyConnectorPluginVersion('demo/plugin', '1.0+build')
    stageConnectorPluginVersion('demo/plugin', '1.0+build')
    getConnectorPluginActivation('demo/plugin', '1.0+build')
    activateConnectorPluginVersion('demo/plugin', '1.0+build')
    disableConnectorPluginVersion('demo/plugin', '1.0+build')

    expect(mockedRequest.get.mock.calls).toEqual([
      ['/connector-plugin'],
      ['/connector-plugin/demo%2Fplugin'],
      ['/connector-plugin/demo%2Fplugin/versions'],
      ['/connector-plugin/demo%2Fplugin/versions/1.0%2Bbuild/activation']
    ])
    expect(mockedRequest.post.mock.calls).toEqual([
      ['/connector-plugin/versions/import', { artifactUri: 'https://repo.test/plugin.jar', expectedSha256: 'abc', detachedSignature: 'sig', signingKeyId: 'key-1' }],
      ['/connector-plugin/demo%2Fplugin/versions/1.0%2Bbuild/verify'],
      ['/connector-plugin/demo%2Fplugin/versions/1.0%2Bbuild/stage'],
      ['/connector-plugin/demo%2Fplugin/versions/1.0%2Bbuild/activate'],
      ['/connector-plugin/demo%2Fplugin/versions/1.0%2Bbuild/disable']
    ])
  })

  it('matches every vendor connector controller route and request body', () => {
    const pipelineSnapshot = [{
      stageKey: 'transport', capability: 'TRANSPORT' as const, pluginId: 'demo', pluginVersion: '1.0.0', order: 0, enabled: true, config: {}
    }]
    getActiveVendorConnector(42)
    getVendorConnectorDraft(42)
    saveVendorConnectorDraft(42, 3, pipelineSnapshot)
    validateVendorConnector(42)
    testVendorConnector(42, { query: 'safe' })
    publishVendorConnector(42, 3)
    getVendorConnectorVersions(42)
    rollbackVendorConnector(42, 2, 7)

    expect(mockedRequest.get.mock.calls).toEqual([
      ['/vendor/config/42/connector'],
      ['/vendor/config/42/connector/draft'],
      ['/vendor/config/42/connector/versions']
    ])
    expect(mockedRequest.put).toHaveBeenCalledWith('/vendor/config/42/connector/draft', { expectedDraftVersion: 3, pipelineSnapshot })
    expect(mockedRequest.post.mock.calls).toEqual([
      ['/vendor/config/42/connector/validate'],
      ['/vendor/config/42/connector/test', { params: { query: 'safe' } }],
      ['/vendor/config/42/connector/publish', { expectedDraftVersion: 3 }],
      ['/vendor/config/42/connector/rollback/2', { expectedConnectorVersion: 7 }]
    ])
  })

  it('matches every connector-spec product route and keeps the raw API read-only fallback separate', () => {
    const connectorSpec = {
      specVersion: '1',
      plugin: { pluginId: 'generic/http', pluginVersion: '2.0.0' },
      config: { endpoint: 'https://vendor.test/api', method: 'GET', auth: { type: 'NONE' } },
      responseMapping: null
    }
    getConnectorSpecCatalog(42)
    getConnectorSpecCatalogVersions(42, 'generic/http')
    getConnectorSpecDraft(42)
    getConnectorSpecHistory(42)
    getConnectorExecutionPlan(42)
    getConnectorExecutionPlan(42, 7)
    saveConnectorSpecDraft(42, 3, connectorSpec)
    validateConnectorSpecDraft(42)
    testConnectorSpecDraft(42, { query: 'safe' })
    publishConnectorSpecDraft(42, 3)
    rollbackConnectorSpecVersion(42, 2, 7)
    previewConnectorSpecConversion(42)
    convertLegacyConnectorSpec(42, 3)
    previewConnectorSpecUpgrade(42, 3, '2.1.0')

    expect(mockedRequest.get.mock.calls).toEqual([
      ['/vendor/config/42/connector-spec/catalog'],
      ['/vendor/config/42/connector-spec/catalog/generic%2Fhttp/versions'],
      ['/vendor/config/42/connector-spec/draft'],
      ['/vendor/config/42/connector-spec/versions'],
      ['/vendor/config/42/connector-spec/execution-plan', { params: undefined }],
      ['/vendor/config/42/connector-spec/execution-plan', { params: { version: 7 } }]
    ])
    expect(mockedRequest.put).toHaveBeenCalledWith(
      '/vendor/config/42/connector-spec/draft',
      { expectedDraftVersion: 3, connectorSpec }
    )
    expect(mockedRequest.post.mock.calls).toEqual([
      ['/vendor/config/42/connector-spec/validate'],
      ['/vendor/config/42/connector-spec/test', { params: { query: 'safe' } }],
      ['/vendor/config/42/connector-spec/publish', { expectedDraftVersion: 3 }],
      ['/vendor/config/42/connector-spec/rollback/2', { expectedConnectorVersion: 7 }],
      ['/vendor/config/42/connector-spec/convert-preview'],
      ['/vendor/config/42/connector-spec/convert', { expectedDraftVersion: 3 }],
      ['/vendor/config/42/connector-spec/upgrade-preview', {
        expectedDraftVersion: 3,
        targetPluginVersion: '2.1.0'
      }]
    ])
  })
})

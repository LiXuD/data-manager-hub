export type ConnectorCapability =
  | 'REQUEST_BUILDER'
  | 'REQUEST_PROCESSOR'
  | 'TRANSPORT'
  | 'RESPONSE_PROCESSOR'
  | 'RESPONSE_PARSER'
  | 'RESPONSE_NORMALIZER'

export type ConnectorPluginStatus = 'ACTIVE' | 'VERIFIED' | 'STAGING' | 'STAGING_FAILED' | 'DISABLED'
/** Historical value retained only for immutable Stage-4 migration audit rows. */
export type ConnectorRuntimeMode = 'LEGACY' | 'PLUGIN'

/** Plugin-only vendor identity/routing and platform execution policy. */
export interface VendorConfigSummary {
  id: number
  vendorId: number
  vendorName?: string
  dataTypeId: number
  dataTypeName?: string
  dataTypeCode?: string
  interfaceId: number
  interfaceName?: string
  timeout: number
  retryCount: number
  circuitThreshold: number
  circuitTimeout: number
  fallbackVendorName?: string
  routingRole?: 'PRIMARY' | 'FALLBACK' | 'UNASSIGNED' | string
  runtimeMode: 'PLUGIN'
  activeConnectorVersionId?: number
  connectorVersion: number
  status: 'active' | 'inactive'
  createdAt: string
  updatedAt: string
}

export interface VendorConfigCreateRequest {
  vendorId: number
  interfaceId: number
  timeout?: number
  retryCount?: number
  circuitThreshold?: number
  circuitTimeout?: number
}

export type VendorConfigUpdateRequest = Partial<Pick<VendorConfigSummary,
  'timeout' | 'retryCount' | 'circuitThreshold' | 'circuitTimeout'>>

/** Response envelope used by the lightweight cross-domain contract module. */
export interface ConnectorApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface ConnectorPlugin {
  id: number
  pluginId: string
  displayName: string
  provider: string
  description?: string
  status: ConnectorPluginStatus
  activeVersion?: string
  bindingCount: number
  createdAt?: string
  updatedAt?: string
}

export interface ConnectorPluginVersion {
  id: number
  pluginId: string
  version: string
  spiVersion: string
  entryClass: string
  artifactUri: string
  artifactSha256: string
  signingKeyId: string
  manifestJson: string
  configSchemaJson: string
  capabilities: ConnectorCapability[]
  permissionManifestJson?: string
  minHostVersion?: string
  status: ConnectorPluginStatus
  safeErrorCode?: string
  safeErrorDigest?: string
  verifiedAt?: string
  createdAt?: string
}

export interface ConnectorPluginImportRequest {
  artifactUri: string
  expectedSha256: string
  detachedSignature: string
  signingKeyId: string
}

export interface ConnectorPluginActivation {
  serviceInstanceId: string
  pluginId: string
  pluginVersion: string
  artifactSha256: string
  hostVersion?: string
  state: string
  loadedAt?: string
  lastHeartbeatAt?: string
  safeErrorCode?: string
  safeErrorDigest?: string
}

export interface ConnectorPluginActivationSummary {
  pluginId: string
  pluginVersion: string
  ready: boolean
  instances: ConnectorPluginActivation[]
}

export interface ConnectorPipelineStep {
  stageKey: string
  capability: ConnectorCapability
  pluginId: string
  pluginVersion: string
  order: number
  enabled: boolean
  config: Record<string, unknown>
  configHash?: string
}

export interface VendorConnectorDraft {
  id?: number
  vendorConfigId: number
  draftVersion: number
  securityVersion?: number
  pipelineSnapshot: ConnectorPipelineStep[]
}

export interface VendorConnectorVersion {
  id: number
  vendorConfigId: number
  versionNo: number
  snapshotHash: string
  securityVersion?: number
  status: string
  previousVersionId?: number
  publishedAt?: string
  publishedBy?: number
  pipelineSnapshot: ConnectorPipelineStep[]
}

export interface ConnectorValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
  snapshotHash?: string
}

export interface ConnectorStageTiming {
  stageKey: string
  capability: ConnectorCapability
  pluginId: string
  pluginVersion: string
  durationMs: number
}

export interface ConnectorTestResult {
  success: boolean
  errorCategory?: string
  errorCode?: string
  safeMessage?: string
  normalizedData?: Record<string, unknown>
  stageTimings: ConnectorStageTiming[]
}

export type ConnectorAuthoringMode = 'SIMPLE_CONNECTOR' | 'ADVANCED_LEGACY'

export interface ConnectorSpecPluginRef {
  pluginId: string
  pluginVersion: string
}

export interface ConnectorSpecResponseMapping {
  targetField: string
  sourcePath: string
  sourceType?: 'field' | 'jsonPath' | string
  defaultValue?: unknown
  transformType?: 'none' | 'toString' | 'toNumber' | string
}

export interface ConnectorSpec {
  specVersion: string
  plugin: ConnectorSpecPluginRef
  config: Record<string, unknown>
  responseMapping?: ConnectorSpecResponseMapping[] | null
}

export interface ConnectorSpecCompatibility {
  vendorCodes: string[]
  dataTypeCodes: string[]
}

export interface ConnectorSpecCatalogEntry {
  pluginId: string
  displayName: string
  provider: string
  description?: string
  connectorKind: string
  transportMode: string
  outputMode: string
  recommendedVersion?: string
  versionCount: number
  configSchema: JsonSchemaNode
  compatibility: ConnectorSpecCompatibility
}

export interface ConnectorSpecCatalogVersion extends Omit<ConnectorSpecCatalogEntry,
  'recommendedVersion' | 'versionCount'> {
  pluginVersion: string
  status: ConnectorPluginStatus
  testable: boolean
  active: boolean
}

export interface ConnectorSpecCatalog {
  plugins: ConnectorSpecCatalogEntry[]
}

export interface ConnectorSpecDraftView {
  present: boolean
  id?: number
  vendorConfigId: number
  draftVersion?: number
  authoringMode?: ConnectorAuthoringMode
  securityVersion?: number
  connectorSpec?: ConnectorSpec | null
  specHash?: string
  compilerVersion?: string
  compileHash?: string
  compiledSnapshotHash?: string
}

export interface ConnectorSpecValidationResult {
  valid: boolean
  errorCode?: string
  specHash?: string
  compilerVersion?: string
  compileHash?: string
  compiledSnapshotHash?: string
}

export type ConnectorPlanSource =
  | 'CONNECTOR'
  | 'PLATFORM_SECURITY'
  | 'PLATFORM_TRANSPORT'
  | 'PLATFORM_NORMALIZER'

export interface ConnectorExecutionPlanStage {
  stageKey: string
  capability: ConnectorCapability
  pluginId: string
  pluginVersion: string
  order: number
  configHash?: string
  artifactHashPrefix?: string
  manifestHashPrefix?: string
  schemaHashPrefix?: string
  source: ConnectorPlanSource
}

export interface ConnectorExecutionPlan {
  connectorVersionId?: number
  version?: number
  draftVersion?: number
  authoringMode: ConnectorAuthoringMode
  snapshotHashPrefix?: string
  stages: ConnectorExecutionPlanStage[]
}

export interface ConnectorSpecVersion {
  id: number
  vendorConfigId: number
  version: number
  authoringMode: ConnectorAuthoringMode
  specHash?: string
  compilerVersion?: string
  compileHash?: string
  snapshotHash?: string
  hashAlgorithm?: string
  integrityHash?: string
  securityVersion?: number
  status: string
  previousVersionId?: number
  publishedAt?: string
  publishedBy?: number
}

export interface ConnectorSpecHistoryVersion extends ConnectorSpecVersion {
  connectorSpec?: ConnectorSpec | null
}

export interface ConnectorSpecHistory {
  versions: ConnectorSpecHistoryVersion[]
}

export interface ConnectorConversionReason {
  code: string
  stepIndex?: number
  stageKey?: string
  safeMessage: string
}

export interface ConnectorSpecConversionPreview {
  convertible: boolean
  classification: string
  errorCode?: string
  reasons: ConnectorConversionReason[]
  connectorSpec?: ConnectorSpec | null
}

export interface ConnectorSpecUpgradePreview {
  currentPlugin: ConnectorSpecPluginRef
  targetPlugin: ConnectorSpecPluginRef
  valid: boolean
  errorCode?: string
  safeMessage?: string
  schemaChanges: Array<{
    path: string
    changeKind: string
    currentType?: string
    targetType?: string
    currentRequired: boolean
    targetRequired: boolean
    secretRef: boolean
  }>
  configChanges: Array<{
    path: string
    changeKind: string
    currentSchemaType?: string
    targetSchemaType?: string
    targetRequired: boolean
    secretRef: boolean
  }>
  planDiff: {
    addedStageCount: number
    removedStageCount: number
    coordinateChangeCount: number
    configHashChangeCount: number
    artifactDigestChangeCount: number
    changedStageKeys: string[]
  }
  previewSpecHash?: string
  compiledSnapshotHash?: string
  compileHash?: string
}

export type VendorConnectorMigrationState =
  | 'PREPARED' | 'VALIDATED' | 'TEST_PASSED' | 'OBSERVING' | 'READY'
  | 'STABLE' | 'FAILED' | 'BLOCKED' | 'ROLLED_BACK'

export interface VendorConnectorMigration {
  id: number
  vendorConfigId: number
  vendorId: number
  interfaceId: number
  state: VendorConnectorMigrationState
  recordVersion: number
  sourceConfigHash?: string
  draftId?: number
  draftVersion?: number
  draftSnapshotHash?: string
  publishedConnectorVersionId?: number
  publishedVersionNo?: number
  previousRuntimeMode: ConnectorRuntimeMode
  previousActiveConnectorVersionId?: number
  previousConnectorVersion: number
  minimumObservationMinutes: number
  minimumCalls: number
  maximumErrorRate: number
  maximumP95DurationMs: number
  minimumBillingCoverageRate: number
  observationStartedAt?: string
  observationEligibleAt?: string
  observedCalls: number
  observedSuccesses: number
  observedFailures: number
  observedErrorRate: number
  observedP95DurationMs: number
  observedCacheHits: number
  observedRealtimeCalls: number
  observedBillingEvents: number
  observedPostedBillingEvents: number
  observedBillingCoverageRate: number
  observedBillingAmount: number
  observationGatePassed: boolean
  safeErrorCode?: string
  safeErrorDigest?: string
  completedAt?: string
  rolledBackAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface JsonSchemaNode {
  type?: 'string' | 'integer' | 'number' | 'boolean' | 'object' | 'array'
  title?: string
  description?: string
  default?: unknown
  enum?: unknown[]
  properties?: Record<string, JsonSchemaNode>
  required?: string[]
  items?: JsonSchemaNode
  additionalProperties?: boolean | JsonSchemaNode
  minItems?: number
  maxItems?: number
  uniqueItems?: boolean
  minimum?: number
  maximum?: number
  minLength?: number
  maxLength?: number
  pattern?: string
  format?: string
  'x-ui-widget'?: string
  'x-ui-order'?: number
  'x-ui-group'?: string
  'x-ui-advanced'?: boolean
  'x-ui-visible-if'?: Record<string, unknown>
  'x-platform-managed'?: string[]
  'x-secret-ref'?: boolean
  'x-sensitive'?: boolean
  'x-placeholder-source'?: string
  'x-help-text'?: string
}

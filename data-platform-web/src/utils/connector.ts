import type {
  ConnectorPipelineStep,
  ConnectorPluginStatus,
  JsonSchemaNode
} from '@/types'

export interface ConnectorDiffItem {
  stageKey: string
  change: 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED'
  before?: ConnectorPipelineStep
  after?: ConnectorPipelineStep
}

export type SecretFieldRepresentation = 'string' | 'object'

export function secretFieldRepresentation(
  schema: JsonSchemaNode,
  fieldName = ''
): SecretFieldRepresentation | undefined {
  const normalized = fieldName.toLowerCase().replace(/[^a-z0-9]/g, '')
  const semanticSensitive = [
    'password', 'token', 'secret', 'privatekey', 'certificate', 'apikey', 'credential'
  ].some(name => normalized.includes(name))
  if (!schema['x-secret-ref'] && !schema['x-sensitive'] && !semanticSensitive) return undefined
  return schema['x-secret-ref'] && schema.type === 'string' && !schema['x-sensitive'] && !semanticSensitive
    ? 'string'
    : 'object'
}

export function readSecretReference(value: unknown): string | undefined {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const reference = (value as Record<string, unknown>).secretRef
    return typeof reference === 'string' ? reference : undefined
  }
  return typeof value === 'string' ? value : undefined
}

export function writeSecretReference(
  representation: SecretFieldRepresentation,
  value: unknown
): string | { secretRef: string } | undefined {
  const reference = typeof value === 'string' && value ? value : undefined
  if (!reference) return undefined
  return representation === 'object' ? { secretRef: reference } : reference
}

export function parseJsonDocument<T>(value?: string, fallback?: T): T {
  if (!value?.trim()) return fallback as T
  try {
    return JSON.parse(value) as T
  } catch {
    return fallback as T
  }
}

export function schemaDefault(schema: JsonSchemaNode): unknown {
  if (schema.default !== undefined) return structuredClone(schema.default)
  if (schema.type === 'object' || schema.properties) {
    return Object.fromEntries(Object.entries(schema.properties || {}).map(([key, node]) => [key, schemaDefault(node)]))
  }
  if (schema.type === 'array') return []
  if (schema.type === 'boolean') return false
  if (schema.type === 'integer' || schema.type === 'number') return undefined
  return schema.enum?.[0] ?? ''
}

export function mergeSchemaDefaults(schema: JsonSchemaNode, value: unknown): unknown {
  if (schema.type === 'object' || schema.properties) {
    const input = value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
    return Object.fromEntries(Object.entries(schema.properties || {}).map(([key, node]) => [
      key,
      input[key] === undefined ? schemaDefault(node) : mergeSchemaDefaults(node, input[key])
    ]))
  }
  if (schema.type === 'array') {
    return Array.isArray(value) ? value.map(item => mergeSchemaDefaults(schema.items || {}, item)) : []
  }
  return value
}

export function orderedSchemaProperties(schema: JsonSchemaNode): Array<[string, JsonSchemaNode]> {
  return Object.entries(schema.properties || {}).sort((left, right) =>
    (left[1]['x-ui-order'] ?? Number.MAX_SAFE_INTEGER) - (right[1]['x-ui-order'] ?? Number.MAX_SAFE_INTEGER)
  )
}

export function diffConnectorPipelines(before: ConnectorPipelineStep[], after: ConnectorPipelineStep[]): ConnectorDiffItem[] {
  const beforeByKey = new Map(before.map(step => [step.stageKey, step]))
  const afterByKey = new Map(after.map(step => [step.stageKey, step]))
  const keys = [...new Set([...beforeByKey.keys(), ...afterByKey.keys()])]
  return keys.map(stageKey => {
    const oldStep = beforeByKey.get(stageKey)
    const newStep = afterByKey.get(stageKey)
    if (!oldStep) return { stageKey, change: 'ADDED', after: newStep }
    if (!newStep) return { stageKey, change: 'REMOVED', before: oldStep }
    const comparable = (step: ConnectorPipelineStep) => JSON.stringify({
      capability: step.capability,
      pluginId: step.pluginId,
      pluginVersion: step.pluginVersion,
      order: step.order,
      enabled: step.enabled,
      config: step.config
    })
    return {
      stageKey,
      change: comparable(oldStep) === comparable(newStep) ? 'UNCHANGED' : 'CHANGED',
      before: oldStep,
      after: newStep
    }
  })
}

export function normalizePipelineOrder(steps: ConnectorPipelineStep[]): ConnectorPipelineStep[] {
  return steps.map((step, order) => ({ ...step, order }))
}

export function canVerify(status: ConnectorPluginStatus): boolean {
  return status !== 'ACTIVE' && status !== 'DISABLED'
}

export function canStage(status: ConnectorPluginStatus): boolean {
  return status === 'VERIFIED' || status === 'STAGING_FAILED' || status === 'STAGING'
}

export function canActivate(status: ConnectorPluginStatus, ready: boolean): boolean {
  return status === 'STAGING' && ready
}

export function canDisable(status: ConnectorPluginStatus): boolean {
  return status === 'ACTIVE' || status === 'VERIFIED' || status === 'STAGING_FAILED'
}

export function disablePluginConfirmation(pluginId: string, version: string): string {
  return `确认禁用 ${pluginId}@${version}？活动绑定会被后端 409 拒绝，需先迁移或回滚；禁用只阻止新绑定，历史版本仍保留。`
}

export function normalizedBindingCount(bindingCount?: number): number {
  return Number.isFinite(bindingCount) && Number(bindingCount) > 0 ? Math.trunc(Number(bindingCount)) : 0
}

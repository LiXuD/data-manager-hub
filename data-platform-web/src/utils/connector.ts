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

const SENSITIVE_FIELD_NAMES = new Set([
  'password', 'passwd', 'token', 'secret', 'privatekey', 'certificate', 'cert', 'apikey', 'apitoken', 'credential',
  'clientsecret', 'apisecret', 'secretkey', 'accesstoken', 'refreshtoken', 'signingkey', 'encryptionkey'
])

export function secretFieldRepresentation(
  schema: JsonSchemaNode,
  fieldName = ''
): SecretFieldRepresentation | undefined {
  const normalized = fieldName.toLowerCase().replace(/[^a-z0-9]/g, '')
  const declared = schema['x-secret-ref'] !== undefined || schema['x-sensitive'] !== undefined
  if (declared) {
    if (!schema['x-secret-ref'] && !schema['x-sensitive']) return undefined
    return schema['x-secret-ref'] && schema.type === 'string' && !schema['x-sensitive']
      ? 'string'
      : 'object'
  }
  return SENSITIVE_FIELD_NAMES.has(normalized) ? 'object' : undefined
}

export function schemaContainsSecretField(schema: JsonSchemaNode, fieldName = ''): boolean {
  if (secretFieldRepresentation(schema, fieldName)) return true
  if (schema.properties && Object.entries(schema.properties).some(([key, child]) =>
    schemaContainsSecretField(child, key))) return true
  return Boolean(schema.items && schemaContainsSecretField(schema.items, fieldName))
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
    return Object.fromEntries(Object.entries(schema.properties || {})
      .filter(([key, node]) => schema.required?.includes(key) || node.default !== undefined)
      .map(([key, node]) => [key, schemaDefault(node)]))
  }
  if (schema.type === 'array') return []
  if (schema.type === 'boolean') return false
  if (schema.type === 'integer' || schema.type === 'number') return undefined
  return schema.enum?.[0] ?? ''
}

export function mergeSchemaDefaults(schema: JsonSchemaNode, value: unknown): unknown {
  if (schema.type === 'object' || schema.properties) {
    const input = value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
    return Object.fromEntries(Object.entries(schema.properties || {})
      .filter(([key, node]) => input[key] !== undefined
        || schema.required?.includes(key) === true
        || node.default !== undefined)
      .map(([key, node]) => [
        key,
        input[key] === undefined ? schemaDefault(node) : mergeSchemaDefaults(node, input[key])
      ]))
  }
  if (schema.type === 'array') {
    return Array.isArray(value) ? value.map(item => mergeSchemaDefaults(schema.items || {}, item)) : []
  }
  return value
}

/** Remove values the current Schema cannot submit while preserving false, 0 and explicit empty arrays. */
export function pruneSchemaValue(schema: JsonSchemaNode, value: unknown, required = false): unknown {
  if (value === undefined || value === null) return undefined
  if (schema.type === 'object' || schema.properties) {
    if (typeof value !== 'object' || Array.isArray(value)) return undefined
    const input = value as Record<string, unknown>
    const output: Record<string, unknown> = {}
    for (const [key, childSchema] of Object.entries(schema.properties || {})) {
      if (!schemaFieldVisible(childSchema, input, key, schema)) continue
      const child = pruneSchemaValue(childSchema, input[key], schema.required?.includes(key) === true)
      if (child !== undefined) output[key] = child
    }
    for (const [key, childValue] of Object.entries(input)) {
      if (schema.properties?.[key]) continue
      const additional = schema.additionalProperties
      if (additional === false) continue
      const child = additional && typeof additional === 'object'
        ? pruneSchemaValue(additional, childValue)
        : childValue
      if (child !== undefined) output[key] = child
    }
    return Object.keys(output).length > 0 || required || schema.default !== undefined ? output : undefined
  }
  if (schema.type === 'array') {
    if (!Array.isArray(value)) return undefined
    return value.map(item => pruneSchemaValue(schema.items || {}, item)).filter(item => item !== undefined)
  }
  if (typeof value === 'string' && value.trim() === '' && !required && schema.default === undefined) {
    return undefined
  }
  return value
}

export function orderedSchemaProperties(schema: JsonSchemaNode): Array<[string, JsonSchemaNode]> {
  return Object.entries(schema.properties || {}).sort((left, right) =>
    (left[1]['x-ui-order'] ?? Number.MAX_SAFE_INTEGER) - (right[1]['x-ui-order'] ?? Number.MAX_SAFE_INTEGER)
  )
}

/** Evaluates only a small declarative visibility whitelist; it never executes schema content. */
export function schemaFieldVisible(
  schema: JsonSchemaNode,
  parentValue: unknown,
  fieldName = '',
  parentSchema?: JsonSchemaNode
): boolean {
  const condition = schema['x-ui-visible-if']
  if (condition !== undefined && !visibilityConditionMatches(condition, parentValue)) return false
  return standardConditionFieldVisible(fieldName, parentSchema, parentValue)
}

export function schemaFieldRequired(
  fieldName: string,
  parentSchema: JsonSchemaNode | undefined,
  parentValue: unknown
): boolean {
  if (!parentSchema || !fieldName) return false
  if (parentSchema.required?.includes(fieldName)) return true
  return conditionalBranches(parentSchema).some(({ condition, then, otherwise }) => {
    const branch = conditionMatches(condition, parentValue) ? then : otherwise
    return Array.isArray(branch?.required) && branch.required.includes(fieldName)
  })
}

function visibilityConditionMatches(condition: Record<string, unknown>, parentValue: unknown): boolean {
  if (!condition || typeof condition !== 'object' || Array.isArray(condition)) return false
  if (!parentValue || typeof parentValue !== 'object' || Array.isArray(parentValue)) return false
  const parent = parentValue as Record<string, unknown>
  const keys = Object.keys(condition)
  const structured = keys.some(key => ['field', 'equals', 'notEquals', 'in', 'present'].includes(key))
  if (!structured) {
    if (keys.length !== 1 || !safeVisibilityField(keys[0])) return false
    return jsonEqual(parent[keys[0]], condition[keys[0]])
  }
  if (keys.some(key => !['field', 'equals', 'notEquals', 'in', 'present'].includes(key))) return false
  const field = condition.field
  if (typeof field !== 'string' || !safeVisibilityField(field)) return false
  const actual = parent[field]
  if ('present' in condition && typeof condition.present !== 'boolean') return false
  if (condition.present === true && (actual === undefined || actual === null)) return false
  if (condition.present === false && actual !== undefined && actual !== null) return false
  if ('equals' in condition && !jsonEqual(actual, condition.equals)) return false
  if ('notEquals' in condition && jsonEqual(actual, condition.notEquals)) return false
  if ('in' in condition) {
    if (!Array.isArray(condition.in) || !condition.in.some(value => jsonEqual(value, actual))) return false
  }
  return true
}

function standardConditionFieldVisible(
  fieldName: string,
  parentSchema: JsonSchemaNode | undefined,
  parentValue: unknown
): boolean {
  if (!parentSchema || !fieldName) return true
  for (const { condition, then, otherwise } of conditionalBranches(parentSchema)) {
    const matched = conditionMatches(condition, parentValue)
    const branch = matched ? then : otherwise
    if (!branch) continue
    if (forbiddenFields(branch).has(fieldName)) return false
  }
  return true
}

function conditionalBranches(schema: JsonSchemaNode): Array<{
  condition: JsonSchemaNode
  then?: JsonSchemaNode
  otherwise?: JsonSchemaNode
}> {
  const branches: Array<{ condition: JsonSchemaNode; then?: JsonSchemaNode; otherwise?: JsonSchemaNode }> = []
  if (schema.if) branches.push({ condition: schema.if, then: schema.then, otherwise: schema.else })
  for (const branch of schema.allOf || []) {
    if (branch.if) branches.push({ condition: branch.if, then: branch.then, otherwise: branch.else })
  }
  return branches
}

function forbiddenFields(schema: JsonSchemaNode): Set<string> {
  const result = new Set<string>()
  const not = schema.not
  for (const field of not?.required || []) result.add(field)
  const candidates = [not?.anyOf, not?.oneOf].filter(Array.isArray).flat() as JsonSchemaNode[]
  for (const candidate of candidates) {
    for (const field of candidate.required || []) result.add(field)
  }
  return result
}

function conditionMatches(condition: JsonSchemaNode, value: unknown): boolean {
  if (!condition || typeof condition !== 'object' || Array.isArray(condition)) return false
  if (condition.type && !matchesSchemaType(condition.type, value)) return false
  if (Object.prototype.hasOwnProperty.call(condition, 'const')
    && !jsonEqual(value, condition.const)) return false
  if (condition.enum && (!Array.isArray(condition.enum)
    || !condition.enum.some(item => jsonEqual(item, value)))) return false
  if (condition.allOf && !condition.allOf.every(item => conditionMatches(item, value))) return false
  if (condition.anyOf && !condition.anyOf.some(item => conditionMatches(item, value))) return false
  if (condition.oneOf && condition.oneOf.filter(item => conditionMatches(item, value)).length !== 1) return false
  if (condition.not && conditionMatches(condition.not, value)) return false
  const objectValue = value !== null && typeof value === 'object' && !Array.isArray(value)
  const parent = value as Record<string, unknown>
  if (condition.required) {
    if (!objectValue || !condition.required.every(field => parent[field] !== undefined && parent[field] !== null)) return false
  }
  if (condition.properties) {
    if (!objectValue) return false
    for (const [field, expected] of Object.entries(condition.properties)) {
      const actual = parent[field]
      if (actual === undefined) return false
      if (!conditionMatches(expected, actual)) return false
    }
  }
  return true
}

function matchesSchemaType(type: JsonSchemaNode['type'], value: unknown): boolean {
  if (!type) return true
  if (type === 'string') return typeof value === 'string'
  if (type === 'integer') return typeof value === 'number' && Number.isInteger(value)
  if (type === 'number') return typeof value === 'number' && Number.isFinite(value)
  if (type === 'boolean') return typeof value === 'boolean'
  if (type === 'object') return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
  if (type === 'array') return Array.isArray(value)
  return false
}

function jsonEqual(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) return true
  if (Array.isArray(left) && Array.isArray(right)) {
    return left.length === right.length && left.every((value, index) => jsonEqual(value, right[index]))
  }
  if (left && right && typeof left === 'object' && typeof right === 'object') {
    const leftRecord = left as Record<string, unknown>
    const rightRecord = right as Record<string, unknown>
    const keys = Object.keys(leftRecord)
    return keys.length === Object.keys(rightRecord).length
      && keys.every(key => Object.prototype.hasOwnProperty.call(rightRecord, key)
        && jsonEqual(leftRecord[key], rightRecord[key]))
  }
  return false
}

function safeVisibilityField(value: string): boolean {
  return /^[A-Za-z_][A-Za-z0-9_-]{0,127}$/.test(value)
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

import { describe, expect, it } from 'vitest'
import {
  canActivate,
  canDisable,
  canStage,
  canVerify,
  disablePluginConfirmation,
  diffConnectorPipelines,
  mergeSchemaDefaults,
  normalizePipelineOrder,
  normalizedBindingCount,
  orderedSchemaProperties,
  parseJsonDocument,
  pruneSchemaValue,
  readSecretReference,
  schemaDefault,
  schemaFieldVisible,
  schemaFieldRequired,
  schemaContainsSecretField,
  secretFieldRepresentation,
  writeSecretReference
} from '../connector'
import type { ConnectorPipelineStep, JsonSchemaNode } from '@/types'

const step = (stageKey: string, order = 0): ConnectorPipelineStep => ({
  stageKey,
  capability: 'REQUEST_BUILDER',
  pluginId: 'demo',
  pluginVersion: '1.0.0',
  order,
  enabled: true,
  config: {}
})

describe('connector helpers', () => {
  it('round-trips string x-secret-ref fields without violating the Schema type', () => {
    const schema: JsonSchemaNode = { type: 'string', 'x-secret-ref': true }
    expect(secretFieldRepresentation(schema, 'signingMaterial')).toBe('string')
    const submitted = writeSecretReference('string', 'vendor.signing-key')
    expect(submitted).toBe('vendor.signing-key')
    expect(readSecretReference(submitted)).toBe('vendor.signing-key')
  })

  it('round-trips x-sensitive fields as an object and never as plaintext', () => {
    const schema: JsonSchemaNode = { type: 'object', 'x-sensitive': true }
    expect(secretFieldRepresentation(schema, 'clientCredential')).toBe('object')
    const submitted = writeSecretReference('object', 'vendor.client-secret')
    expect(submitted).toEqual({ secretRef: 'vendor.client-secret' })
    expect(readSecretReference(submitted)).toBe('vendor.client-secret')
  })

  it('parses valid JSON and returns fallback for invalid JSON', () => {
    expect(parseJsonDocument('{"a":1}', {})).toEqual({ a: 1 })
    expect(parseJsonDocument('{', { safe: true })).toEqual({ safe: true })
  })

  it('creates defaults for supported schema types', () => {
    const schema: JsonSchemaNode = { type: 'object', properties: {
      text: { type: 'string' }, count: { type: 'integer' }, ratio: { type: 'number' },
      enabled: { type: 'boolean' }, names: { type: 'array', items: { type: 'string' } },
      mode: { type: 'string', enum: ['A', 'B'] }, nested: { type: 'object', properties: { active: { type: 'boolean' } } }
    } }
    expect(schemaDefault(schema)).toEqual({})
  })

  it('merges stored values with newly introduced schema fields', () => {
    const schema: JsonSchemaNode = { type: 'object', required: ['url'], properties: { url: { type: 'string' }, enabled: { type: 'boolean' }, retries: { type: 'integer', default: 3 } } }
    expect(mergeSchemaDefaults(schema, {})).toEqual({ url: '', retries: 3 })
    expect(mergeSchemaDefaults(schema, { url: 'https://a.test' })).toEqual({ url: 'https://a.test', retries: 3 })
  })

  it('does not classify tokenEndpoint as a secret and prunes hidden/optional fields', () => {
    expect(secretFieldRepresentation({ type: 'string', 'x-secret-ref': true }, 'tokenEndpoint')).toBe('string')
    expect(secretFieldRepresentation({ type: 'string' }, 'tokenEndpoint')).toBeUndefined()
    expect(secretFieldRepresentation({ type: 'string' }, 'accessToken')).toBe('object')
    expect(schemaContainsSecretField({ type: 'object', properties: {
      endpoint: { type: 'string' }, credential: { type: 'string' }
    } })).toBe(true)
    const schema: JsonSchemaNode = {
      type: 'object',
      properties: {
        mode: { type: 'string' },
        endpoint: { type: 'string', 'x-ui-visible-if': { field: 'mode', equals: 'CUSTOM' } },
        optional: { type: 'string' },
        enabled: { type: 'boolean' },
        values: { type: 'array', items: { type: 'string' } }
      }
    }
    expect(pruneSchemaValue(schema, {
      mode: 'DEFAULT', endpoint: 'https://hidden.test', optional: '', enabled: false, values: []
    })).toEqual({ mode: 'DEFAULT', enabled: false, values: [] })
  })

  it('orders properties using x-ui-order', () => {
    const schema: JsonSchemaNode = { properties: { late: { 'x-ui-order': 2 }, early: { 'x-ui-order': 1 }, last: {} } }
    expect(orderedSchemaProperties(schema).map(([key]) => key)).toEqual(['early', 'late', 'last'])
  })

  it('evaluates only whitelisted declarative x-ui-visible-if conditions', () => {
    expect(schemaFieldVisible({ 'x-ui-visible-if': { type: 'BEARER' } }, { type: 'BEARER' })).toBe(true)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { type: 'BASIC' } }, { type: 'BEARER' })).toBe(false)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { field: 'mode', equals: 'A' } }, { mode: 'A' })).toBe(true)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { field: 'mode', notEquals: 'B' } }, { mode: 'A' })).toBe(true)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { field: 'mode', in: ['A', 'C'] } }, { mode: 'A' })).toBe(true)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { field: 'token', present: true } }, { token: 'ref' })).toBe(true)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { field: 'token', present: false } }, {})).toBe(true)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { field: 'mode', equals: 'A', script: 'alert(1)' } }, { mode: 'A' })).toBe(false)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { '__proto__.polluted': true } }, {})).toBe(false)
  })

  it('matches primitive JSON Schema conditions and direct not-required exclusions', () => {
    const schema: JsonSchemaNode = {
      type: 'object',
      properties: { mode: { type: 'string' }, endpoint: { type: 'string' } },
      if: { properties: { mode: { const: 'CUSTOM' } } },
      then: { not: { required: ['endpoint'] } }
    }
    expect(schemaFieldVisible(schema.properties!.endpoint!, { mode: 'CUSTOM' }, 'endpoint', schema)).toBe(false)
    expect(schemaFieldVisible(schema.properties!.endpoint!, { mode: 'DEFAULT' }, 'endpoint', schema)).toBe(true)
    expect(schemaFieldVisible({ 'x-ui-visible-if': { field: 'enabled', equals: false } }, { enabled: false })).toBe(true)
  })

  it('keeps the selected branch of standard conditional schemas aligned with the form', () => {
    const schema: JsonSchemaNode = {
      type: 'object',
      properties: {
        mode: { type: 'string' },
        endpoint: { type: 'string' },
        tokenEndpoint: { type: 'string' }
      },
      if: { properties: { mode: { const: 'single-http' } } },
      then: { required: ['endpoint'], not: { anyOf: [{ required: ['tokenEndpoint'] }] } },
      else: { required: ['tokenEndpoint'], not: { anyOf: [{ required: ['endpoint'] }] } }
    }
    expect(schemaFieldVisible(schema.properties!.endpoint!, { mode: 'single-http' }, 'endpoint', schema)).toBe(true)
    expect(schemaFieldVisible(schema.properties!.endpoint!, { mode: 'token-business' }, 'endpoint', schema)).toBe(false)
    expect(schemaFieldRequired('endpoint', schema, { mode: 'single-http' })).toBe(true)
    expect(pruneSchemaValue(schema, {
      mode: 'token-business', endpoint: 'https://hidden.test', tokenEndpoint: 'https://token.test'
    })).toEqual({ mode: 'token-business', tokenEndpoint: 'https://token.test' })
  })

  it('preserves declared additional properties instead of silently dropping them', () => {
    const schema: JsonSchemaNode = {
      type: 'object', properties: { known: { type: 'string' } }, additionalProperties: true
    }
    expect(pruneSchemaValue(schema, { known: 'value', extension: false })).toEqual({
      known: 'value', extension: false
    })
  })

  it('reports added, removed and changed stages', () => {
    const changed = { ...step('keep'), pluginVersion: '2.0.0' }
    const diff = diffConnectorPipelines([step('keep'), step('gone')], [changed, step('new')])
    expect(diff.map(item => [item.stageKey, item.change])).toEqual([
      ['keep', 'CHANGED'], ['gone', 'REMOVED'], ['new', 'ADDED']
    ])
  })

  it('normalizes order after moves and deletions', () => {
    expect(normalizePipelineOrder([step('b', 8), step('a', 4)]).map(item => item.order)).toEqual([0, 1])
  })

  it('enforces version lifecycle actions', () => {
    expect(canVerify('VERIFIED')).toBe(true)
    expect(canVerify('ACTIVE')).toBe(false)
    expect(canStage('STAGING_FAILED')).toBe(true)
    expect(canStage('ACTIVE')).toBe(false)
    expect(canActivate('STAGING', true)).toBe(true)
    expect(canActivate('STAGING', false)).toBe(false)
    expect(canDisable('ACTIVE')).toBe(true)
    expect(canDisable('STAGING')).toBe(false)
  })

  it('explains disable conflicts and keeps binding counts safe for display', () => {
    expect(disablePluginConfirmation('vendor-demo', '1.2.0')).toContain('活动绑定会被后端 409 拒绝')
    expect(disablePluginConfirmation('vendor-demo', '1.2.0')).toContain('历史版本仍保留')
    expect(normalizedBindingCount(3)).toBe(3)
    expect(normalizedBindingCount(undefined)).toBe(0)
    expect(normalizedBindingCount(-1)).toBe(0)
  })
})

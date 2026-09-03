import { describe, expect, it } from 'vitest'
import {
  hasNavigationPermission,
  navigationManifest,
  navigationPagePermissions,
  resolveFirstAuthorizedRoute
} from '../navigation'

describe('navigation manifest', () => {
  it('uses the explicit admin capability instead of a role name', () => {
    const dashboard = navigationManifest[0]
    expect(hasNavigationPermission(dashboard, ['admin'])).toBe(false)
    expect(hasNavigationPermission(dashboard, ['system:admin'])).toBe(true)
  })

  it('resolves requested and first authorized pages without a forbidden detour', () => {
    expect(resolveFirstAuthorizedRoute(['api-permission:view'], '/dashboard')).toBe('/api-permission')
    expect(resolveFirstAuthorizedRoute(['api-permission:view'], '/api-permission')).toBe('/api-permission')
    expect(resolveFirstAuthorizedRoute(['api-permission:apply'], '/data-test')).toBe('/data-test')
    expect(resolveFirstAuthorizedRoute(['caller:view'], '/caller/7/products')).toBe('/caller/7/products')
    expect(resolveFirstAuthorizedRoute([], '/dashboard')).toBe('/profile')
  })

  it('lets grant and process observers enter the shared approval page', () => {
    const approval = navigationManifest.find(item => item.path === '/api-permission')!
    expect(navigationPagePermissions('/api-permission')).toEqual([
      'api-permission:view',
      'api-permission:approve',
      'api-permission:grant-view',
      'api-permission:process-view'
    ])
    expect(hasNavigationPermission(approval, ['api-permission:grant-view'])).toBe(true)
    expect(hasNavigationPermission(approval, ['api-permission:process-view'])).toBe(true)
    expect(resolveFirstAuthorizedRoute(['api-permission:process-view'], '/dashboard')).toBe('/api-permission')
  })
})

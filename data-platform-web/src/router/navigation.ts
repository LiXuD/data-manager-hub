export interface NavigationItem {
  path: string
  title: string
  icon?: string
  pagePermission?: string
  pagePermissions?: readonly string[]
  children?: readonly NavigationItem[]
}

/** Single page-level navigation contract shared by the menu and router. */
export const navigationManifest: readonly NavigationItem[] = [
  { path: '/dashboard', title: '数据概览', icon: 'dashboard', pagePermission: 'dashboard:view' },
  {
    path: 'system',
    title: '系统管理',
    icon: 'setting',
    children: [
      { path: '/tenant', title: '租户管理', pagePermission: 'tenant:view' },
      { path: '/user', title: '用户管理', pagePermission: 'user:view' },
      { path: '/role', title: '角色管理', pagePermission: 'role:view' }
    ]
  },
  {
    path: 'business',
    title: '业务管理',
    icon: 'component',
    children: [
      { path: '/vendor', title: '厂商管理', pagePermission: 'vendor:view' },
      { path: '/connector-plugin', title: '连接器插件', pagePermission: 'connector-plugin:view' },
      { path: '/connector-migration', title: '厂商连接器迁移', pagePermission: 'connector-plugin:view' },
      { path: '/connector-diagnostics', title: '连接器运行诊断', pagePermission: 'system:admin' },
      { path: '/caller', title: '内部系统管理', pagePermission: 'caller:view' },
      { path: '/call-scene', title: '场景管理', pagePermission: 'call-scene:view' },
      { path: '/datatype', title: '数据类型', pagePermission: 'datatype:view' },
      { path: '/interface', title: '接口管理', pagePermission: 'interface:view' }
    ]
  },
  { path: '/call', title: '调用记录', icon: 'connection', pagePermission: 'call:view' },
  {
    path: '/api-permission',
    title: '接口权限审批',
    icon: 'document',
    pagePermissions: ['api-permission:view', 'api-permission:approve', 'api-permission:grant-view', 'api-permission:process-view']
  },
  { path: '/billing', title: '计费管理', icon: 'wallet', pagePermission: 'billing:view' },
  { path: '/monitor', title: '监控告警', icon: 'alarm', pagePermission: 'monitor:view' },
  { path: '/config', title: '配置中心', icon: 'config', pagePermission: 'config:view' },
  { path: '/graylog', title: '灰度发布', icon: 'release', pagePermission: 'graylog:view' },
  { path: '/audit', title: '操作日志', icon: 'document', pagePermission: 'audit:view' },
  {
    path: '/data-test',
    title: '数据查询测试',
    icon: 'play',
    pagePermissions: ['api-permission:view', 'api-permission:apply']
  }
]

export function flattenNavigation(items: readonly NavigationItem[] = navigationManifest): NavigationItem[] {
  return items.flatMap(item => item.children ? flattenNavigation(item.children) : [item])
}

export function hasNavigationPermission(item: NavigationItem, permissions: readonly string[]): boolean {
  if (permissions.includes('system:admin')) return true
  if (item.pagePermissions?.some(permission => permissions.includes(permission))) return true
  if (item.pagePermission) return permissions.includes(item.pagePermission)
  return Boolean(item.children?.some(child => hasNavigationPermission(child, permissions)))
}

export function navigationPagePermission(path: string): string {
  return navigationPagePermissions(path)[0]
}

export function navigationPagePermissions(path: string): readonly string[] {
  const item = flattenNavigation().find(candidate => candidate.path === path)
  if (!item) throw new Error(`Navigation page permission is not declared for ${path}`)
  const permissions = item.pagePermissions || (item.pagePermission ? [item.pagePermission] : [])
  if (!permissions.length) throw new Error(`Navigation page permission is not declared for ${path}`)
  return permissions
}

export function resolveFirstAuthorizedRoute(
  permissions: readonly string[],
  requestedPath?: string
): string {
  const pages = flattenNavigation()
  const requested = requestedPath?.split('?')[0]
  const requestedPage = requested && pages.find(item =>
    requested === item.path || requested.startsWith(`${item.path}/`))
  if (requestedPage && hasNavigationPermission(requestedPage, permissions)) {
    return requested
  }
  return pages.find(item => hasNavigationPermission(item, permissions))?.path || '/profile'
}

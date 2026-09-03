import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getProfile } from '@/api/auth'
import { navigationPagePermission, navigationPagePermissions, resolveFirstAuthorizedRoute } from './navigation'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false, trickleSpeed: 100 })

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/views/layout/index.vue'),
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据概览', permissions: [navigationPagePermission('/dashboard')] }
      },
      {
        path: '/tenant',
        name: 'Tenant',
        component: () => import('@/views/tenant/index.vue'),
        meta: { title: '租户管理', permissions: [navigationPagePermission('/tenant')] }
      },
      {
        path: '/user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', permissions: [navigationPagePermission('/user')] }
      },
      {
        path: '/role',
        name: 'Role',
        component: () => import('@/views/role/index.vue'),
        meta: { title: '角色管理', permissions: [navigationPagePermission('/role')] }
      },
      {
        path: '/vendor',
        name: 'Vendor',
        component: () => import('@/views/vendor/index.vue'),
        meta: { title: '厂商管理', permissions: [navigationPagePermission('/vendor')] }
      },
      {
        path: '/connector-plugin',
        name: 'ConnectorPlugin',
        component: () => import('@/views/connector-plugin/index.vue'),
        meta: {
          title: '连接器插件',
          permissions: [navigationPagePermission('/connector-plugin')]
        }
      },
      {
        path: '/connector-migration',
        name: 'ConnectorMigration',
        component: () => import('@/views/connector-migration/index.vue'),
        meta: {
          title: '厂商连接器迁移',
          permissions: [navigationPagePermission('/connector-migration')]
        }
      },
      {
        path: '/connector-diagnostics',
        name: 'ConnectorDiagnostics',
        component: () => import('@/views/connector-diagnostics/index.vue'),
        meta: {
          title: '连接器运行诊断',
          permissions: [navigationPagePermission('/connector-diagnostics')]
        }
      },
      {
        path: '/caller',
        name: 'Caller',
        component: () => import('@/views/caller/index.vue'),
        meta: { title: '内部系统管理', permissions: [navigationPagePermission('/caller')] }
      },
      {
        path: '/caller/:callerId/products',
        name: 'CallerProducts',
        component: () => import('@/views/caller/products.vue'),
        meta: { title: '内部系统产品管理', permissions: [navigationPagePermission('/caller')] }
      },
      {
        path: '/api-permission',
        name: 'ApiPermission',
        component: () => import('@/views/api-permission/index.vue'),
        meta: {
          title: '接口权限审批',
          permissions: navigationPagePermissions('/api-permission')
        }
      },
      {
        path: '/datatype',
        name: 'DataType',
        component: () => import('@/views/datatype/index.vue'),
        meta: { title: '数据类型', permissions: [navigationPagePermission('/datatype')] }
      },
      {
        path: '/interface',
        name: 'Interface',
        component: () => import('@/views/interface/index.vue'),
        meta: { title: '接口管理', permissions: [navigationPagePermission('/interface')] }
      },
      {
        path: '/interface/:id/docs',
        name: 'InterfaceDocs',
        component: () => import('@/views/interface/docs.vue'),
        meta: { title: '接口文档', permissions: [navigationPagePermission('/interface')] }
      },
      {
        path: '/call',
        name: 'Call',
        component: () => import('@/views/call/index.vue'),
        meta: { title: '调用记录', permissions: [navigationPagePermission('/call')] }
      },
      {
        path: '/call-scene',
        name: 'CallScene',
        component: () => import('@/views/call-scene/index.vue'),
        meta: { title: '场景管理', permissions: [navigationPagePermission('/call-scene')] }
      },
      {
        path: '/billing',
        name: 'Billing',
        component: () => import('@/views/billing/index.vue'),
        meta: { title: '计费管理', permissions: [navigationPagePermission('/billing')] }
      },
      {
        path: '/monitor',
        name: 'Monitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: '监控告警', permissions: [navigationPagePermission('/monitor')] }
      },
      {
        path: '/config',
        name: 'Config',
        component: () => import('@/views/config/index.vue'),
        meta: { title: '配置中心', permissions: [navigationPagePermission('/config')] }
      },
      {
        path: '/graylog',
        name: 'Graylog',
        component: () => import('@/views/graylog/index.vue'),
        meta: { title: '灰度发布', permissions: [navigationPagePermission('/graylog')] }
      },
      {
        path: '/audit',
        name: 'Audit',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: '操作日志', permissions: [navigationPagePermission('/audit')] }
      },
      {
        path: '/data-test',
        name: 'DataTest',
        component: () => import('@/views/data-test/index.vue'),
        meta: { title: '数据查询测试', permissions: navigationPagePermissions('/data-test') }
      },
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心' }
      }
    ]
  },
  {
    path: '/openapi-docs',
    name: 'CallerOpenApiDocs',
    component: () => import('@/views/openapi-docs/index.vue'),
    meta: { title: '内部接口文档', public: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/not-found/index.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

let syncedToken = ''

const syncCurrentUser = async (userStore: ReturnType<typeof useUserStore>): Promise<boolean> => {
  const currentToken = userStore.token
  if (!userStore.isLoggedIn || !currentToken) {
    syncedToken = ''
    return false
  }
  if (currentToken === syncedToken) return true

  try {
    const response = await getProfile()
    if (!userStore.isLoggedIn || userStore.token !== currentToken) {
      syncedToken = ''
      return false
    }
    const data = response.data
    if (!data || data.userId === undefined || !data.username) {
      throw new Error('用户资料响应无效')
    }
    userStore.setUserInfo({
      id: String(data.userId),
      username: data.username,
      nickname: data.nickname || data.username,
      email: data.email,
      phone: data.phone,
      roles: data.roles || [],
      tenantId: data.tenantId,
      tenantName: data.tenantName,
      lastLoginTime: data.lastLoginTime,
      permissions: data.permissions || []
    })
    syncedToken = currentToken
    return true
  } catch {
    if (userStore.token === currentToken) {
      userStore.logout()
    }
    syncedToken = ''
    console.warn('刷新用户权限失败')
    return false
  }
}

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  const userStore = useUserStore()

  if (to.path === '/login') {
    if (userStore.isLoggedIn) {
      if (!await syncCurrentUser(userStore)) {
        next()
        return
      }
      next(resolveFirstAuthorizedRoute(userStore.permissions, typeof to.query.redirect === 'string' ? to.query.redirect : undefined))
    } else {
      syncedToken = ''
      next()
    }
  } else if (to.meta.public) {
    next()
  } else {
    if (!userStore.isLoggedIn) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
    if (!await syncCurrentUser(userStore)) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
    if (to.path === '/') {
      next(resolveFirstAuthorizedRoute(userStore.permissions))
      return
    }
    const requiredPermissions = to.meta.permissions as string[] | undefined
    if (requiredPermissions?.length
      && !userStore.hasPermission('system:admin')
      && !requiredPermissions.some(permission => userStore.hasPermission(permission))) {
      next({ path: '/profile', query: { forbidden: '1' } })
    } else {
      next()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router

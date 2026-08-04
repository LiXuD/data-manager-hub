import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
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
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据概览', permissions: ['dashboard:view'] }
      },
      {
        path: '/tenant',
        name: 'Tenant',
        component: () => import('@/views/tenant/index.vue'),
        meta: { title: '租户管理', permissions: ['tenant:view'] }
      },
      {
        path: '/user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', permissions: ['user:view'] }
      },
      {
        path: '/role',
        name: 'Role',
        component: () => import('@/views/role/index.vue'),
        meta: { title: '角色管理', permissions: ['role:view'] }
      },
      {
        path: '/vendor',
        name: 'Vendor',
        component: () => import('@/views/vendor/index.vue'),
        meta: { title: '厂商管理', permissions: ['vendor:view'] }
      },
      {
        path: '/connector-plugin',
        name: 'ConnectorPlugin',
        component: () => import('@/views/connector-plugin/index.vue'),
        meta: {
          title: '连接器插件',
          permissions: [
            'connector-plugin:view',
            'connector-plugin:import',
            'connector-plugin:verify',
            'connector-plugin:activate',
            'connector-plugin:disable'
          ]
        }
      },
      {
        path: '/caller',
        name: 'Caller',
        component: () => import('@/views/caller/index.vue'),
        meta: { title: '内部系统管理', permissions: ['caller:view'] }
      },
      {
        path: '/caller/:callerId/products',
        name: 'CallerProducts',
        component: () => import('@/views/caller/products.vue'),
        meta: { title: '内部系统产品管理', permissions: ['caller:view'] }
      },
      {
        path: '/api-permission',
        name: 'ApiPermission',
        component: () => import('@/views/api-permission/index.vue'),
        meta: {
          title: '接口权限审批',
          permissions: [
            'api-permission:view',
            'api-permission:approve',
            'api-permission:grant-view',
            'api-permission:process-view',
            'api-permission:emergency-grant'
          ]
        }
      },
      {
        path: '/datatype',
        name: 'DataType',
        component: () => import('@/views/datatype/index.vue'),
        meta: { title: '数据类型', permissions: ['datatype:view'] }
      },
      {
        path: '/interface',
        name: 'Interface',
        component: () => import('@/views/interface/index.vue'),
        meta: { title: '接口管理', permissions: ['interface:view'] }
      },
      {
        path: '/interface/:id/docs',
        name: 'InterfaceDocs',
        component: () => import('@/views/interface/docs.vue'),
        meta: { title: '接口文档', permissions: ['interface:view'] }
      },
      {
        path: '/call',
        name: 'Call',
        component: () => import('@/views/call/index.vue'),
        meta: { title: '调用记录', permissions: ['call:view'] }
      },
      {
        path: '/call-scene',
        name: 'CallScene',
        component: () => import('@/views/call-scene/index.vue'),
        meta: { title: '场景管理', permissions: ['call-scene:view'] }
      },
      {
        path: '/billing',
        name: 'Billing',
        component: () => import('@/views/billing/index.vue'),
        meta: { title: '计费管理', permissions: ['billing:view'] }
      },
      {
        path: '/monitor',
        name: 'Monitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: '监控告警', permissions: ['monitor:view'] }
      },
      {
        path: '/config',
        name: 'Config',
        component: () => import('@/views/config/index.vue'),
        meta: { title: '配置中心', permissions: ['config:view'] }
      },
      {
        path: '/graylog',
        name: 'Graylog',
        component: () => import('@/views/graylog/index.vue'),
        meta: { title: '灰度发布', permissions: ['graylog:view'] }
      },
      {
        path: '/audit',
        name: 'Audit',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: '操作日志', permissions: ['audit:view'] }
      },
      {
        path: '/data-test',
        name: 'DataTest',
        component: () => import('@/views/data-test/index.vue'),
        meta: { title: '数据查询测试' }
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

// 路由守卫
router.beforeEach((to, _from, next) => {
  NProgress.start()
  const userStore = useUserStore()

  if (to.path === '/login') {
    if (userStore.isLoggedIn) {
      next('/dashboard')
    } else {
      next()
    }
  } else if (to.meta.public) {
    next()
  } else {
    if (!userStore.isLoggedIn) {
      next('/login')
      return
    }
    const requiredPermissions = to.meta.permissions as string[] | undefined
    if (requiredPermissions?.length
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

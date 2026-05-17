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
        meta: { title: '数据概览' }
      },
      {
        path: '/tenant',
        name: 'Tenant',
        component: () => import('@/views/tenant/index.vue'),
        meta: { title: '租户管理' }
      },
      {
        path: '/user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: '/role',
        name: 'Role',
        component: () => import('@/views/role/index.vue'),
        meta: { title: '角色管理' }
      },
      {
        path: '/vendor',
        name: 'Vendor',
        component: () => import('@/views/vendor/index.vue'),
        meta: { title: '厂商管理' }
      },
      {
        path: '/caller',
        name: 'Caller',
        component: () => import('@/views/caller/index.vue'),
        meta: { title: '调用方管理' }
      },
      {
        path: '/datatype',
        name: 'DataType',
        component: () => import('@/views/datatype/index.vue'),
        meta: { title: '数据类型' }
      },
      {
        path: '/interface',
        name: 'Interface',
        component: () => import('@/views/interface/index.vue'),
        meta: { title: '接口管理' }
      },
      {
        path: '/call',
        name: 'Call',
        component: () => import('@/views/call/index.vue'),
        meta: { title: '调用记录' }
      },
      {
        path: '/call-scene',
        name: 'CallScene',
        component: () => import('@/views/call-scene/index.vue'),
        meta: { title: '场景字典' }
      },
      {
        path: '/billing',
        name: 'Billing',
        component: () => import('@/views/billing/index.vue'),
        meta: { title: '计费管理' }
      },
      {
        path: '/monitor',
        name: 'Monitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: '监控告警' }
      },
      {
        path: '/config',
        name: 'Config',
        component: () => import('@/views/config/index.vue'),
        meta: { title: '配置中心' }
      },
      {
        path: '/graylog',
        name: 'Graylog',
        component: () => import('@/views/graylog/index.vue'),
        meta: { title: '灰度发布' }
      },
      {
        path: '/audit',
        name: 'Audit',
        component: () => import('@/views/audit/index.vue'),
        meta: { title: '操作日志' }
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
  } else {
    if (userStore.isLoggedIn) {
      next()
    } else {
      next('/login')
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router

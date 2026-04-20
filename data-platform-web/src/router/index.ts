import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  
  if (to.path === '/login') {
    next()
  } else {
    if (token) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
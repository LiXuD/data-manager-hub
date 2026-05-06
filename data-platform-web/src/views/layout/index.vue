<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { ref, computed, onMounted, onUnmounted } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElConfigProvider, ElMenu, ElMenuItem, ElSubMenu, ElDropdown, ElDropdownItem, ElDropdownMenu, ElBadge } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { STORAGE_KEYS, THEME_MODE } from '@/constants'
import { applyTheme, getStoredTheme } from '@/composables/useTheme'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isCollapse = ref(false)

const handleStorageChange = (e: StorageEvent) => {
  if (e.key === STORAGE_KEYS.THEME) {
    applyTheme((e.newValue || THEME_MODE.DARK) as typeof THEME_MODE[keyof typeof THEME_MODE])
  }
}

const handleThemeChange = () => {
  applyTheme(getStoredTheme())
}

onMounted(() => {
  applyTheme(getStoredTheme())
  window.addEventListener('storage', handleStorageChange)
  window.addEventListener('theme-change', handleThemeChange)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener('theme-change', handleThemeChange)
})

const activeMenu = computed(() => route.path)

// 菜单配置
const menuItems = [
  {
    path: '/dashboard',
    title: '数据概览',
    icon: 'dashboard'
  },
  {
    path: 'system',
    title: '系统管理',
    icon: 'setting',
    children: [
      { path: '/tenant', title: '租户管理' },
      { path: '/user', title: '用户管理' },
      { path: '/role', title: '角色管理' }
    ]
  },
  {
    path: 'business',
    title: '业务管理',
    icon: 'component',
    children: [
      { path: '/vendor', title: '厂商管理' },
      { path: '/caller', title: '调用方管理' },
      { path: '/datatype', title: '数据类型' },
      { path: '/interface', title: '接口管理' }
    ]
  },
  {
    path: '/call',
    title: '调用记录',
    icon: 'connection'
  },
  {
    path: '/billing',
    title: '计费管理',
    icon: 'wallet'
  },
  {
    path: '/monitor',
    title: '监控告警',
    icon: 'alarm',
    badge: 3
  },
  {
    path: '/config',
    title: '配置中心',
    icon: 'config'
  },
  {
    path: '/graylog',
    title: '灰度发布',
    icon: 'release'
  },
  {
    path: '/audit',
    title: '操作日志',
    icon: 'document'
  },
  {
    path: '/data-test',
    title: '数据查询测试',
    icon: 'play'
  }
]

// SVG 图标组件
const IconDashboard = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>`
}
const IconSetting = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>`
}
const IconComponent = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/></svg>`
}
const IconConnection = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>`
}
const IconWallet = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 12V8H6a2 2 0 0 1-2-2c0-1.1.9-2 2-2h12v4"/><path d="M4 6v12c0 1.1.9 2 2 2h14v-4"/><path d="M18 12a2 2 0 0 0-2-2H4a2 2 0 0 0 0 4h12"/></svg>`
}
const IconAlarm = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>`
}
const IconConfig = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>`
}
const IconRelease = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M8 12l2 2 4-4"/></svg>`
}
const IconDocument = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>`
}
const IconPlay = {
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>`
}

const icons: Record<string, { template: string }> = {
  dashboard: IconDashboard,
  setting: IconSetting,
  component: IconComponent,
  connection: IconConnection,
  wallet: IconWallet,
  alarm: IconAlarm,
  config: IconConfig,
  release: IconRelease,
  document: IconDocument,
  play: IconPlay
}

const handleMenuSelect = (path: string) => {
  if (path.startsWith('/')) {
    router.push(path)
  }
}

const handleCommand = (command: string) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
}
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="layout-wrapper">
      <!-- 左侧菜单 -->
      <aside class="sidebar" :class="{ collapsed: isCollapse }">
        <!-- Logo -->
        <div class="logo">
          <div class="logo-icon">
            <svg viewBox="0 0 32 32" fill="none">
              <rect x="2" y="2" width="28" height="28" rx="6" stroke="currentColor" stroke-width="2"/>
              <path d="M10 16L14 20L22 12" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span v-if="!isCollapse" class="logo-text">数据管理</span>
        </div>

        <!-- 菜单 -->
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          class="sidebar-menu"
          @select="handleMenuSelect"
        >
          <template v-for="item in menuItems" :key="item.path">
            <el-sub-menu v-if="item.children" :index="String(item.path)">
              <template #title>
                <component :is="icons[item.icon]" class="menu-icon" />
                <span class="menu-title">{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
                <span class="menu-title">{{ child.title }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path">
              <component :is="icons[item.icon]" class="menu-icon" />
              <span class="menu-title">{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>

        <!-- 底部折叠按钮 -->
        <div class="sidebar-footer">
          <button class="collapse-btn" @click="toggleSidebar">
            <svg v-if="isCollapse" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 18l6-6-6-6"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 18l-6-6 6-6"/>
            </svg>
          </button>
        </div>
      </aside>

      <!-- 右侧内容 -->
      <div class="main-wrapper">
        <!-- 顶部导航 -->
        <header class="header">
          <div class="header-left">
            <div class="breadcrumb">
              <span class="breadcrumb-item">首页</span>
              <span class="breadcrumb-separator">/</span>
              <span class="breadcrumb-item active">{{ route.meta?.title || '控制台' }}</span>
            </div>
          </div>

          <div class="header-right">
            <!-- 搜索按钮 -->
            <button class="header-btn">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <path d="m21 21-4.35-4.35"/>
              </svg>
            </button>

            <!-- 通知 -->
            <el-badge :value="3" :max="99" class="notification-badge" @click="router.push('/monitor')">
              <button class="header-btn">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                </svg>
              </button>
            </el-badge>

            <!-- 用户菜单 -->
            <el-dropdown trigger="click" @command="handleCommand">
              <div class="user-info">
                <div class="user-avatar">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                  </svg>
                </div>
                <span class="username">{{ userStore.username || '用户' }}</span>
                <svg class="dropdown-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="m6 9 6 6 6-6"/>
                </svg>
              </div>
              <template #dropdown>
                <el-dropdown-menu class="user-dropdown">
                  <el-dropdown-item command="profile">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                      <circle cx="12" cy="7" r="4"/>
                    </svg>
                    个人中心
                  </el-dropdown-item>
                  <el-dropdown-item command="settings">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="3"/>
                      <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
                    </svg>
                    系统设置
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                      <polyline points="16 17 21 12 16 7"/>
                      <line x1="21" y1="12" x2="9" y2="12"/>
                    </svg>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </header>

        <!-- 主内容区 -->
        <main class="main-content">
          <RouterView v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </RouterView>
        </main>
      </div>
    </div>
  </el-config-provider>
</template>

<style scoped>
.layout-wrapper {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg);
}

/* 侧边栏 */
.sidebar {
  width: 260px;
  height: 100vh;
  background: var(--color-bg-light);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), background 0.3s ease;
  overflow: hidden;
  flex-shrink: 0;
}

.sidebar.collapsed {
  width: 72px;
}

/* Logo */
.logo {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border);
  background: linear-gradient(90deg, rgba(0, 212, 170, 0.05), transparent);
}

.logo-icon {
  width: 36px;
  height: 36px;
  color: var(--color-primary);
  flex-shrink: 0;
  transition: transform 0.3s ease;
}

.logo:hover .logo-icon {
  transform: scale(1.05);
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  white-space: nowrap;
  letter-spacing: -0.02em;
}

/* 菜单 */
.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 0;
}

.sidebar-menu::-webkit-scrollbar {
  width: 4px;
}

.sidebar-menu::-webkit-scrollbar-thumb {
  background: var(--color-border-light);
  border-radius: 2px;
}

.menu-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.menu-title {
  font-size: 14px;
  margin-left: 12px;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 48px;
  line-height: 48px;
  margin: 4px 12px;
  padding: 0 16px !important;
  border-radius: 10px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: var(--color-hover-bg) !important;
}

:deep(.el-menu-item.is-active) {
  background: rgba(0, 212, 170, 0.12) !important;
  color: var(--color-primary) !important;
}

:deep(.el-menu-item.is-active .menu-icon) {
  color: var(--color-primary);
}

:deep(.el-sub-menu .el-menu-item) {
  padding-left: 52px !important;
}

:deep(.el-sub-menu__title) {
  color: var(--color-text-secondary) !important;
}

:deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--color-primary) !important;
}

:deep(.el-menu--collapse) {
  width: 72px;
}

:deep(.el-menu--collapse .el-sub-menu__title) {
  padding: 0 20px !important;
  justify-content: center;
}

:deep(.el-menu--collapse .el-menu-item) {
  padding: 0 20px !important;
  justify-content: center;
}

:deep(.el-menu--collapse .menu-title) {
  display: none;
}

/* 底部折叠按钮 */
.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--color-border);
}

.collapse-btn {
  width: 100%;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-light);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.collapse-btn:hover {
  background: var(--color-surface);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.collapse-btn svg {
  width: 18px;
  height: 18px;
}

/* 主内容区 */
.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* 顶部导航 */
.header {
  height: 72px;
  background: var(--color-bg-light);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  flex-shrink: 0;
  transition: background 0.3s ease;
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.breadcrumb-item {
  color: var(--color-text-tertiary);
}

.breadcrumb-item.active {
  color: var(--color-text-primary);
  font-weight: 500;
}

.breadcrumb-separator {
  color: var(--color-text-tertiary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.header-btn:hover {
  background: var(--color-surface);
  color: var(--color-text-primary);
  border-color: var(--color-border);
}

.header-btn svg {
  width: 20px;
  height: 20px;
}

.notification-badge {
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    transform: scale(1.1);
  }

  :deep(.el-badge__content) {
    background: var(--color-danger);
    border: none;
  }
}

/* 用户信息 */
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  margin-left: 8px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-info:hover {
  border-color: var(--color-border-light);
  background: var(--color-surface-hover);
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary), var(--color-info));
  border-radius: 8px;
  color: var(--color-bg);
}

.user-avatar svg {
  width: 18px;
  height: 18px;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.dropdown-arrow {
  width: 16px;
  height: 16px;
  color: var(--color-text-tertiary);
  transition: transform 0.2s ease;
}

.user-info:hover .dropdown-arrow {
  transform: translateY(2px);
}

/* 下拉菜单 */
.user-dropdown {
  background: var(--color-surface) !important;
  border: 1px solid var(--color-border) !important;
  border-radius: 12px !important;
  padding: 8px !important;
  min-width: 180px;
}

.user-dropdown :deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.user-dropdown :deep(.el-dropdown-menu__item:hover) {
  background: var(--color-bg-light);
  color: var(--color-text-primary);
}

.user-dropdown :deep(.el-dropdown-menu__item) svg {
  width: 18px;
  height: 18px;
}

.user-dropdown :deep(.el-dropdown-menu__item--divided) {
  border-top: 1px solid var(--color-border);
  margin-top: 8px;
  padding-top: 14px;
}

/* 主内容区 */
.main-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: var(--color-bg);
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
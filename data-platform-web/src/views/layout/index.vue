<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { ref, computed, onMounted, onUnmounted } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElConfigProvider, ElMenu, ElMenuItem, ElSubMenu, ElDropdown, ElDropdownItem, ElDropdownMenu, ElBadge, ElMessage } from 'element-plus'
import type { Component } from 'vue'
import {
  Bell,
  Connection,
  DataAnalysis,
  Document,
  Grid,
  Promotion,
  SetUp,
  Setting,
  Wallet
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getAlertRecordList } from '@/api/monitor'
import { logout as logoutRequest } from '@/api/auth'
import { STORAGE_KEYS, THEME_MODE } from '@/constants'
import { applyTheme, getStoredTheme } from '@/composables/useTheme'
import { extractPageData } from '@/utils/pagination'
import { hasNavigationPermission, navigationManifest } from '@/router/navigation'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isCollapse = ref(false)
const pendingAlertCount = ref(0)
let alertCountTimer: number | undefined

const handleViewportResize = () => {
  if (window.innerWidth <= 900) {
    isCollapse.value = true
  }
}

const handleStorageChange = (e: StorageEvent) => {
  if (e.key === STORAGE_KEYS.THEME) {
    applyTheme((e.newValue || THEME_MODE.DARK) as typeof THEME_MODE[keyof typeof THEME_MODE])
  }
}

const handleThemeChange = () => {
  applyTheme(getStoredTheme())
}

const handleAlertRecordUpdated = () => {
  void fetchPendingAlertCount()
}

onMounted(() => {
  applyTheme(getStoredTheme())
  handleViewportResize()
  window.addEventListener('storage', handleStorageChange)
  window.addEventListener('theme-change', handleThemeChange)
  window.addEventListener('resize', handleViewportResize)
  window.addEventListener('alert-record-updated', handleAlertRecordUpdated)
  void fetchPendingAlertCount()
  alertCountTimer = window.setInterval(fetchPendingAlertCount, 60_000)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener('theme-change', handleThemeChange)
  window.removeEventListener('resize', handleViewportResize)
  window.removeEventListener('alert-record-updated', handleAlertRecordUpdated)
  if (alertCountTimer) window.clearInterval(alertCountTimer)
})

const activeMenu = computed(() => route.path)

const allMenuItems = navigationManifest

// 管理员是显式能力，不依赖角色名称。
const isAdmin = computed(() => userStore.hasPermission('system:admin'))

const canViewNotifications = computed(() => isAdmin.value || userStore.hasPermission('monitor:view'))

const fetchPendingAlertCount = async () => {
  if (!canViewNotifications.value) {
    pendingAlertCount.value = 0
    return
  }
  try {
    const response = await getAlertRecordList({ page: 1, pageSize: 1, status: 'pending' })
    pendingAlertCount.value = extractPageData(response).total
  } catch {
    pendingAlertCount.value = 0
  }
}

const handleNotificationClick = () => {
  if (!canViewNotifications.value) return
  router.push({ path: '/monitor', query: { tab: 'record', status: 'pending' } })
}

// 菜单和路由共用同一份 page-permission manifest。
const menuItems = computed(() => filterMenuItems(allMenuItems))

function filterMenuItems(items: readonly typeof navigationManifest[number][]): typeof navigationManifest[number][] {
  return items
    .map(item => {
      if (item.children) {
        const filteredChildren = filterMenuItems(item.children)
        if (filteredChildren.length > 0) {
          return { ...item, children: filteredChildren }
        }
        return null
      }
      return hasNavigationPermission(item, userStore.permissions) ? item : null
    })
    .filter((item): item is typeof navigationManifest[number] => item !== null)
}

const icons: Record<string, Component> = {
  dashboard: DataAnalysis,
  setting: Setting,
  component: Grid,
  connection: Connection,
  wallet: Wallet,
  alarm: Bell,
  config: SetUp,
  release: Promotion,
  document: Document,
  play: Promotion
}

const handleMenuSelect = (path: string) => {
  if (path.startsWith('/')) {
    router.push(path)
  }
}

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await logoutRequest()
    } catch {
      ElMessage.warning('服务端注销未确认，本地会话已清理')
    } finally {
      userStore.logout()
      await router.push('/login')
    }
  } else if (command === 'profile') {
    router.push('/profile')
  }
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
            <el-sub-menu v-if="item.children && item.children.length > 0" :index="String(item.path)">
              <template #title>
                <component :is="icons[item.icon || 'document']" class="menu-icon" />
                <span class="menu-title">{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
                <span class="menu-title">{{ child.title }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path">
              <component :is="icons[item.icon || 'document']" class="menu-icon" />
              <span class="menu-title">{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>

        <!-- 底部折叠按钮 -->
        <div class="sidebar-footer">
          <button
            class="collapse-btn"
            type="button"
            :aria-label="isCollapse ? '展开侧边栏' : '收起侧边栏'"
            @click="isCollapse = !isCollapse"
          >
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
            <button class="header-btn" type="button" aria-label="搜索">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <path d="m21 21-4.35-4.35"/>
              </svg>
            </button>

            <!-- 通知 -->
            <el-badge
              v-if="canViewNotifications"
              :value="pendingAlertCount"
              :hidden="pendingAlertCount === 0"
              :max="99"
              class="notification-badge"
            >
              <button class="header-btn" type="button" aria-label="通知" @click="handleNotificationClick">
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
  width: 100%;
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

.collapse-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
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

.header-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
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

@media (max-width: 900px) {
  .sidebar,
  .sidebar.collapsed {
    width: 64px;
  }

  .sidebar-footer {
    display: none;
  }

  .header {
    padding: 0 16px;
  }

  .username,
  .dropdown-arrow {
    display: none;
  }

  .user-info {
    margin-left: 0;
    padding: 6px;
  }

  .main-content {
    padding: 16px;
  }
}

@media (max-width: 480px) {
  .sidebar,
  .sidebar.collapsed {
    width: 56px;
  }

  .logo {
    height: 64px;
    padding: 0 12px;
  }

  .logo-icon {
    width: 30px;
    height: 30px;
  }

  .header {
    height: 64px;
    padding: 0 12px;
  }

  .header-btn {
    display: none;
  }

  .main-content {
    padding: 12px;
  }
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

/* 减少动画支持 */
@media (prefers-reduced-motion: reduce) {
  .fade-enter-active,
  .fade-leave-active {
    transition: none;
  }

  .sidebar {
    transition: none;
  }

  .main-content {
    transition: none;
  }
}
</style>

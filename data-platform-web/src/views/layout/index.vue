<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { ref, computed } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElConfigProvider, ElMenu, ElMenuItem, ElSubMenu, ElAvatar, ElDropdown, ElDropdownItem, ElDropdownMenu, ElButton } from 'element-plus'
import { Odometer, Setting, Goods, Connection, Wallet, AlarmClock } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const isCollapse = ref(false)

const activeMenu = computed(() => route.path)

const menuItems = [
  { path: '/dashboard', title: '数据概览', icon: Odometer },
  { 
    path: '/system', 
    title: '系统管理', 
    icon: Setting,
    children: [
      { path: '/tenant', title: '租户管理' },
      { path: '/user', title: '用户管理' },
      { path: '/role', title: '角色管理' }
    ]
  },
  { 
    path: '/business', 
    title: '业务管理', 
    icon: Goods,
    children: [
      { path: '/vendor', title: '厂商管理' },
      { path: '/caller', title: '调用方管理' },
      { path: '/datatype', title: '数据类型' }
    ]
  },
  { path: '/call', title: '调用记录', icon: Connection },
  { path: '/billing', title: '计费管理', icon: Wallet },
  { path: '/monitor', title: '监控告警', icon: AlarmClock }
]

const handleMenuSelect = (path: string) => {
  router.push(path)
}

const handleCommand = (command: string) => {
  if (command === 'logout') {
    localStorage.removeItem('token')
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
}

// 获取用户名
const username = localStorage.getItem('username') || '管理员'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <el-container class="layout-container">
      <!-- 左侧菜单 -->
      <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
        <div class="logo">
          <img v-if="!isCollapse" src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0MCIgaGVpZ2h0PSI0MCI+PHJlY3Qgd2lkdGg9IjQwIiBoZWlnaHQ9IjQwIiBmaWxsPSIjMDk4Q0MzIi8+PHJlY3QgeD0iMTAiIHk9IjEwIiB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIGZpbGw9IiNmZmYiLz48L3N2Zz4=" alt="logo" />
          <span v-if="!isCollapse" class="logo-text">数据管理平台</span>
        </div>
        
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          class="sidebar-menu"
          @select="handleMenuSelect"
        >
          <template v-for="item in menuItems" :key="item.path">
            <el-sub-menu v-if="item.children" :index="item.path">
              <template #title>
                <component :is="item.icon" />
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path">
              <component :is="item.icon" />
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>
      
      <!-- 右侧内容 -->
      <el-container>
        <!-- 顶部导航 -->
        <el-header class="header">
          <div class="header-left">
            <el-button :icon="isCollapse ? 'Expand' : 'Fold'" text @click="toggleSidebar" />
          </div>
          
          <div class="header-right">
            <el-dropdown @command="handleCommand">
              <div class="user-info">
                <el-avatar :size="32" src="https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png" />
                <span class="username">{{ username }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>
        
        <!-- 主内容区 -->
        <el-main class="main-content">
          <RouterView />
        </el-main>
      </el-container>
    </el-container>
  </el-config-provider>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #263445;
  color: #fff;
}

.logo img {
  width: 32px;
  height: 32px;
}

.logo-text {
  font-size: 16px;
  font-weight: bold;
  white-space: nowrap;
}

.sidebar-menu {
  border-right: none;
  background-color: #304156;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 220px;
}

.header {
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 0 8px;
  border-radius: 4px;
}

.user-info:hover {
  background-color: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #333;
}

.main-content {
  background: #f0f2f5;
  padding: 20px;
  min-height: calc(100vh - 60px);
}

/* 菜单样式 */
:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  color: #bfcbd9;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background-color: #263445 !important;
}

:deep(.el-menu-item.is-active) {
  background-color: #409EFF !important;
  color: #fff !important;
}

:deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #409EFF !important;
}
</style>
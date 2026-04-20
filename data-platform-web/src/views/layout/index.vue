<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { ref, computed, onMounted } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElConfigProvider, ElMenu, ElMenuItem, ElSubMenu, ElAvatar, ElDropdown, ElDropdownItem, ElDropdownMenu, ElButton } from 'element-plus'
import { Odometer, Setting, Goods, Connection, Wallet, AlarmClock, Fold, Expand } from '@element-plus/icons-vue'

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
  { path: '/monitor', title: '监控告警', icon: AlarmClock },
  { path: '/audit', title: '操作日志', icon: Document }
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
    <div class="layout-wrapper">
      <!-- 左侧菜单 -->
      <aside class="sidebar" :class="{ collapsed: isCollapse }">
        <div class="logo">
          <img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0MCIgaGVpZ2h0PSI0MCI+PHJlY3Qgd2lkdGg9IjQwIiBoZWlnaHQ9IjQwIiBmaWxsPSIjMDk4Q0MzIi8+PHJlY3QgeD0iMTAiIHk9IjEwIiB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIGZpbGw9IiNmZmYiLz48L3N2Zz4=" alt="logo" class="logo-img" />
          <span v-if="!isCollapse" class="logo-text">数据管理平台</span>
        </div>
        
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          class="sidebar-menu"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          @select="handleMenuSelect"
        >
          <template v-for="item in menuItems" :key="item.path">
            <el-sub-menu v-if="item.children" :index="item.path">
              <template #title>
                <component :is="item.icon" class="menu-icon" />
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path">
              <component :is="item.icon" class="menu-icon" />
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </aside>
      
      <!-- 右侧内容 -->
      <div class="main-wrapper">
        <!-- 顶部导航 -->
        <header class="header">
          <div class="header-left">
            <el-button :icon="isCollapse ? Expand : Fold" text @click="toggleSidebar" />
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
        </header>
        
        <!-- 主内容区 -->
        <main class="main-content">
          <RouterView />
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
}

.sidebar {
  width: 220px;
  height: 100vh;
  background-color: #304156;
  transition: width 0.3s ease;
  overflow: hidden;
  flex-shrink: 0;
}

.sidebar.collapsed {
  width: 64px;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #263445;
  color: #fff;
  overflow: hidden;
}

.logo-img {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.logo-text {
  font-size: 14px;
  font-weight: bold;
  white-space: nowrap;
  overflow: hidden;
}

.sidebar-menu {
  border-right: none;
  height: calc(100vh - 60px);
  overflow-y: auto;
}

.menu-icon {
  width: 20px;
  height: 20px;
  margin-right: 8px;
}

.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 60px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
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
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.user-info:hover {
  background-color: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #333;
}

.main-content {
  flex: 1;
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

/* 菜单样式覆盖 */
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 50px;
  line-height: 50px;
}

:deep(.el-menu--collapse) {
  width: 64px;
}

:deep(.el-menu--collapse .el-sub-menu__title) {
  padding: 0 20px !important;
}

:deep(.el-menu--collapse .el-menu-item) {
  padding: 0 20px !important;
}
</style>
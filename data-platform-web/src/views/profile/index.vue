<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>个人中心</h2>
        <p class="header-desc">管理个人账户信息与设置</p>
      </div>
    </div>

    <el-row :gutter="24">
      <el-col :span="8">
        <el-card class="profile-card">
          <div class="avatar-section">
            <div class="avatar">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
            <h3 class="username">{{ userInfo.username }}</h3>
            <p class="role">{{ userInfo.role }}</p>
          </div>
          <el-divider />
          <div class="info-list">
            <div class="info-item">
              <span class="label">租户</span>
              <span class="value">{{ userInfo.tenant }}</span>
            </div>
            <div class="info-item">
              <span class="label">邮箱</span>
              <span class="value">{{ userInfo.email }}</span>
            </div>
            <div class="info-item">
              <span class="label">手机</span>
              <span class="value">{{ userInfo.phone }}</span>
            </div>
            <div class="info-item">
              <span class="label">上次登录</span>
              <span class="value">{{ userInfo.lastLogin }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card class="settings-card">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本信息" name="info">
              <el-form :model="formData" label-width="100px" class="profile-form">
                <el-form-item label="用户名">
                  <el-input v-model="formData.username" disabled />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="formData.email" />
                </el-form-item>
                <el-form-item label="手机号">
                  <el-input v-model="formData.phone" />
                </el-form-item>
                <el-form-item label="昵称">
                  <el-input v-model="formData.nickname" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleSaveInfo">保存修改</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="修改密码" name="password">
              <el-form :model="passwordForm" label-width="100px" class="profile-form">
                <el-form-item label="当前密码">
                  <el-input v-model="passwordForm.oldPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="新密码">
                  <el-input v-model="passwordForm.newPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="确认密码">
                  <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleChangePassword">修改密码</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="偏好设置" name="preference">
              <el-form label-width="100px" class="profile-form">
                <el-form-item label="语言">
                  <el-select v-model="preferenceForm.language" style="width: 100%">
                    <el-option label="简体中文" value="zh-CN" />
                    <el-option label="English" value="en-US" />
                  </el-select>
                </el-form-item>
                <el-form-item label="时区">
                  <el-select v-model="preferenceForm.timezone" style="width: 100%">
                    <el-option label="Asia/Shanghai (UTC+8)" value="Asia/Shanghai" />
                    <el-option label="America/New_York (UTC-5)" value="America/New_York" />
                    <el-option label="Europe/London (UTC+0)" value="Europe/London" />
                  </el-select>
                </el-form-item>
                <el-form-item label="主题">
                  <el-radio-group v-model="preferenceForm.theme">
                    <el-radio label="dark">深色</el-radio>
                    <el-radio label="light">浅色</el-radio>
                    <el-radio label="auto">跟随系统</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="邮件通知">
                  <el-switch v-model="preferenceForm.emailNotify" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleSavePreference">保存设置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('info')

// 主题相关
type ThemeMode = 'dark' | 'light' | 'auto'

const getStoredTheme = (): ThemeMode => {
  return (localStorage.getItem('theme') as ThemeMode) || 'dark'
}

const applyTheme = (theme: ThemeMode) => {
  const body = document.body

  if (theme === 'auto') {
    body.classList.remove('light-theme')
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    if (!prefersDark) {
      body.classList.add('light-theme')
    }
  } else if (theme === 'light') {
    body.classList.add('light-theme')
  } else {
    body.classList.remove('light-theme')
  }
}

// 监听系统主题变化
const setupMediaQuery = () => {
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQuery.addEventListener('change', () => {
    if (preferenceForm.theme === 'auto') {
      applyTheme('auto')
    }
  })
}

const userInfo = reactive({
  username: 'admin',
  role: '超级管理员',
  tenant: '默认租户',
  email: 'admin@example.com',
  phone: '138****8888',
  lastLogin: '2026-04-24 10:30:00'
})

const formData = reactive({
  username: 'admin',
  email: 'admin@example.com',
  phone: '13800138000',
  nickname: '管理员'
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const preferenceForm = reactive({
  language: 'zh-CN',
  timezone: 'Asia/Shanghai',
  theme: getStoredTheme(),
  emailNotify: true
})

// 监听主题变化
watch(() => preferenceForm.theme, (newTheme) => {
  applyTheme(newTheme)
  localStorage.setItem('theme', newTheme)
  // 通知其他组件主题已变更
  window.dispatchEvent(new Event('theme-change'))
})

const handleSaveInfo = () => {
  ElMessage.success('信息已更新')
}

const handleChangePassword = () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  ElMessage.success('密码已修改')
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

const handleSavePreference = () => {
  localStorage.setItem('theme', preferenceForm.theme)
  localStorage.setItem('language', preferenceForm.language)
  localStorage.setItem('timezone', preferenceForm.timezone)
  localStorage.setItem('emailNotify', String(preferenceForm.emailNotify))
  ElMessage.success('偏好设置已保存')
}

onMounted(() => {
  applyTheme(preferenceForm.theme)
  setupMediaQuery()
})
</script>

<style scoped>
.page-container { max-width: 1200px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; color: var(--color-text-primary); margin: 0 0 4px; letter-spacing: -0.02em; }
.header-desc { font-size: 14px; color: var(--color-text-tertiary); margin: 0; }

.profile-card { height: fit-content; }
.avatar-section { text-align: center; padding: 20px 0; }
.avatar {
  width: 96px;
  height: 96px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary), #6366F1);
  border-radius: 50%;
  color: #0A1628;
}
.avatar svg { width: 48px; height: 48px; }
.username { font-size: 20px; font-weight: 600; color: var(--color-text-primary); margin: 0 0 4px; }
.role { font-size: 14px; color: var(--color-primary); margin: 0; }

.info-list { padding: 0 8px; }
.info-item { display: flex; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid var(--color-border); }
.info-item:last-child { border-bottom: none; }
.info-item .label { color: var(--color-text-tertiary); font-size: 14px; }
.info-item .value { color: var(--color-text-primary); font-size: 14px; font-family: var(--font-mono); }

.settings-card { min-height: 500px; }
.profile-form { max-width: 500px; padding: 20px 0; }

:deep(.el-tabs__item) { font-size: 14px; }
:deep(.el-tabs__item.is-active) { color: var(--color-primary); }
:deep(.el-tabs__active-bar) { background: var(--color-primary); }
</style>
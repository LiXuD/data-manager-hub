<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { request } from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = ref({
  username: '',
  password: '',
  remember: false
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const res = await request.post<{ data: { token: string; username: string; userId: number } }>('/auth/login', {
          username: loginForm.value.username,
          password: loginForm.value.password
        })

        const data = res.data
        userStore.login(data.token, {
          id: String(data.userId),
          username: data.username,
          nickname: data.username,
          roles: ['admin']
        })

        ElMessage.success('登录成功')
        router.push('/dashboard')
      } catch (error) {
        console.error('登录失败:', error)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<template>
  <div class="login-container">
    <!-- 背景装饰 -->
    <div class="bg-gradient"></div>
    <div class="bg-grid"></div>
    <div class="bg-glow bg-glow-1"></div>
    <div class="bg-glow bg-glow-2"></div>

    <!-- 浮动粒子 -->
    <div class="particles">
      <span v-for="i in 20" :key="i" class="particle" :style="{
        left: Math.random() * 100 + '%',
        top: Math.random() * 100 + '%',
        animationDelay: Math.random() * 5 + 's',
        animationDuration: (3 + Math.random() * 4) + 's'
      }"></span>
    </div>

    <div class="login-box">
      <!-- Logo 区域 -->
      <div class="login-header">
        <div class="logo-icon">
          <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="4" y="4" width="40" height="40" rx="10" stroke="currentColor" stroke-width="2.5"/>
            <path d="M14 24L20 30L34 16" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            <circle cx="24" cy="24" r="16" stroke="currentColor" stroke-width="1.5" stroke-dasharray="4 4" opacity="0.3"/>
          </svg>
        </div>
        <h1>数据管理平台</h1>
        <p>Data Management Platform</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              size="large"
            />
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
            </svg>
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              @keyup.enter="handleLogin"
            />
          </div>
        </el-form-item>

        <el-form-item>
          <div class="form-options">
            <el-checkbox v-model="loginForm.remember">记住我</el-checkbox>
            <a href="#" class="forgot-link">忘记密码？</a>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          >
            <span v-if="!loading">立即登录</span>
            <span v-else>登录中...</span>
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <div class="divider">
          <span>安全登录</span>
        </div>
      </div>
    </div>

    <!-- 版本信息 -->
    <div class="version-info">
      <span>v1.0.0</span>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: #0A1628;
}

/* 背景渐变 */
.bg-gradient {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 50% at 50% -20%, rgba(0, 212, 170, 0.15), transparent),
    radial-gradient(ellipse 60% 40% at 80% 100%, rgba(99, 102, 241, 0.1), transparent);
}

/* 网格背景 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px);
  background-size: 60px 60px;
}

/* 光晕效果 */
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.4;
}

.bg-glow-1 {
  width: 600px;
  height: 600px;
  background: var(--color-primary);
  top: -200px;
  left: -100px;
  animation: float 8s ease-in-out infinite;
}

.bg-glow-2 {
  width: 400px;
  height: 400px;
  background: #6366F1;
  bottom: -100px;
  right: -50px;
  animation: float 10s ease-in-out infinite reverse;
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(30px, 30px);
  }
}

/* 浮动粒子 */
.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  width: 4px;
  height: 4px;
  background: var(--color-primary);
  border-radius: 50%;
  opacity: 0;
  animation: particle-float 5s ease-in-out infinite;
}

@keyframes particle-float {
  0% {
    opacity: 0;
    transform: translateY(0) scale(1);
  }
  20% {
    opacity: 0.6;
  }
  80% {
    opacity: 0.3;
  }
  100% {
    opacity: 0;
    transform: translateY(-100px) scale(0.5);
  }
}

/* 登录框 */
.login-box {
  width: 440px;
  padding: 48px 40px;
  background: rgba(21, 31, 50, 0.8);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  box-shadow:
    0 25px 50px -12px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(255, 255, 255, 0.05) inset;
  position: relative;
  z-index: 10;
  animation: box-appear 0.6s ease-out;
}

@keyframes box-appear {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* Logo 区域 */
.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  color: var(--color-primary);
  animation: icon-appear 0.8s ease-out 0.2s both;
}

@keyframes icon-appear {
  from {
    opacity: 0;
    transform: scale(0.8) rotate(-10deg);
  }
  to {
    opacity: 1;
    transform: scale(1) rotate(0);
  }
}

.login-header h1 {
  font-size: 26px;
  font-weight: 700;
  color: #E8EDF3;
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.login-header p {
  font-size: 13px;
  color: #5A6A7E;
  margin: 0;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

/* 输入框包装 */
.input-wrapper {
  position: relative;

  .input-icon {
    position: absolute;
    left: 16px;
    top: 50%;
    transform: translateY(-50%);
    width: 18px;
    height: 18px;
    color: #5A6A7E;
    z-index: 1;
    transition: color 0.2s ease;
  }

  :deep(.el-input__wrapper) {
    padding-left: 48px !important;
  }

  &:focus-within .input-icon {
    color: var(--color-primary);
  }
}

/* 表单 */
.login-form {
  margin-top: 10px;
}

:deep(.el-form-item) {
  margin-bottom: 24px;
}

/* 表单选项 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

:deep(.el-checkbox__label) {
  color: #8B98A8 !important;
}

.forgot-link {
  color: #5A6A7E;
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s ease;

  &:hover {
    color: var(--color-primary);
  }
}

/* 登录按钮 */
.login-button {
  width: 100%;
  height: 52px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 14px !important;
  background: linear-gradient(135deg, var(--color-primary) 0%, #00B894 100%) !important;
  border: none !important;
  color: #0A1628 !important;
  letter-spacing: 0.05em;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease !important;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
    transition: left 0.5s ease;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow:
      0 10px 30px -5px rgba(0, 212, 170, 0.4),
      0 0 0 1px rgba(0, 212, 170, 0.3) inset;

    &::before {
      left: 100%;
    }
  }

  &:active {
    transform: translateY(0);
  }
}

/* 登录底部 */
.login-footer {
  margin-top: 32px;
  text-align: center;
}

.divider {
  display: flex;
  align-items: center;
  margin-bottom: 20px;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent);
  }

  span {
    padding: 0 16px;
    font-size: 12px;
    color: #5A6A7E;
    text-transform: uppercase;
    letter-spacing: 0.1em;
  }
}

.footer-tips {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #5A6A7E;

  .dot {
    width: 3px;
    height: 3px;
    background: #5A6A7E;
    border-radius: 50%;
  }
}

/* 版本信息 */
.version-info {
  position: absolute;
  bottom: 24px;
  right: 24px;
  font-size: 12px;
  color: #3A4A5E;
  font-family: var(--font-mono);
}

/* 响应式 */
@media (max-width: 480px) {
  .login-box {
    width: calc(100% - 32px);
    padding: 32px 24px;
  }

  .login-header h1 {
    font-size: 22px;
  }
}
</style>
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      // 厂商服务
      '^/vendor': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      // 调用方服务
      '^/caller': {
        target: 'http://localhost:8082',
        changeOrigin: true
      },
      // 计费服务
      '^/billing': {
        target: 'http://localhost:8083',
        changeOrigin: true
      },
      // 调用记录服务
      '^/call-record': {
        target: 'http://localhost:8084',
        changeOrigin: true
      },
      // 监控告警服务
      '^/alert': {
        target: 'http://localhost:8085',
        changeOrigin: true
      },
      // 租户服务
      '^/tenant': {
        target: 'http://localhost:8086',
        changeOrigin: true
      },
      // 用户服务
      '^/user': {
        target: 'http://localhost:8087',
        changeOrigin: true
      },
      // 角色服务
      '^/role': {
        target: 'http://localhost:8088',
        changeOrigin: true
      },
      // 数据类型服务
      '^/datatype': {
        target: 'http://localhost:8089',
        changeOrigin: true
      },
      // 日志服务
      '^/log': {
        target: 'http://localhost:8090',
        changeOrigin: true
      },
      // 配置中心服务
      '^/config': {
        target: 'http://localhost:8091',
        changeOrigin: true
      },
      // 灰度发布服务
      '^/graylog': {
        target: 'http://localhost:8092',
        changeOrigin: true
      }
    }
  }
})
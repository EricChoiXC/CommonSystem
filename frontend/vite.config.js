import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/backend': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 前端语言包动态接口（D2 决策）：GET /locales/{code}.json 免鉴权
      '/locales': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: resolve(__dirname, '../backend/src/main/resources/static'),
    emptyOutDir: true
  }
})

import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发 server 与 preview 共用：把 /api、/ws、/files 原样代理到 Spring Boot
const backendProxy = {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
  '/ws': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    ws: true,
  },
  // 本地上传的图片由后端映射 /files/** （规范第 8 节）
  '/files': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // sockjs-client 在 Vite 下需要将 global 映射为 window
  define: {
    global: 'window',
  },
  build: {
    rolldownOptions: {
      output: {
        // 把体积大的依赖拆出主包/管理端图表包，减轻 >500kB 警告
        codeSplitting: {
          groups: [
            {
              name: 'echarts',
              test: /node_modules[\\/](?:echarts|zrender)/,
            },
            {
              name: 'element-plus',
              test: /node_modules[\\/]element-plus/,
            },
            {
              name: 'vue-vendor',
              test: /node_modules[\\/](?:vue|vue-router|pinia|@vue)[\\/]/,
            },
          ],
        },
      },
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: backendProxy,
  },
  preview: {
    host: '127.0.0.1',
    port: 4173,
    strictPort: true,
    proxy: backendProxy,
  },
})

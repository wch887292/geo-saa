import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'

  return {
    plugins: [
      vue(),
      // Element Plus 按需自动引入：模板中的 <el-*> 组件及其样式按需加载，
      // 替代原先全量 import ElementPlus + 全量 CSS，显著缩减 vendor-element 分包体积。
      Components({
        resolvers: [
          ElementPlusResolver({ importStyle: 'css', locale: zhCn })
        ]
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: Number(env.VITE_PORT) || 3000,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    },
    build: {
      target: 'es2018',
      sourcemap: false,
      // 构建后单 chunk 曾达 1.19MB（含 ECharts 1.03MB），首屏加载明显偏重。
      // 按依赖来源手动分包，让浏览器可以并行下载并长期缓存第三方库。
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return
            if (id.includes('echarts') || id.includes('zrender')) return 'vendor-echarts'
            // element-plus 故意不归入手动分包：ElementPlusResolver 走 `element-plus/es`
            // 全量 barrel 重新导出，若强制塞进单一 vendor-element 分包，Rollup 会为
            // 保留跨分包重导出而保留所有组件（无法摇树，chunk 恒为 ~809KB）。
            // 交给 Rollup 默认分包后，可真正按需摇树，未用组件（calendar/watermark/
            // carousel/color-picker 等）不会进包，并按组件粒度缓存。
            if (id.includes('element-plus') || id.includes('@element-plus')) return
            if (id.includes('vue-router') || id.includes('pinia') || /node_modules[\\/]@?vue/.test(id)) {
              return 'vendor-vue'
            }
            return 'vendor'
          }
        }
      },
      chunkSizeWarningLimit: 800
    }
  }
})

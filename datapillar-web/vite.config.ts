import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      tailwindcss(),
      react()
    ],

    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src')
      }
    },

    server: {
      port: 3000,
      host: true,
      // 统一代理到 Gateway，由 Gateway 负责路由
      proxy: {
        '/api': {
          target: env.VITE_GATEWAY_URL || 'http://localhost:6000',
          changeOrigin: true,
          secure: false
        }
      }
    },

    build: {
      // 生产环境优化
      target: 'es2015',
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: false,

      // 分包策略
      rollupOptions: {
        output: {
          manualChunks: {
            // React 核心
            'react-vendor': ['react', 'react-dom'],

            // React Router
            'router': ['react-router-dom'],

            // Ant Design (UI 库)
            'antd': ['antd', '@ant-design/icons'],

            // Monaco Editor (大文件)
            'monaco': ['monaco-editor', '@monaco-editor/react'],

            // React Flow
            'reactflow': ['@xyflow/react'],

            // 工具库
            'utils': ['axios', 'lodash-es', 'qs', 'zustand'],

            // UI 库 (其他)
            'ui-libs': ['@dnd-kit/core', '@dnd-kit/sortable']
          },

          // 资源文件命名
          assetFileNames: 'assets/[name]-[hash][extname]',
          chunkFileNames: 'assets/[name]-[hash].js',
          entryFileNames: 'assets/[name]-[hash].js'
        }
      },

      // 分块大小警告限制
      chunkSizeWarningLimit: 1000
    },

    optimizeDeps: {
      include: [
        'react',
        'react-dom',
        'react-router-dom',
        'axios',
        'lodash-es',
        'dt-sql-parser',
        'antlr4ng'
      ],
      exclude: ['monaco-editor'] // Monaco 按需加载，不预构建
    }
  }
})

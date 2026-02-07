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
      alias: [
        { find: '@', replacement: path.resolve(__dirname, './src') }
      ],
      dedupe: ['lodash']
    },

    server: {
      port: 3001,
      host: true,
      // 统一代理到 Gateway，由 Gateway 负责路由
      proxy: {
        '/api': {
          target: env.VITE_GATEWAY_URL ,
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
        'lodash',
        'dt-sql-parser',
        'antlr4ng',
        '@neo4j-nvl/base',
        '@neo4j-nvl/interaction-handlers',
        '@neo4j-nvl/react',
        // Monaco 深度导入，减少 dev 请求爆炸
        '@monaco-editor/react',
        'monaco-editor/esm/vs/editor/editor.api',
        'monaco-editor/esm/vs/basic-languages/sql/sql',
        // 视觉与交互重库，换取首屏响应
        'framer-motion',
        'lucide-react',
        'react-icons',
        'react-icons/si',
        'sonner',
        // 大型 UI 与图形依赖
        '@xyflow/react',
        'react-virtuoso',
        // 文本/表单/格式化
        'react-markdown',
        'remark-gfm',
        'react-hook-form',
        '@hookform/resolvers',
        'zod',
        'yup',
        'sql-formatter',
        // 其他重依赖
        '@iconify/react',
        '@radix-ui/react-select',
        '@dnd-kit/utilities',
        'zustand',
        // neo4j 依赖的 CommonJS 模块，需要预构建
        '@neo4j-bloom/dagre',
        'graphlib'
      ]
    }
  }
})

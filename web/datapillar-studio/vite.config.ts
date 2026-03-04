import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Load environment variables
  const env = loadEnv(mode, process.cwd(), '')
  const gatewayUrl = env.VITE_GATEWAY_URL?.trim()
  if (!gatewayUrl) {
    throw new Error('Missing environment variables VITE_GATEWAY_URL')
  }
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
      // Unified agent to Gateway，by Gateway Responsible for routing
      proxy: {
        '/api': {
          target: gatewayUrl,
          changeOrigin: true,
          secure: false
        }
      }
    },

    build: {
      // Production environment optimization
      target: 'es2015',
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: false,

      // subcontracting strategy
      rollupOptions: {
        output: {
          manualChunks: {
            // React core
            'react-vendor': ['react', 'react-dom'],

            // React Router
            'router': ['react-router-dom'],

            // Ant Design (UI Library)
            'antd': ['antd', '@ant-design/icons'],

            // Monaco Editor (large files)
            'monaco': ['monaco-editor', '@monaco-editor/react'],

            // React Flow
            'reactflow': ['@xyflow/react'],

            // Tool library
            'utils': ['axios', 'lodash-es', 'qs', 'zustand'],

            // UI Library (Others)
            'ui-libs': ['@dnd-kit/core', '@dnd-kit/sortable']
          },

          // Resource file naming
          assetFileNames: 'assets/[name]-[hash][extname]',
          chunkFileNames: 'assets/[name]-[hash].js',
          entryFileNames: 'assets/[name]-[hash].js'
        }
      },

      // Chunk size warning limit
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
        // Monaco Deep import，reduce dev request explosion
        '@monaco-editor/react',
        'monaco-editor/esm/vs/editor/editor.api',
        'monaco-editor/esm/vs/basic-languages/sql/sql',
        // Visual and interactive repository，In exchange for first screen response
        'framer-motion',
        'lucide-react',
        'react-icons',
        'react-icons/si',
        'sonner',
        // Large UI Dependent on graphics
        '@xyflow/react',
        'react-virtuoso',
        // text/form/Format
        'react-markdown',
        'remark-gfm',
        'react-hook-form',
        '@hookform/resolvers',
        'zod',
        'yup',
        'sql-formatter',
        // Other heavy dependencies
        '@iconify/react',
        '@radix-ui/react-select',
        '@dnd-kit/utilities',
        'zustand',
        // neo4j dependent CommonJS module，Requires pre-build
        '@neo4j-bloom/dagre',
        'graphlib'
      ]
    }
  }
})

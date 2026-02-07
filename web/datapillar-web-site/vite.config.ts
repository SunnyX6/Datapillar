import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig(() => {
  return {
    plugins: [
      tailwindcss(),
      react()
    ],

    resolve: {
      alias: [
        { find: '@', replacement: path.resolve(__dirname, './src') }
      ]
    },

    server: {
      port: 3100,
      host: true
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
            'router': ['react-router-dom']
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
        'lucide-react',
        'clsx',
        'tailwind-merge'
      ]
    }
  }
})

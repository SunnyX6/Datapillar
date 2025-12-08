import { StrictMode, useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { Toaster } from 'sonner'
import { useTranslation } from 'react-i18next'
import { router } from './router'
import { useThemeStore, useI18nStore, useAuthStore } from '@/stores'
import '@/lib/i18n' // 初始化 i18n
import './index.css' // Tailwind CSS

/**
 * 应用初始化组件
 * 负责初始化主题、国际化、认证状态等全局状态
 */
export function App() {
  const { t, i18n } = useTranslation('common')

  useEffect(() => {
    // 初始化主题系统
    useThemeStore.getState().initialize()
    // 初始化国际化系统
    useI18nStore.getState().initialize()
    // 初始化认证状态（验证 token 有效性）
    useAuthStore.getState().initializeAuth()
  }, [])

  // 监听语言变化，动态更新网站标题
  useEffect(() => {
    document.title = t('site.title')
  }, [t, i18n.language])

  return (
    <>
      <RouterProvider router={router} />
      <Toaster position="top-right" richColors />
    </>
  )
}

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('找不到 root 元素')
}

const root = ReactDOM.createRoot(rootElement)

const renderApp = () => {
  try {
    root.render(
      <StrictMode>
        <App />
      </StrictMode>
    )
  } catch (error) {
    document.body.innerHTML = `
    <div style="padding: 40px; font-family: monospace;">
      <h1 style="color: red;">应用启动失败</h1>
      <pre style="background: #f5f5f5; padding: 20px; border-radius: 8px; overflow: auto;">
${error instanceof Error ? error.message : String(error)}

${error instanceof Error && error.stack ? error.stack : ''}
      </pre>
    </div>
  `
}
}

renderApp()

if (import.meta.hot) {
  import.meta.hot.accept()
  import.meta.hot.dispose(() => {
    root.unmount()
  })
}

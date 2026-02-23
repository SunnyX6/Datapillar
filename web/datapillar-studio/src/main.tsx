import { StrictMode, useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { Toaster } from 'sonner'
import { useTranslation } from 'react-i18next'
import { router } from './router'
import { AppRuntimeErrorBoundary } from './router/AppRuntimeErrorBoundary'
import { handleAppError, normalizeRuntimeError } from '@/lib/error-center'
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
    // 初始化认证状态（页面直刷 /home 时也要先恢复会话）
    void useAuthStore.getState().initializeAuth()
  }, [])

  // 监听语言变化，动态更新网站标题
  useEffect(() => {
    document.title = t('site.title')
  }, [t, i18n.language])

  // 兜底捕获非 React 渲染阶段的运行时错误（如未处理 Promise 异常）
  useEffect(() => {
    const handleWindowError = (event: ErrorEvent) => {
      const runtimeError = event.error ?? new Error(event.message)
      handleAppError(
        normalizeRuntimeError(runtimeError, {
          module: 'runtime/window-error',
          isCoreRequest: true,
          raw: {
            filename: event.filename,
            lineno: event.lineno,
            colno: event.colno
          }
        })
      )
    }

    const handleUnhandledRejection = (event: PromiseRejectionEvent) => {
      handleAppError(
        normalizeRuntimeError(event.reason, {
          module: 'runtime/unhandled-rejection',
          isCoreRequest: true
        })
      )
    }

    window.addEventListener('error', handleWindowError)
    window.addEventListener('unhandledrejection', handleUnhandledRejection)
    return () => {
      window.removeEventListener('error', handleWindowError)
      window.removeEventListener('unhandledrejection', handleUnhandledRejection)
    }
  }, [])

  return (
    <>
      <AppRuntimeErrorBoundary>
        <RouterProvider router={router} />
      </AppRuntimeErrorBoundary>
      <Toaster position="top-right" richColors />
    </>
  )
}

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('找不到 root 元素')
}

const root = ReactDOM.createRoot(rootElement)

root.render(
  <StrictMode>
    <App />
  </StrictMode>
)

if (import.meta.hot) {
  import.meta.hot.accept()
  import.meta.hot.dispose(() => {
    root.unmount()
  })
}

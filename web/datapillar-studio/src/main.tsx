import { StrictMode, useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { Toaster } from 'sonner'
import { useTranslation } from 'react-i18next'
import { router } from '@/router'
import { useI18nStore } from '@/state'
import { useAuthBootstrap } from '@/app/auth'
import { useThemeBootstrap } from '@/app/theme'
import { installToastCopyAction } from '@/app/toast'
import '@/app/i18n' // 初始化 i18n
import './index.css' // Tailwind CSS

installToastCopyAction()

/**
 * 应用初始化组件
 * 负责初始化主题、国际化、认证状态等全局状态
 */
export function App() {
  const { t, i18n } = useTranslation('common')
  useThemeBootstrap()
  useAuthBootstrap()

  useEffect(() => {
    // 初始化国际化系统
    useI18nStore.getState().initialize()
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

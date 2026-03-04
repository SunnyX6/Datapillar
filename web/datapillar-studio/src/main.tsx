import { StrictMode,useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { Toaster } from 'sonner'
import { useTranslation } from 'react-i18next'
import { router } from '@/router'
import { useI18nStore } from '@/state'
import { useAuthBootstrap } from '@/app/auth'
import { useThemeBootstrap } from '@/app/theme'
import { installToastCopyAction } from '@/app/toast'
import '@/app/i18n' // initialization i18n
import './index.css' // Tailwind CSS

installToastCopyAction()

/**
 * Application initialization component
 * Responsible for initializing the theme,internationalization,Global status such as authentication status
 */
export function App() {
 const { t,i18n } = useTranslation('common')
 useThemeBootstrap()
 useAuthBootstrap()

 useEffect(() => {
 // Initialize the internationalization system
 useI18nStore.getState().initialize()
 },[])

 // Listen for language changes,Dynamically update website title
 useEffect(() => {
 document.title = t('site.title')
 },[t,i18n.language])

 return (<>
 <RouterProvider router={router} />
 <Toaster position="top-right" richColors />
 </>)
}

const rootElement = document.getElementById('root')

if (!rootElement) {
 throw new Error('not found root element')
}

const root = ReactDOM.createRoot(rootElement)

root.render(<StrictMode>
 <App />
 </StrictMode>)

if (import.meta.hot) {
 import.meta.hot.accept()
 import.meta.hot.dispose(() => {
 root.unmount()
 })
}

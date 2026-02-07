import { StrictMode } from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { router } from './router'
import './lib/i18n'
import { useI18nStore } from './stores/i18nStore'
import './index.css'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('找不到 root 元素')
}

useI18nStore.getState().initialize()

ReactDOM.createRoot(rootElement).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>
)

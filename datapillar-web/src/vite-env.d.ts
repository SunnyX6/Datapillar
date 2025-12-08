/// <reference types="vite/client" />

// 环境变量类型定义
interface ImportMetaEnv {
  readonly VITE_APP_NAME: string
  readonly VITE_APP_ENV: string
  readonly VITE_ENABLE_DEBUG: string
  readonly VITE_API_TIMEOUT: string
  readonly VITE_FRONTEND_URL: string
  readonly VITE_AUTH_API_BASE_URL: string
  readonly VITE_AUTH_API_PATH: string
  readonly VITE_API_BASE_URL: string
  readonly VITE_API_PATH: string
  readonly VITE_XXL_JOB_API_BASE_URL: string
  readonly VITE_XXL_JOB_API_PATH: string
  readonly VITE_GRAVITINO_API_BASE_URL: string
  readonly VITE_GRAVITINO_API_PATH: string
  readonly VITE_AI_SERVICE_URL: string
  readonly VITE_ENCRYPTION_KEY: string
  readonly MODE: string
  readonly DEV: boolean
  readonly PROD: boolean
  readonly SSR: boolean
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// CSS Modules
declare module '*.module.css' {
  const classes: { readonly [key: string]: string }
  export default classes
}

declare module '*.module.scss' {
  const classes: { readonly [key: string]: string }
  export default classes
}

declare module '*.module.sass' {
  const classes: { readonly [key: string]: string }
  export default classes
}

// CSS
declare module '*.css'
declare module '*.scss'
declare module '*.sass'

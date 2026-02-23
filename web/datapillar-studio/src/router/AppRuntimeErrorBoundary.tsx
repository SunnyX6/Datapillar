import { Component, type ErrorInfo, type ReactNode } from 'react'
import { handleAppError, normalizeRuntimeError } from '@/lib/error-center'

interface AppRuntimeErrorBoundaryProps {
  children: ReactNode
}

interface AppRuntimeErrorBoundaryState {
  hasError: boolean
}

/**
 * 应用级错误边界：兜底捕获 React 渲染异常并交给全局错误中心。
 */
export class AppRuntimeErrorBoundary extends Component<
AppRuntimeErrorBoundaryProps,
AppRuntimeErrorBoundaryState
> {
  state: AppRuntimeErrorBoundaryState = {
    hasError: false
  }

  static getDerivedStateFromError(): AppRuntimeErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    handleAppError(
      normalizeRuntimeError(error, {
        module: 'runtime/react-error-boundary',
        isCoreRequest: true,
        raw: {
          componentStack: info.componentStack
        }
      })
    )
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-dvh items-center justify-center bg-slate-50 text-slate-500 dark:bg-[#020617]">
          <span className="text-xs tracking-[0.3em] uppercase">Loading...</span>
        </div>
      )
    }
    return this.props.children
  }
}

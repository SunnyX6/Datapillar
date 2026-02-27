import { Suspense, type ComponentType, type LazyExoticComponent } from 'react'

const loadingFallback = (
  <div className="flex min-h-dvh items-center justify-center bg-slate-50 text-slate-500 dark:bg-[#020617]">
    <span className="text-xs tracking-[0.3em] uppercase">Loading...</span>
  </div>
)

export function withSuspense(Component: LazyExoticComponent<ComponentType>) {
  return (
    <Suspense fallback={loadingFallback}>
      <Component />
    </Suspense>
  )
}

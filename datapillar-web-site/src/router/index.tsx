import { Suspense, lazy, type ComponentType, type LazyExoticComponent } from 'react'
import { createBrowserRouter } from 'react-router-dom'

const loadingFallback = (
  <div className="flex min-h-dvh items-center justify-center bg-slate-950 text-slate-400">
    <span className="text-xs tracking-[0.3em] uppercase">Loading...</span>
  </div>
)

const withSuspense = (Component: LazyExoticComponent<ComponentType>) => (
  <Suspense fallback={loadingFallback}>
    <Component />
  </Suspense>
)

const LazyHomePage = lazy(() => import('@/pages/HomePage').then(m => ({ default: m.HomePage })))
const LazyNotFoundPage = lazy(() => import('@/pages/not-found').then(m => ({ default: m.NotFoundPage })))

export const router = createBrowserRouter([
  {
    path: '/',
    element: withSuspense(LazyHomePage)
  },
  {
    path: '*',
    element: withSuspense(LazyNotFoundPage)
  }
])

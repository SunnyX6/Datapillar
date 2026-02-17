/**
 * 登录页面主组件
 *
 * 功能：
 * 1. 左右布局：左侧演示动画 + 右侧登录表单
 * 2. 响应式设计
 */

import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AppLayout, SplitGrid, useLayout } from '@/layouts/responsive'
import { ThemeToggle, LanguageToggle } from '@/components'
import { DemoCanvas } from './DemoCanvas'
import { LoginForm, LOGIN_FORM_BASE_HEIGHT, LOGIN_FORM_BASE_WIDTH } from './LoginForm'

/**
 * 登录页面
 */
export function LoginPage() {
  const [searchParams] = useSearchParams()
  const tenantCode = searchParams.get('tenantCode')

  // 禁用 body 滚动
  useEffect(() => {
    const originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = originalOverflow
    }
  }, [])

  const { ref: rightPaneRef, scale: formScale, ready: formReady } = useLayout<HTMLDivElement>({
    baseWidth: LOGIN_FORM_BASE_WIDTH,
    baseHeight: LOGIN_FORM_BASE_HEIGHT,
    scaleFactor: 1.0,
    minScale: 0.5,
    maxScale: 1.5
  })

  return (
    <AppLayout
      surface="dark"
      padding="none"
      align="stretch"
      maxWidthClassName="max-w-none"
      scrollBehavior="hidden"
      className="relative font-sans selection:bg-indigo-500/30 selection:text-indigo-200"
      contentClassName="flex flex-1 w-full overflow-hidden"
    >
      <SplitGrid
        columns={[0.65, 0.35]}
        stackAt="never"
        gapX="none"
        gapY="lg"
        className="flex-1 w-full"
        leftClassName="bg-[#02040a] overflow-hidden"
        rightClassName="bg-white dark:bg-[#020617] overflow-hidden"
        left={
          <div className="flex h-full w-full items-center justify-center">
            <DemoCanvas />
          </div>
        }
        right={
          <div
            ref={rightPaneRef}
            className="relative flex h-dvh max-h-dvh w-full items-center justify-center overflow-hidden bg-white dark:bg-[#020617]"
          >
            {/* 语言和主题切换按钮 - 固定在右上角 */}
            <div className="absolute top-4 right-4 z-30 flex items-center gap-3">
              <ThemeToggle />
              <LanguageToggle />
            </div>
            <LoginForm scale={formScale} ready={formReady} tenantCode={tenantCode} />
          </div>
        }
      />
    </AppLayout>
  )
}

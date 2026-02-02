/**
 * 邀请登录页面
 *
 * 功能：
 * 1. 承接邀请链接
 * 2. 引导前往登录
 */

import { useEffect, useMemo, type CSSProperties } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AppLayout, SplitGrid, useLayout } from '@/layouts/responsive'
import { BrandLogo, LanguageToggle, ThemeToggle } from '@/components'
import { Button } from '@/components/ui'
import { DemoCanvas } from '@/pages/login/DemoCanvas'
import { LOGIN_FORM_BASE_HEIGHT, LOGIN_FORM_BASE_WIDTH, LOGIN_FORM_WIDTH_CLASS } from '@/pages/login/LoginForm'
import { paddingClassMap } from '@/design-tokens/dimensions'
import { cn } from '@/lib/utils'

interface InvitePanelProps {
  scale: number
  ready: boolean
  tenantCode?: string | null
  inviteCode?: string | null
}

function InvitePanel({ scale, ready, tenantCode, inviteCode }: InvitePanelProps) {
  const navigate = useNavigate()
  const normalizedTenant = tenantCode?.trim() ?? ''
  const normalizedInvite = inviteCode?.trim() ?? ''
  const missingFields = [] as string[]

  if (!normalizedTenant) {
    missingFields.push('租户编码')
  }
  if (!normalizedInvite) {
    missingFields.push('邀请码')
  }

  const canProceed = missingFields.length === 0
  const panelStyle = useMemo(() => ({
    transform: `scale(${scale})`,
    transformOrigin: 'center center',
    opacity: ready ? 1 : 0,
    transition: 'opacity 0.3s ease'
  }), [scale, ready])

  const handleGoLogin = () => {
    const params = new URLSearchParams()
    if (normalizedTenant) {
      params.set('tenantCode', normalizedTenant)
    }
    if (normalizedInvite) {
      params.set('inviteCode', normalizedInvite)
    }
    const query = params.toString()
    navigate(query ? `/?${query}` : '/')
  }

  return (
    <div
      className="relative z-20 flex w-full h-full items-center justify-center translate-y-6 md:translate-y-8"
      style={{ '--login-form-base-height': `${LOGIN_FORM_BASE_HEIGHT}px` } as CSSProperties}
    >
      <div
        className={cn(
          'flex flex-col gap-4 md:gap-5 rounded-none w-full',
          paddingClassMap.md,
          LOGIN_FORM_WIDTH_CLASS
        )}
        style={panelStyle}
      >
        <div className="flex flex-col gap-1 md:gap-1.5 select-none mb-1 -ml-2 min-h-[60px]">
          <BrandLogo
            size={38}
            showText
            brandName="数蝶"
            brandTagline="吞噬原数，羽化真知"
            nameClassName="text-xl font-bold leading-tight tracking-tight text-indigo-600 dark:text-indigo-200"
          />
        </div>

        <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 p-5 flex flex-col gap-3">
          <div>
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">邀请链接已准备</h3>
            <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
              请前往登录页完成账号登录或企业SSO授权。
            </p>
          </div>
          <div className="space-y-1">
            {normalizedTenant ? (
              <div className="text-xs text-slate-500 dark:text-slate-400">
                已识别租户：{normalizedTenant}
              </div>
            ) : null}
            {normalizedInvite ? (
              <div className="text-xs text-slate-500 dark:text-slate-400">邀请码已确认</div>
            ) : null}
            {!canProceed ? (
              <div className="text-xs text-rose-500">缺少 {missingFields.join(' / ')}，请联系管理员重新发送邀请。</div>
            ) : null}
          </div>
          <Button
            size="normal"
            className="w-full"
            disabled={!canProceed}
            onClick={handleGoLogin}
          >
            前往登录
          </Button>
        </div>

        <div className="text-legal text-slate-400 dark:text-slate-500 text-center mt-3 select-none min-h-[48px]">
          <p className="font-mono tracking-wide">© {new Date().getFullYear()} 数蝶 吞噬原数，羽化真知</p>
          <p className="mt-1 text-slate-500 dark:text-slate-400">让AI来吐丝结茧</p>
        </div>
      </div>
    </div>
  )
}

export function InvitePage() {
  const [searchParams] = useSearchParams()
  const tenantCode = searchParams.get('tenantCode')
  const inviteCode = searchParams.get('inviteCode')

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
            <div className="absolute top-4 right-4 z-30 flex items-center gap-3">
              <ThemeToggle />
              <LanguageToggle />
            </div>
            <InvitePanel
              scale={formScale}
              ready={formReady}
              tenantCode={tenantCode}
              inviteCode={inviteCode}
            />
          </div>
        }
      />
    </AppLayout>
  )
}

/**
 * 邀请登录页面
 *
 * 功能：
 * 1. 承接邀请链接
 * 2. 引导前往登录
 */

import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { AppLayout } from '@/layouts/responsive'
import { BrandLogo, LanguageToggle, ThemeToggle } from '@/components'
import { Button, ResponsiveCard } from '@/components/ui'
import { cn } from '@/lib/utils'
import { TYPOGRAPHY } from '@/design-tokens/typography'

interface InvitePanelProps {
  tenantCode?: string | null
  inviteCode?: string | null
}

function InvitePanel({ tenantCode, inviteCode }: InvitePanelProps) {
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
  const statusTitle = canProceed ? '邀请已确认' : '邀请信息缺失'
  const statusDescription = canProceed
    ? '打开此页面即代表接受企业邀请，可继续登录或完成 SSO 授权。'
    : `缺少 ${missingFields.join(' / ')}，请联系管理员重新发送邀请链接。`
  const StatusIcon = canProceed ? CheckCircle2 : AlertTriangle
  const statusToneClassName = canProceed
    ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-500'
    : 'border-amber-500/30 bg-amber-500/10 text-amber-500'

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
    <div className="relative z-20 flex w-full items-center justify-center px-6 py-12">
      <div className="flex w-full max-w-3xl flex-col items-center gap-6">
        <BrandLogo
          size={44}
          showText
          brandName="数蝶"
          brandTagline="吞噬原数，羽化真知"
          nameClassName="text-2xl font-bold leading-tight tracking-tight text-white"
          taglineClassName="text-xs text-slate-300"
        />

        <ResponsiveCard
          size="wide"
          padding="lg"
          border={false}
          shadow={false}
          className="relative w-full @container overflow-hidden border border-white/10 bg-white/95 text-slate-900 shadow-[0_30px_90px_-35px_rgba(15,23,42,0.75)] backdrop-blur dark:bg-[#0B1120]/90 dark:text-white"
        >
          <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-indigo-500 via-sky-500 to-emerald-400" />

          <div className="flex flex-col gap-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.3em] text-slate-400">企业邀请</p>
                <h1 className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">企业邀请确认</h1>
                <p className="mt-2 text-sm text-slate-500 dark:text-slate-300">{statusDescription}</p>
              </div>
              <div
                className={cn(
                  'inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold',
                  statusToneClassName
                )}
              >
                <StatusIcon size={14} />
                {statusTitle}
              </div>
            </div>

            <div className="grid gap-4 @md:grid-cols-2">
              <div className="rounded-xl border border-slate-200/80 bg-slate-50/80 p-4 dark:border-slate-700/60 dark:bg-slate-800/70">
                <p className={`${TYPOGRAPHY.legal} font-semibold uppercase tracking-[0.2em] text-slate-400`}>租户</p>
                <p
                  className={cn(
                    'mt-2 text-sm font-semibold',
                    normalizedTenant ? 'text-slate-900 dark:text-white' : 'text-rose-500'
                  )}
                >
                  {normalizedTenant || '未识别'}
                </p>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">由管理员配置的企业租户编码</p>
              </div>
              <div className="rounded-xl border border-slate-200/80 bg-slate-50/80 p-4 dark:border-slate-700/60 dark:bg-slate-800/70">
                <p className={`${TYPOGRAPHY.legal} font-semibold uppercase tracking-[0.2em] text-slate-400`}>邀请码</p>
                <p
                  className={cn(
                    'mt-2 text-sm font-semibold',
                    normalizedInvite ? 'text-slate-900 dark:text-white' : 'text-rose-500'
                  )}
                >
                  {normalizedInvite ? '已确认' : '未识别'}
                </p>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">用于绑定到企业成员身份</p>
              </div>
            </div>

            <div className="flex flex-col gap-3">
              <Button
                size="normal"
                className="w-full"
                disabled={!canProceed}
                onClick={handleGoLogin}
              >
                前往登录
              </Button>
              <p className="text-center text-xs text-slate-400 dark:text-slate-500">
                若非本人操作，请直接关闭页面以避免误绑定。
              </p>
            </div>
          </div>
        </ResponsiveCard>

        <div className="text-legal text-slate-400 text-center select-none min-h-[48px]">
          <p className="font-mono tracking-wide">© {new Date().getFullYear()} 数蝶 吞噬原数，羽化真知</p>
          <p className="mt-1 text-slate-500">让AI来吐丝结茧</p>
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

  return (
    <AppLayout
      surface="dark"
      padding="none"
      align="center"
      maxWidthClassName="max-w-none"
      scrollBehavior="hidden"
      className="relative overflow-hidden bg-[#05070f] font-sans text-white selection:bg-indigo-500/30 selection:text-indigo-200"
      contentClassName="flex flex-1 w-full"
    >
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,#1e1b4b,transparent_55%)] opacity-70" />
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_bottom,#0ea5e9,transparent_45%)] opacity-40" />
        <div className="absolute inset-0 bg-[linear-gradient(to_right,rgba(15,23,42,0.45)_1px,transparent_1px),linear-gradient(to_bottom,rgba(15,23,42,0.45)_1px,transparent_1px)] bg-[size:56px_56px] opacity-30" />
      </div>

      <div className="relative flex min-h-dvh w-full items-center justify-center">
        <div className="absolute top-6 right-6 z-30 flex items-center gap-3">
          <ThemeToggle />
          <LanguageToggle />
        </div>
        <InvitePanel
          tenantCode={tenantCode}
          inviteCode={inviteCode}
        />
      </div>
    </AppLayout>
  )
}

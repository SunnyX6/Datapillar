/**
 * 邀请注册页面
 *
 * 功能：
 * 1. 承接企业邀请链接
 * 2. 保持登录页左侧演示画布一致
 * 3. 右侧展示邀请说明和注册信息表单
 */

import { useEffect, useMemo, useState, type CSSProperties, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Lock } from 'lucide-react'
import { toast } from 'sonner'
import { AppLayout, SplitGrid, useLayout } from '@/layouts/responsive'
import { LanguageToggle, ThemeToggle } from '@/components'
import { Button } from '@/components/ui'
import { DemoCanvas } from '@/pages/login/DemoCanvas'
import { paddingClassMap } from '@/design-tokens/dimensions'
import { getInvitationByCode, registerInvitation } from '@/services/studioInvitationService'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/authStore'
import { isTenantSelectResult } from '@/types/auth'
import type { InvitationDetailResponse } from '@/types/studio/tenant'
import { resolveFirstAccessibleRoute } from '@/router/access/routeAccess'

const INVITE_FORM_BASE_WIDTH = 400
const INVITE_FORM_BASE_HEIGHT = 700
const INVITE_FORM_WIDTH_CLASS = 'w-[400px] max-w-[400px]'

const FORM_LABEL_CLASS = 'mb-1.5 block text-xs font-semibold tracking-[0.05em] text-slate-500 dark:text-slate-400'
const FORM_INPUT_BASE_CLASS =
  'select-text w-full rounded-xl border border-slate-200/60 dark:border-slate-800/60 bg-white dark:bg-[#0B1120] px-4 py-2.5 text-sm text-slate-900 dark:text-white placeholder:text-[11px] placeholder-slate-400 dark:placeholder-slate-600 selection:bg-indigo-500/20 selection:text-slate-900 dark:selection:bg-indigo-400/35 dark:selection:text-white transition-all focus:border-indigo-400/70 focus:ring-[0.5px] focus:ring-indigo-400/60 focus:outline-none'

interface InviteFormProps {
  inviteCode?: string | null
  scale: number
  ready: boolean
}

const INVITATION_STATUS_PENDING = 0
const INVITATION_STATUS_ACCEPTED = 1
const INVITATION_STATUS_EXPIRED = 2
const INVITATION_STATUS_CANCELLED = 3

function resolveInvitationStatusMessage(status: number): string {
  if (status === INVITATION_STATUS_ACCEPTED) {
    return '该邀请已被使用，请联系管理员重新生成邀请链接。'
  }
  if (status === INVITATION_STATUS_EXPIRED) {
    return '该邀请已过期，请联系管理员重新生成邀请链接。'
  }
  if (status === INVITATION_STATUS_CANCELLED) {
    return '该邀请已失效，请联系管理员重新生成邀请链接。'
  }
  return '该邀请不可用，请联系管理员重新生成邀请链接。'
}

function InviteForm({
  inviteCode,
  scale,
  ready
}: InviteFormProps) {
  const { t } = useTranslation('login')
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isLoadingInvitation, setIsLoadingInvitation] = useState(false)
  const [invitationDetail, setInvitationDetail] = useState<InvitationDetailResponse | null>(null)
  const [invitationError, setInvitationError] = useState('')

  const normalizedInvite = inviteCode?.trim() ?? ''
  const canProceed = normalizedInvite.length > 0
  const inviterName = invitationDetail?.inviterName?.trim() ?? ''
  const roleName = invitationDetail?.roleName?.trim() ?? ''
  const invitationStatus = invitationDetail?.status ?? null
  const isInvitationReady =
    invitationStatus === INVITATION_STATUS_PENDING && !invitationError
  const canSubmit = canProceed && isInvitationReady && !isSubmitting && !isLoadingInvitation
  const inviterInitials = useMemo(() => {
    const initials = inviterName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('')
    return initials || 'IN'
  }, [inviterName])
  const hasInviteDescription = inviterName.trim().length > 0 && roleName.trim().length > 0
  const invitationStatusMessage = useMemo(() => {
    if (invitationStatus === null || invitationStatus === INVITATION_STATUS_PENDING) {
      return ''
    }
    return resolveInvitationStatusMessage(invitationStatus)
  }, [invitationStatus])
  const panelStyle = useMemo(() => ({
    transform: `scale(${scale})`,
    transformOrigin: 'center center',
    opacity: ready ? 1 : 0,
    transition: 'opacity 0.3s ease'
  }), [ready, scale])

  useEffect(() => {
    if (!canProceed) {
      setInvitationDetail(null)
      setInvitationError('')
      setIsLoadingInvitation(false)
      return
    }

    let cancelled = false
    setIsLoadingInvitation(true)
    setInvitationError('')

    const loadInvitation = async () => {
      try {
        const response = await getInvitationByCode(normalizedInvite)
        if (cancelled) {
          return
        }
        setInvitationDetail(response)
      } catch (error) {
        if (cancelled) {
          return
        }
        setInvitationDetail(null)
        setInvitationError('邀请信息不存在或已失效，请联系管理员重新发送邀请。')
        const message = error instanceof Error ? error.message : String(error)
        toast.error(message || '邀请信息加载失败，请稍后重试。')
      } finally {
        if (!cancelled) {
          setIsLoadingInvitation(false)
        }
      }
    }

    void loadInvitation()
    return () => {
      cancelled = true
    }
  }, [canProceed, normalizedInvite])

  const handleAccept = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canSubmit) {
      return
    }
    const formData = new FormData(event.currentTarget)
    const submittedEmail = String(formData.get('email') ?? '').trim()
    const submittedUsername = String(formData.get('username') ?? '').trim()
    const submittedPassword = String(formData.get('password') ?? '').trim()

    if (!submittedEmail || !submittedUsername || !submittedPassword) {
      toast.error('请完整填写工作邮箱、用户名和密码。')
      return
    }
    try {
      setIsSubmitting(true)
      await registerInvitation({
        inviteCode: normalizedInvite,
        email: submittedEmail,
        username: submittedUsername,
        password: submittedPassword
      })
      try {
        await useAuthStore.getState().logout()
      } catch (logoutError) {
        void logoutError
      }
      const loginResult = await useAuthStore.getState().login(
        submittedUsername,
        submittedPassword,
        false,
      )
      if (isTenantSelectResult(loginResult)) {
        toast.error('账号存在多个租户，请先从登录页完成租户选择。')
        return
      }
      const targetPath = resolveFirstAccessibleRoute(loginResult.menus)
      if (!targetPath) {
        toast.error('当前账号暂无可访问页面，请联系管理员配置权限。')
        return
      }
      navigate(targetPath, { replace: true })
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(message || '接受邀请失败，请稍后重试。')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form
      onSubmit={handleAccept}
      className={cn(
        'relative z-20 flex w-full min-h-[var(--invite-form-base-height)] flex-col gap-3 md:gap-4 lg:gap-5 rounded-none translate-y-14 md:translate-y-16',
        paddingClassMap.md,
        INVITE_FORM_WIDTH_CLASS
      )}
      style={{
        ...panelStyle,
        '--invite-form-base-height': `${INVITE_FORM_BASE_HEIGHT}px`
      } as CSSProperties}
    >
      <div className="mx-auto mt-1 flex items-center justify-center">
        <div className="relative flex items-center">
          <div className="z-10 flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-sm font-semibold text-white shadow-sm">
            {inviterInitials}
          </div>
          <div className="-ml-3 flex h-14 w-14 items-center justify-center rounded-full border-4 border-white bg-indigo-100 text-sm font-semibold text-indigo-600">
            You
          </div>
        </div>
      </div>

      <div className="mt-3 text-center">
        {isLoadingInvitation ? (
          <p className="text-sm leading-6 text-slate-500 dark:text-slate-400">
            正在校验邀请信息，请稍候。
          </p>
        ) : hasInviteDescription ? (
          <p className="text-sm leading-6 text-slate-500 dark:text-slate-400">
            <span className="font-semibold text-slate-700 dark:text-slate-300">{inviterName}</span> 邀请你加入{' '}
            <span className="font-semibold text-slate-700 dark:text-slate-300">{roleName}</span> 角色。加入后，你将默认获得{' '}
            <span className="font-semibold text-slate-700 dark:text-slate-300">{roleName}</span> 权限。
          </p>
        ) : invitationError ? (
          <p className="text-sm leading-6 text-slate-500 dark:text-slate-400">
            邀请信息加载失败，请联系管理员重新发送邀请链接。
          </p>
        ) : (
          <p className="text-sm leading-6 text-slate-500 dark:text-slate-400">
            你收到一个成员邀请。完成注册后将自动加入对应角色并获得默认权限。
          </p>
        )}
        {!canProceed ? (
          <p className="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-500">
            邀请链接缺少邀请码，请联系管理员重新发送邀请。
          </p>
        ) : null}
        {canProceed && !isLoadingInvitation && invitationError ? (
          <p className="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-500">
            {invitationError}
          </p>
        ) : null}
        {canProceed && !isLoadingInvitation && !invitationError && invitationStatusMessage ? (
          <p className="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-500">
            {invitationStatusMessage}
          </p>
        ) : null}
      </div>

      <div className="mt-1 space-y-3.5">
        <div>
          <label className={FORM_LABEL_CLASS}>工作邮箱</label>
          <input
            name="email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="请输入工作邮箱"
            className={FORM_INPUT_BASE_CLASS}
            required
          />
        </div>

        <div>
          <label className={FORM_LABEL_CLASS}>用户名</label>
          <input
            name="username"
            type="text"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="请输入用户名"
            className={FORM_INPUT_BASE_CLASS}
            required
          />
        </div>

        <div>
          <label className={FORM_LABEL_CLASS}>设置密码</label>
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
              <Lock size={16} />
            </div>
            <input
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="创建登录密码"
              className={cn(FORM_INPUT_BASE_CLASS, 'pl-10')}
              required
            />
          </div>
        </div>
      </div>

      <Button
        type="submit"
        size="normal"
        disabled={!canSubmit}
        className={cn(
          'mt-2 w-full rounded-lg py-2.5 text-sm font-semibold text-white shadow-md shadow-indigo-500/20',
          'flex items-center justify-center gap-2 transition-colors duration-150',
          'bg-indigo-500 hover:bg-indigo-600 active:bg-indigo-700 disabled:bg-indigo-400',
          'dark:bg-indigo-500 dark:hover:bg-indigo-600 dark:active:bg-indigo-700 dark:disabled:bg-indigo-400',
          'focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-400'
        )}
      >
        <span>{isSubmitting ? '提交中...' : '接受邀请并加入'}</span>
        <ArrowRight size={16} />
      </Button>

      <div className="text-legal text-slate-400 dark:text-slate-500 text-center mt-3 select-none min-h-[48px]">
        <p className="font-mono tracking-wide">© {new Date().getFullYear()} {t('brand.name')} {t('brand.tagline')}</p>
        <p className="mt-1 text-slate-500 dark:text-slate-400">{t('brand.slogan')}</p>
      </div>
    </form>
  )
}

export function InvitePage() {
  const [searchParams] = useSearchParams()
  const inviteCode = searchParams.get('inviteCode')
  const { ref: rightPaneRef, scale: formScale, ready: formReady } = useLayout<HTMLDivElement>({
    baseWidth: INVITE_FORM_BASE_WIDTH,
    baseHeight: INVITE_FORM_BASE_HEIGHT,
    scaleFactor: 1.0,
    minScale: 0.5,
    maxScale: 1.5
  })

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
            <InviteForm
              inviteCode={inviteCode}
              scale={formScale}
              ready={formReady}
            />
          </div>
        }
      />
    </AppLayout>
  )
}

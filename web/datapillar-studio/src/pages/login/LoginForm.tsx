/**
 * 登录表单组件
 *
 * 功能：
 * 1. 用户名/密码登录
 * 2. 主题切换
 * 3. 表单验证
 * 4. 错误处理
 */

import { useEffect, useMemo, useRef, useState, useCallback, FormEvent, type CSSProperties, type KeyboardEvent, useId } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, UserRound, Lock, Loader2, Eye, EyeOff, Check, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { useThemeMode, useAuthStore } from '@/stores'
import { BrandLogo, ThirdPartyIcons } from '@/components'
import { Button, Tooltip } from '@/components/ui'
import { paddingClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import { isTenantSelectResult, type LoginResult, type TenantOption } from '@/types/auth'
import { resolveFirstAccessibleRoute } from '@/router/access/routeAccess'
import { WorkspaceSelectPanel } from './WorkspaceSelect'

/**
 * 尺寸配置系统
 * 通过调整 SCALE 值来统一控制所有组件的大小
 * SCALE = 1.0 (默认), 1.1 (放大10%), 0.9 (缩小10%)
 */
const SCALE = 1.3 // 调整这个值来放大或缩小所有组件

// 表单基础尺寸（用于自适应缩放）
export const LOGIN_FORM_BASE_WIDTH = 400
export const LOGIN_FORM_BASE_HEIGHT = 700
export const LOGIN_FORM_WIDTH_CLASS = 'w-[400px] max-w-[400px]'

const SIZES = {
  // 输入框
  input: {
    py: SCALE >= 1.15 ? 'py-2.5' : SCALE >= 1.0 ? 'py-2' : 'py-1.5',
    px: SCALE >= 1.15 ? 'pl-12 pr-10' : SCALE >= 1.0 ? 'pl-12 pr-10' : 'pl-10 pr-8',
    text: SCALE >= 1.15 ? 'text-sm' : SCALE >= 1.0 ? 'text-sm' : 'text-xs',
    iconSize: Math.round(12 * SCALE),
    iconPadding: SCALE >= 1.15 ? 'pl-3.5' : 'pl-3.5'
  },
  // 按钮
  button: {
    py: SCALE >= 1.15 ? 'py-2.5' : SCALE >= 1.0 ? 'py-2' : 'py-1.5',
    text: SCALE >= 1.15 ? 'text-sm' : SCALE >= 1.0 ? 'text-sm' : 'text-xs',
    iconSize: Math.round(16 * SCALE)
  },
  // Logo
  logo: {
    size: SCALE >= 1.15 ? 'w-9 h-9' : SCALE >= 1.0 ? 'w-9 h-9' : 'w-8 h-8',
    iconSize: Math.round(18 * SCALE)
  },
  // 标题
  title: {
    text: SCALE >= 1.15 ? 'text-xl' : SCALE >= 1.0 ? 'text-xl' : 'text-lg'
  },
  // 复选框
  checkbox: {
    size: SCALE >= 1.15 ? 'h-3.5 w-3.5' : SCALE >= 1.0 ? 'h-3 w-3' : 'h-2.5 w-2.5',
    iconSize: Math.round(10 * SCALE)
  }
}

/**
 * 登录表单组件
 */
interface LoginFormProps {
  scale: number
  ready: boolean
  tenantCode?: string | null
}

interface LoginFormContentProps {
  tenantCode?: string | null
  onSsoClick?: () => void
  onLoginSuccess?: (result: LoginResult) => void
}

type LoginTab = 'account' | 'sso'

type SsoProviderKey = 'dingtalk' | 'wecom' | 'lark'

const PROVIDER_CONFIG: Array<{
  key: SsoProviderKey
  nameKey: string
  descKey: string
  icon: keyof typeof ThirdPartyIcons
  enabled: boolean
}> = [
  { key: 'dingtalk', nameKey: 'sso.providers.dingtalk.name', descKey: 'sso.providers.dingtalk.desc', icon: 'DingTalk', enabled: false },
  { key: 'wecom', nameKey: 'sso.providers.wecom.name', descKey: 'sso.providers.wecom.desc', icon: 'WeCom', enabled: false },
  { key: 'lark', nameKey: 'sso.providers.lark.name', descKey: 'sso.providers.lark.desc', icon: 'Lark', enabled: false }
]

export function LoginFormContent({ tenantCode, onSsoClick, onLoginSuccess }: LoginFormContentProps) {
  const { t } = useTranslation('login')
  const mode = useThemeMode()
  const navigate = useNavigate()
  const rememberId = useId()

  const login = useAuthStore((state) => state.login)
  const loading = useAuthStore((state) => state.loading)
  const lastUsername = useAuthStore((state) => state.lastUsername ?? '')
  const lastRememberMe = useAuthStore((state) => state.lastRememberMe ?? false)
  const usernameRef = useRef<HTMLInputElement | null>(null)
  const passwordRef = useRef<HTMLInputElement | null>(null)

  // 表单状态
  const [username, setUsername] = useState(() => (lastRememberMe ? lastUsername : ''))
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(() => lastRememberMe)
  const [showPassword, setShowPassword] = useState(false)
  const inputBaseClass =
    `select-text w-full bg-white dark:bg-[#0B1120] border border-slate-200/60 dark:border-slate-800/60 rounded-xl ${SIZES.input.py} ${SIZES.input.px} ${SIZES.input.text} text-slate-900 dark:text-white placeholder:text-[11px] placeholder-slate-400 dark:placeholder-slate-600 selection:bg-indigo-500/20 selection:text-slate-900 dark:selection:bg-indigo-400/35 dark:selection:text-white focus:outline-none focus:border-indigo-400/70 focus:ring-[0.5px] focus:ring-indigo-400/60 transition-all`
  const labelTypography = 'tracking-[0.05em]'
  const rememberHintContent = (
    <div className="flex flex-col gap-0.5">
      <span className={TYPOGRAPHY.micro}>{t('form.rememberHintDefault')}</span>
      <span className={TYPOGRAPHY.micro}>{t('form.rememberHintRemember')}</span>
    </div>
  )
  const successToastStyle =
    mode === 'dark'
      ? {
          background: '#0F172A',
          color: '#E2E8F0',
          border: '1px solid rgba(129, 140, 248, 0.5)',
          boxShadow: '0 18px 45px rgba(15, 23, 42, 0.55)'
        }
      : {
          background: '#EEF2FF',
          color: '#312E81',
          border: '1px solid rgba(79, 70, 229, 0.15)',
          boxShadow: '0 12px 30px rgba(79, 70, 229, 0.15)'
        }

  /**
   * 处理登录提交
   */
  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()

    // 表单验证
    if (!username.trim()) {
      toast.error(t('form.username.required'))
      return
    }

    if (!password) {
      toast.error(t('form.password.required'))
      return
    }

    try {
      const result = await login(username, password, rememberMe, { tenantCode: tenantCode ?? undefined })
      if (!isTenantSelectResult(result)) {
        toast.success(t('form.success'), { style: successToastStyle })
      }
      if (onLoginSuccess) {
        onLoginSuccess(result)
        return
      }
      if (!isTenantSelectResult(result)) {
        const targetPath = resolveFirstAccessibleRoute(result.menus)
        if (!targetPath) {
          toast.error('当前账号暂无可访问页面，请联系管理员配置权限。')
          return
        }
        navigate(targetPath)
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '登录失败，请重试')
    }
  }

  const handleInputTab = useCallback((target: 'username' | 'password') => (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Tab') {
      return
    }
    event.preventDefault()
    if (target === 'username') {
      passwordRef.current?.focus()
      return
    }
    usernameRef.current?.focus()
  }, [])

  return (
    <div className="select-none">
      <form onSubmit={handleSubmit} autoComplete="on" className="flex flex-col gap-3 md:gap-3.5 select-none">
        {/* 用户名输入框 */}
        <div className="flex flex-col gap-1.5 md:gap-2">
          <div className="flex items-center justify-between">
            <span className={`text-xs font-semibold text-slate-500 dark:text-slate-400 ${labelTypography}`}>
              {t('form.username.label')}
            </span>
          </div>
          <div className="relative group">
            <div className={`absolute inset-y-0 left-0 ${SIZES.input.iconPadding} flex items-center pointer-events-none text-slate-400 dark:text-slate-500 group-focus-within:text-indigo-500 transition-colors`}>
              <UserRound size={SIZES.input.iconSize} />
            </div>
            <input
              type="text"
              name="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              ref={usernameRef}
              onKeyDown={handleInputTab('username')}
              className={inputBaseClass}
              placeholder={t('form.username.placeholder')}
              disabled={loading}
            />
          </div>
        </div>

        {/* 密码输入框 */}
        <div className="flex flex-col gap-1.5 md:gap-2">
          <div className="flex justify-between items-center">
            <label className={`text-xs font-semibold text-slate-500 dark:text-slate-400 ${labelTypography}`}>
              {t('form.password.label')}
            </label>
            <a href="#" tabIndex={-1} className="text-xs font-semibold text-indigo-500 dark:text-indigo-400 hover:text-indigo-300">
              {t('form.forgot')}
            </a>
          </div>
          <div className="relative group">
            <div className={`absolute inset-y-0 left-0 ${SIZES.input.iconPadding} flex items-center pointer-events-none text-slate-400 dark:text-slate-500 group-focus-within:text-indigo-500 transition-colors`}>
              <Lock size={SIZES.input.iconSize} />
            </div>
            <input
              type={showPassword ? 'text' : 'password'}
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              ref={passwordRef}
              onKeyDown={handleInputTab('password')}
              className={inputBaseClass}
              placeholder={t('form.password.placeholder')}
              disabled={loading}
            />
              <Button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                variant="ghost"
                size="tiny"
                className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-colors"
                aria-label={showPassword ? '隐藏密码' : '显示密码'}
                tabIndex={-1}
              >
              {showPassword ? <EyeOff size={SIZES.input.iconSize} /> : <Eye size={SIZES.input.iconSize} />}
            </Button>
          </div>
        </div>

        {/* 记住我复选框 */}
        <div className={`flex items-center gap-2.5 select-none ${loading ? 'opacity-60' : ''}`}>
          <div className="relative inline-flex items-start">
            <label
              htmlFor={rememberId}
              className={`flex items-center gap-2.5 group ${loading ? 'cursor-not-allowed' : 'cursor-pointer'}`}
            >
              <input
                id={rememberId}
                type="checkbox"
                checked={rememberMe}
                onChange={() => setRememberMe(!rememberMe)}
                disabled={loading}
                className="sr-only"
              />
              <span
                className={`flex ${SIZES.checkbox.size} items-center justify-center rounded-[5px] border transition-all duration-200 ${
                  rememberMe
                    ? 'border-transparent bg-gradient-to-br from-indigo-500 to-purple-500 text-white shadow-[0_8px_14px_rgba(79,70,229,0.35)]'
                    : 'border-slate-400/70 dark:border-slate-600 bg-transparent text-transparent group-hover:border-indigo-400/70 dark:group-hover:border-indigo-500/60'
                }`}
              >
                {rememberMe ? <Check size={SIZES.checkbox.iconSize} strokeWidth={2} /> : null}
              </span>
              <span className={`text-xs font-semibold text-slate-500 dark:text-slate-400 ${labelTypography} group-hover:text-slate-900 dark:group-hover:text-slate-300 transition-colors`}>
                {t('form.remember')}
              </span>
            </label>
            <Tooltip content={rememberHintContent} side="top" disabled={loading} className="absolute -top-1 -right-5">
              <button
                type="button"
                aria-label={t('form.remember')}
                disabled={loading}
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
                className={cn(
                  'flex h-3.5 w-3.5 items-center justify-center rounded-full border border-slate-300/70 text-slate-400 dark:border-slate-600 dark:text-slate-500 transition-colors',
                  loading
                    ? 'cursor-not-allowed'
                    : 'cursor-help hover:text-slate-600 dark:hover:text-slate-300 hover:border-slate-400/80 dark:hover:border-slate-500'
                )}
              >
                <span className={TYPOGRAPHY.micro}>?</span>
              </button>
            </Tooltip>
          </div>
        </div>

        {/* 登录按钮 */}
        <Button
          type="submit"
          disabled={loading}
          // 登录页按钮不应该继承 Button 的默认 header 尺寸（含 @md:py-* 容器断点），否则会覆盖这里自定义的 py-*。
          size="normal"
          className={cn(
            'w-full text-white rounded-lg shadow-md shadow-indigo-500/20 disabled:cursor-not-allowed',
            'flex items-center justify-center gap-2',
            'bg-indigo-500 hover:bg-indigo-600 active:bg-indigo-700 disabled:bg-indigo-400',
            'dark:bg-indigo-500 dark:hover:bg-indigo-600 dark:active:bg-indigo-700 dark:disabled:bg-indigo-400',
            'transition-colors duration-150',
            SIZES.button.text,
            SIZES.button.py,
            'font-semibold',
            '-mt-1',
            'focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-400'
          )}
        >
          {loading ? (
            <>
              <Loader2 size={SIZES.button.iconSize} className="animate-spin" />
              <span>{t('form.submit')}...</span>
            </>
          ) : (
            <>
              <span>{t('form.submit')}</span>
              <ArrowRight size={SIZES.button.iconSize} />
            </>
          )}
        </Button>
        <div className="mt-4">
          <div className="flex items-center gap-3">
            <div className="flex-1 border-t border-slate-100 dark:border-white/10" />
            <span className={`${TYPOGRAPHY.legal} text-slate-400 dark:text-slate-500 font-medium`}>{t('sso.otherMethods')}</span>
            <div className="flex-1 border-t border-slate-100 dark:border-white/10" />
          </div>
          {onSsoClick ? (
            <div className="flex justify-center mt-3">
              <button
                type="button"
                onClick={onSsoClick}
                disabled={loading}
                className={cn(
                  TYPOGRAPHY.legal,
                  'group inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 font-semibold text-slate-700',
                  'hover:border-indigo-300 hover:bg-indigo-50/40 hover:text-slate-900',
                  'dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800/80',
                  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-400',
                  loading ? 'cursor-not-allowed opacity-60' : 'transition-colors'
                )}
              >
                <span className="flex items-center gap-1">
                  {PROVIDER_CONFIG.map((provider) => {
                    const Icon = ThirdPartyIcons[provider.icon]
                    return (
                      <span
                        key={provider.key}
                        className="flex h-4.5 w-4.5 items-center justify-center rounded-full border border-slate-200 bg-white shadow-sm dark:border-slate-600 dark:bg-slate-900"
                      >
                        <span className="flex items-center justify-center scale-[0.58]">
                          <Icon />
                        </span>
                      </span>
                    )
                  })}
                </span>
                <span>{t('sso.quickLogin')}</span>
                <ChevronRight size={16} className="text-slate-400 group-hover:text-slate-500 dark:text-slate-500 dark:group-hover:text-slate-300" />
              </button>
            </div>
          ) : null}
        </div>
      </form>
    </div>
  )
}

export function LoginForm({ scale, ready, tenantCode }: LoginFormProps) {
  const { t } = useTranslation('login')
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState<LoginTab>('account')
  const [activeProvider, setActiveProvider] = useState<SsoProviderKey | null>(null)
  const [tenantOptions, setTenantOptions] = useState<TenantOption[]>([])
  const [tenantSelectLoading, setTenantSelectLoading] = useState(false)
  const loginTenant = useAuthStore((state) => state.loginTenant)
  const authLoading = useAuthStore((state) => state.loading)

  const panelStyle = useMemo(() => ({
    transform: `scale(${scale})`,
    transformOrigin: 'center center',
    opacity: ready ? 1 : 0,
    transition: 'opacity 0.3s ease'
  }), [scale, ready])

  const showWorkspaceSelect = tenantOptions.length > 0

  const handleLoginResult = useCallback((result: LoginResult) => {
    if (isTenantSelectResult(result)) {
      setActiveTab('account')
      setActiveProvider(null)
      setTenantOptions(result.tenants)
      return
    }
    const targetPath = resolveFirstAccessibleRoute(result.menus)
    if (!targetPath) {
      toast.error('当前账号暂无可访问页面，请联系管理员配置权限。')
      return
    }
    setTenantOptions([])
    navigate(targetPath)
  }, [navigate])

  const handleWorkspaceSelect = useCallback(async (tenantId: number) => {
    try {
      setTenantSelectLoading(true)
      const result = await loginTenant(tenantId)
      if (isTenantSelectResult(result)) {
        toast.error('租户选择未完成，请重试。')
        return
      }
      const targetPath = resolveFirstAccessibleRoute(result.menus)
      if (!targetPath) {
        toast.error('当前账号暂无可访问页面，请联系管理员配置权限。')
        return
      }
      setTenantOptions([])
      navigate(targetPath)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '登录失败，请重试')
    } finally {
      setTenantSelectLoading(false)
    }
  }, [loginTenant, navigate])

  const handleWorkspaceBack = useCallback(() => {
    setTenantOptions([])
    setTenantSelectLoading(false)
  }, [])

  useEffect(() => {
    if (activeTab === 'account') {
      setActiveProvider(null)
    }
  }, [activeTab])
  const showListPanel = !showWorkspaceSelect && activeTab === 'sso' && activeProvider === null

  return (
    <div
      className="relative z-20 flex w-full h-full items-center justify-center translate-y-28 md:translate-y-32"
      style={{ '--login-form-base-height': `${LOGIN_FORM_BASE_HEIGHT}px` } as CSSProperties}
    >
      <div
        className={cn(
          'relative flex flex-col gap-3 md:gap-4 lg:gap-5 rounded-none w-full',
          showWorkspaceSelect ? 'h-[var(--login-form-base-height)]' : 'min-h-[var(--login-form-base-height)]',
          paddingClassMap.md,
          LOGIN_FORM_WIDTH_CLASS
        )}
        style={panelStyle}
      >
        {showWorkspaceSelect ? (
          <WorkspaceSelectPanel
            tenants={tenantOptions}
            onBack={handleWorkspaceBack}
            onSelect={handleWorkspaceSelect}
            loading={tenantSelectLoading}
          />
        ) : (
          <>
            {/* 品牌区域 - 固定高度防止中英文切换时布局抖动 */}
            <div className="flex flex-col gap-1 md:gap-1.5 select-none mb-1 md:mb-2 -ml-2 min-h-[60px]">
              <BrandLogo
                size={Math.round(38 * SCALE)}
                showText
                brandName={t('brand.name')}
                brandTagline={t('brand.tagline')}
                nameClassName={SCALE >= 1 ? 'text-xl font-bold leading-tight tracking-tight text-indigo-600 dark:text-indigo-200' : 'text-lg font-bold leading-tight tracking-tight text-indigo-600 dark:text-indigo-200'}
              />
            </div>

            {activeTab === 'account' ? (
              <LoginFormContent
                tenantCode={tenantCode}
                onSsoClick={() => setActiveTab('sso')}
                onLoginSuccess={handleLoginResult}
              />
            ) : (
              <div className="flex flex-col gap-3">
                <div className="flex items-start justify-between">
                  <div>
                    <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('sso.panel.title')}</h3>
                    <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">{t('sso.panel.subtitle')}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setActiveTab('account')}
                    className="text-xs font-semibold text-indigo-500 hover:text-indigo-600 dark:text-indigo-300 dark:hover:text-indigo-200 transition-colors"
                  >
                    {t('sso.panel.accountLogin')}
                  </button>
                </div>

                <div className="relative">
                  <div
                    aria-hidden={!showListPanel}
                    className={cn(
                      'h-[200px] overflow-y-auto overscroll-contain scrollbar-invisible py-1 flex flex-col transition-opacity',
                      showListPanel ? 'opacity-100' : 'opacity-0 pointer-events-none'
                    )}
                  >
                    {PROVIDER_CONFIG.map((provider) => {
                      const Icon = ThirdPartyIcons[provider.icon]
                      const disabled = !provider.enabled || authLoading
                      return (
                        <button
                          key={provider.key}
                          type="button"
                          onClick={() => {
                            if (disabled) {
                              return
                            }
                            setActiveProvider(provider.key)
                          }}
                          className={cn(
                            'group relative rounded-2xl px-4 py-3 flex items-center gap-3 text-left transition-colors',
                            'bg-transparent text-slate-600 dark:text-slate-300',
                            disabled
                              ? 'opacity-60 cursor-not-allowed'
                              : 'cursor-pointer hover:bg-white dark:hover:bg-slate-900/80 hover:shadow-[inset_0_0_0_1px_rgba(226,232,240,0.9)] dark:hover:shadow-[inset_0_0_0_1px_rgba(51,65,85,0.8)] hover:z-10'
                          )}
                        >
                          <div className="w-10 h-10 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 flex items-center justify-center shrink-0">
                            <Icon />
                          </div>
                          <div className="flex-1 min-w-0 text-left">
                            <div className="flex items-center justify-between gap-2">
                              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                                {t(provider.nameKey)}
                              </h4>
                              {!provider.enabled && (
                                <span className={`${TYPOGRAPHY.micro} font-semibold text-slate-400 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-full`}>
                                  {t('sso.panel.comingSoon')}
                                </span>
                              )}
                            </div>
                            <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
                              {t(provider.descKey)}
                            </p>
                          </div>
                          {!disabled && (
                            <div className="ml-auto flex h-8 w-8 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 border border-indigo-100 opacity-0 transition-opacity duration-150 group-hover:opacity-100 dark:bg-indigo-500/10 dark:text-indigo-300 dark:border-indigo-500/30">
                              <ChevronRight size={16} />
                            </div>
                          )}
                        </button>
                      )
                    })}
                  </div>

                </div>
              </div>
            )}
          </>
        )}

        {/* 底部版权区域 - 固定高度防止中英文切换时布局抖动 */}
        {!showWorkspaceSelect ? (
          <div className="text-legal text-slate-400 dark:text-slate-500 text-center mt-3 select-none min-h-[48px]">
            <p className="font-mono tracking-wide">© {new Date().getFullYear()} {t('brand.name')} {t('brand.tagline')}</p>
            <p className="mt-1 text-slate-500 dark:text-slate-400">{t('brand.slogan')}</p>
          </div>
        ) : null}
      </div>
    </div>
  )
}

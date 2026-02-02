/**
 * 登录表单组件
 *
 * 功能：
 * 1. 用户名/密码登录
 * 2. 主题切换
 * 3. 表单验证
 * 4. 错误处理
 */

import { useEffect, useMemo, useRef, useState, FormEvent, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, UserRound, Lock, Loader2, Eye, EyeOff, Check, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { useThemeMode, useAuthStore } from '@/stores'
import { BrandLogo, ThirdPartyIcons } from '@/components'
import { Button } from '@/components/ui'
import { paddingClassMap } from '@/design-tokens/dimensions'
import { cn } from '@/lib/utils'
import { getSsoQr } from '@/lib/api/auth'
import type { SsoQrResponse } from '@/types/auth'

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
    iconSize: Math.round(14 * SCALE),
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

const DINGTALK_PROVIDER = 'dingtalk'
const DINGTALK_SCRIPT_SRC = 'https://g.alicdn.com/dingding/h5-dingtalk-login/0.21.0/ddlogin.js'
const DINGTALK_CONTAINER_ID = 'dingtalk-login-container'
const DINGTALK_QR_SIZE = 180

let dingtalkScriptPromise: Promise<void> | null = null

function loadDingTalkScript(): Promise<void> {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('无法加载钉钉脚本'))
  }
  if (window.DTFrameLogin) {
    return Promise.resolve()
  }
  if (dingtalkScriptPromise) {
    return dingtalkScriptPromise
  }
  dingtalkScriptPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[data-dingtalk-login="true"]`)
    if (existing) {
      existing.addEventListener('load', () => resolve())
      existing.addEventListener('error', () => reject(new Error('钉钉脚本加载失败')))
      return
    }
    const script = document.createElement('script')
    script.src = DINGTALK_SCRIPT_SRC
    script.async = true
    script.setAttribute('data-dingtalk-login', 'true')
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('钉钉脚本加载失败'))
    document.body.appendChild(script)
  })
  return dingtalkScriptPromise
}

function readPayloadString(payload: Record<string, unknown>, key: string, fallback: string | null = null): string | null {
  const value = payload[key]
  if (typeof value !== 'string') {
    return fallback
  }
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : fallback
}

function resolveAuthCode(result: { authCode?: string; redirectUrl?: string }): string | null {
  if (result.authCode && result.authCode.trim().length > 0) {
    return result.authCode.trim()
  }
  if (result.redirectUrl) {
    try {
      const url = new URL(result.redirectUrl)
      return url.searchParams.get('authCode') || url.searchParams.get('code')
    } catch {
      return null
    }
  }
  return null
}

/**
 * 登录表单组件
 */
interface LoginFormProps {
  scale: number
  ready: boolean
  tenantCode?: string | null
  inviteCode?: string | null
}

interface LoginFormContentProps {
  tenantCode?: string | null
  inviteCode?: string | null
  onSsoClick?: () => void
}

type LoginTab = 'account' | 'sso'

type SsoProviderKey = 'dingtalk' | 'wecom' | 'lark'

const PROVIDER_CONFIG: Array<{
  key: SsoProviderKey
  name: string
  desc: string
  icon: keyof typeof ThirdPartyIcons
  enabled: boolean
}> = [
  { key: 'dingtalk', name: '钉钉 登录', desc: '使用该平台组织账号授权', icon: 'DingTalk', enabled: true },
  { key: 'wecom', name: '企业微信 登录', desc: '使用该平台组织账号授权', icon: 'WeCom', enabled: false },
  { key: 'lark', name: '飞书 登录', desc: '使用该平台组织账号授权', icon: 'Lark', enabled: false }
]

export function LoginFormContent({ tenantCode, inviteCode, onSsoClick }: LoginFormContentProps) {
  const { t } = useTranslation('login')
  const mode = useThemeMode()
  const navigate = useNavigate()

  const login = useAuthStore((state) => state.login)
  const loading = useAuthStore((state) => state.loading)

  // 表单状态（默认填充测试账号）
  const [username, setUsername] = useState('sunny')
  const [password, setPassword] = useState('123456asd')
  const [rememberMe, setRememberMe] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const inputBaseClass =
    `select-text w-full bg-white dark:bg-[#0B1120] border border-slate-200 dark:border-slate-800 rounded-xl ${SIZES.input.py} ${SIZES.input.px} ${SIZES.input.text} text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-600 selection:bg-indigo-500/20 selection:text-slate-900 dark:selection:bg-indigo-400/35 dark:selection:text-white focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all`
  const labelTypography = 'tracking-[0.05em]'
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

    if (!tenantCode || !tenantCode.trim()) {
      toast.error('缺少租户编码')
      return
    }

    try {
      await login(username, password, rememberMe, { tenantCode, inviteCode: inviteCode ?? undefined })
      toast.success(t('form.success'), { style: successToastStyle })
      navigate('/home')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '登录失败，请重试')
    }
  }

  return (
    <div className="select-none">
      <form onSubmit={handleSubmit} className="flex flex-col gap-3 md:gap-4 select-none">
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
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className={inputBaseClass}
              placeholder="sunny"
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
            <a href="#" className="text-xs font-semibold text-indigo-500 dark:text-indigo-400 hover:text-indigo-300">
              {t('form.forgot')}
            </a>
          </div>
          <div className="relative group">
            <div className={`absolute inset-y-0 left-0 ${SIZES.input.iconPadding} flex items-center pointer-events-none text-slate-400 dark:text-slate-500 group-focus-within:text-indigo-500 transition-colors`}>
              <Lock size={SIZES.input.iconSize} />
            </div>
            <input
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={inputBaseClass}
              placeholder="••••••••"
              disabled={loading}
            />
            <Button
              type="button"
              onClick={() => setShowPassword((prev) => !prev)}
              variant="ghost"
              size="tiny"
              className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-colors"
              aria-label={showPassword ? '隐藏密码' : '显示密码'}
            >
              {showPassword ? <EyeOff size={SIZES.input.iconSize} /> : <Eye size={SIZES.input.iconSize} />}
            </Button>
          </div>
        </div>

        {/* 记住我复选框 */}
        <label
          className={`flex items-center gap-2.5 select-none group ${loading ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
        >
          <input
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
        <div className="mt-6">
          <div className="flex items-center gap-3">
            <div className="flex-1 border-t border-slate-100 dark:border-white/10" />
            <span className="text-[11px] text-slate-400 dark:text-slate-500 font-medium">其他登录方式</span>
            <div className="flex-1 border-t border-slate-100 dark:border-white/10" />
          </div>
          {onSsoClick ? (
            <div className="flex justify-center mt-3">
              <button
                type="button"
                onClick={onSsoClick}
                className="text-xs font-semibold text-indigo-500 hover:text-indigo-600 dark:text-indigo-300 dark:hover:text-indigo-200 transition-colors"
              >
                企业SSO
              </button>
            </div>
          ) : null}
        </div>
      </form>
    </div>
  )
}

export function LoginForm({ scale, ready, tenantCode, inviteCode }: LoginFormProps) {
  const { t } = useTranslation('login')
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState<LoginTab>('account')
  const [activeProvider, setActiveProvider] = useState<SsoProviderKey | null>(null)
  const [qrConfig, setQrConfig] = useState<SsoQrResponse | null>(null)
  const [qrLoading, setQrLoading] = useState(false)
  const [qrError, setQrError] = useState<string | null>(null)
  const [scriptReady, setScriptReady] = useState(false)
  const qrContainerRef = useRef<HTMLDivElement | null>(null)
  const loginWithSso = useAuthStore((state) => state.loginWithSso)
  const authLoading = useAuthStore((state) => state.loading)

  const panelStyle = useMemo(() => ({
    transform: `scale(${scale})`,
    transformOrigin: 'center center',
    opacity: ready ? 1 : 0,
    transition: 'opacity 0.3s ease'
  }), [scale, ready])

  useEffect(() => {
    if (activeTab === 'account') {
      setActiveProvider(null)
      setQrConfig(null)
      setQrError(null)
    }
  }, [activeTab])

  useEffect(() => {
    if (activeProvider !== 'dingtalk') {
      return
    }
    if (!tenantCode || !tenantCode.trim()) {
      setQrError('缺少租户编码')
      toast.error('缺少租户编码')
      return
    }
    let cancelled = false
    setQrLoading(true)
    setQrError(null)
    getSsoQr(tenantCode, DINGTALK_PROVIDER)
      .then((data) => {
        if (!cancelled) {
          setQrConfig(data)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : '获取扫码配置失败'
          setQrError(message)
          toast.error(message)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setQrLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [activeProvider, tenantCode])

  useEffect(() => {
    if (activeProvider !== 'dingtalk' || !qrConfig) {
      return
    }
    let cancelled = false
    loadDingTalkScript()
      .then(() => {
        if (!cancelled) {
          setScriptReady(true)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : '钉钉脚本加载失败'
          setQrError(message)
          toast.error(message)
        }
      })

    return () => {
      cancelled = true
    }
  }, [activeProvider, qrConfig])

  useEffect(() => {
    if (!scriptReady || !qrConfig || activeProvider !== 'dingtalk') {
      return
    }
    if (!window.DTFrameLogin) {
      return
    }
    const payload = (qrConfig.payload ?? {}) as Record<string, unknown>
    const clientId = readPayloadString(payload, 'clientId')
    const redirectUri = readPayloadString(payload, 'redirectUri')
    if (!clientId || !redirectUri) {
      setQrError('SSO 配置缺少 clientId 或 redirectUri')
      return
    }
    const scope = readPayloadString(payload, 'scope', 'openid corpid') || 'openid corpid'
    const responseType = readPayloadString(payload, 'responseType', 'code') || 'code'
    const prompt = readPayloadString(payload, 'prompt', 'consent')
    const corpId = readPayloadString(payload, 'corpId')
    const container = qrContainerRef.current
    if (container) {
      container.innerHTML = ''
    }
    window.DTFrameLogin(
      {
        id: DINGTALK_CONTAINER_ID,
        width: DINGTALK_QR_SIZE,
        height: DINGTALK_QR_SIZE
      },
      {
        redirect_uri: encodeURIComponent(redirectUri),
        client_id: clientId,
        scope,
        response_type: responseType,
        state: qrConfig.state,
        prompt: prompt ?? 'consent',
        ...(corpId ? { corpId } : {})
      },
      async (result) => {
        const authCode = resolveAuthCode(result || {})
        const state = result?.state || qrConfig.state
        if (!authCode) {
          toast.error('获取授权码失败')
          return
        }
        if (!tenantCode || !tenantCode.trim()) {
          toast.error('缺少租户编码')
          return
        }
        try {
          await loginWithSso({
            tenantCode,
            provider: DINGTALK_PROVIDER,
            authCode,
            state,
            inviteCode: inviteCode ?? undefined
          })
          toast.success('登录成功')
          navigate('/home')
        } catch (error) {
          toast.error(error instanceof Error ? error.message : '登录失败，请重试')
        }
      },
      (errorMsg) => {
        const message = errorMsg || '钉钉登录失败'
        toast.error(message)
      }
    )
  }, [scriptReady, qrConfig, activeProvider, tenantCode, inviteCode, loginWithSso, navigate])

  const showQrPanel = activeTab === 'sso' && activeProvider === 'dingtalk'
  const showListPanel = activeTab === 'sso' && activeProvider === null

  return (
    <div
      className="relative z-20 flex w-full h-full items-center justify-center translate-y-6 md:translate-y-8"
      style={{ '--login-form-base-height': `${LOGIN_FORM_BASE_HEIGHT}px` } as CSSProperties}
    >
      <div
        className={cn(
          'flex flex-col gap-3 md:gap-4 lg:gap-5 rounded-none w-full',
          paddingClassMap.md,
          LOGIN_FORM_WIDTH_CLASS
        )}
        style={panelStyle}
      >
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
            inviteCode={inviteCode}
            onSsoClick={() => setActiveTab('sso')}
          />
        ) : (
          <div className="flex flex-col gap-3">
            <div className="flex items-start justify-between">
              <div>
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">企业登录</h3>
                <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">通过第三方平台授权</p>
              </div>
              <button
                type="button"
                onClick={() => setActiveTab('account')}
                className="text-xs font-semibold text-indigo-500 hover:text-indigo-600 dark:text-indigo-300 dark:hover:text-indigo-200 transition-colors"
              >
                账号登录
              </button>
            </div>

            <div className="relative">
              <div
                aria-hidden={!showListPanel}
                className={cn(
                  'flex flex-col gap-2.5 transition-opacity',
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
                        'group relative bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 px-4 py-3 flex items-center gap-4 transition-all',
                        disabled
                          ? 'opacity-60 cursor-not-allowed'
                          : 'hover:border-indigo-300 dark:hover:border-indigo-400/50 hover:shadow-lg hover:shadow-indigo-500/10'
                      )}
                    >
                      <div className="w-12 h-12 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 flex items-center justify-center shrink-0">
                        <Icon />
                      </div>
                      <div className="flex-1 min-w-0 text-left">
                        <div className="flex items-center justify-between gap-2">
                          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                            {provider.name}
                          </h4>
                          {!provider.enabled && (
                            <span className="text-[10px] font-semibold text-slate-400 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-full">
                              敬请期待
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
                          {provider.desc}
                        </p>
                      </div>
                      {!disabled && (
                        <div className="text-slate-300 group-hover:text-indigo-500 transition-colors">
                          <ChevronRight size={18} />
                        </div>
                      )}
                    </button>
                  )
                })}
              </div>

              {showQrPanel ? (
                <div className="absolute inset-0 flex flex-col items-center gap-2">
                  <div className="w-full flex items-center justify-between">
                    <h3 className="text-xs font-semibold text-indigo-600 dark:text-indigo-300">钉钉扫码登录</h3>
                    <button
                      type="button"
                      onClick={() => {
                        setActiveProvider(null)
                        setQrConfig(null)
                        setQrError(null)
                      }}
                      className="text-[11px] text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
                    >
                      返回
                    </button>
                  </div>
                  <div
                    className="bg-slate-50 dark:bg-slate-800/70 rounded-xl border border-slate-100 dark:border-slate-700 flex items-center justify-center"
                    style={{ width: DINGTALK_QR_SIZE, height: DINGTALK_QR_SIZE }}
                  >
                    {qrLoading ? (
                      <Loader2 size={22} className="animate-spin text-slate-400" />
                    ) : (
                      <div id={DINGTALK_CONTAINER_ID} ref={qrContainerRef} className="w-full h-full" />
                    )}
                  </div>
                  {qrError ? (
                    <div className="text-[11px] text-rose-500">{qrError}</div>
                  ) : (
                    <div className="text-[11px] text-slate-400 bg-slate-50 dark:bg-slate-800/80 px-3 py-1.5 rounded-full">
                      请使用钉钉App扫码登录
                    </div>
                  )}
                </div>
              ) : null}
            </div>
          </div>
        )}

        {/* 底部版权区域 - 固定高度防止中英文切换时布局抖动 */}
        <div className="text-legal text-slate-400 dark:text-slate-500 text-center mt-3 select-none min-h-[48px]">
          <p className="font-mono tracking-wide">© {new Date().getFullYear()} {t('brand.name')} {t('brand.tagline')}</p>
          <p className="mt-1 text-slate-500 dark:text-slate-400">{t('brand.slogan')}</p>
        </div>
      </div>
    </div>
  )
}

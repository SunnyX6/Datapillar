/**
 * 登录表单组件
 *
 * 功能：
 * 1. 用户名/密码登录
 * 2. 主题切换
 * 3. 表单验证
 * 4. 错误处理
 */

import { useState, FormEvent, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, UserRound, Lock, Loader2, Eye, EyeOff, Check } from 'lucide-react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { useThemeMode, useAuthStore } from '@/stores'
import { BrandLogo } from '@/components'
import { paddingClassMap } from '@/design-tokens/dimensions'
import { cn } from '@/lib/utils'

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

/**
 * 登录表单组件
 */
interface LoginFormProps {
  scale: number
  ready: boolean
}

export function LoginForm({ scale, ready }: LoginFormProps) {
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

    try {
      await login(username, password, rememberMe)
      toast.success(t('form.success'), { style: successToastStyle })
      navigate('/home')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '登录失败，请重试')
    }
  }

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
        style={{
          transform: `scale(${scale})`,
          transformOrigin: 'center center',
          opacity: ready ? 1 : 0,
          transition: 'opacity 0.3s ease'
        }}
      >
      {/* 欢迎标题 */}
      {/* <div className="select-none mb-3 md:mb-4 mt-4 md:mt-6">
          <h2 className={`${SIZES.title.text} font-semibold text-slate-900 dark:text-white text-center`}>{t('title')}</h2>
        </div> */}

      {/* 品牌区域 - 固定高度防止中英文切换时布局抖动 */}
        <div className="flex flex-col gap-1 md:gap-1.5 select-none mb-2 md:mb-3 -ml-2 min-h-[60px]">
          <BrandLogo
            size={Math.round(38 * SCALE)}
            showText
            brandName={t('brand.name')}
            brandTagline={t('brand.tagline')}
            nameClassName={SCALE >= 1 ? 'text-xl font-bold leading-tight tracking-tight text-indigo-600 dark:text-indigo-200' : 'text-lg font-bold leading-tight tracking-tight text-indigo-600 dark:text-indigo-200'}
          />
        </div>

        {/* 登录表单 */}
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
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-colors"
                  aria-label={showPassword ? '隐藏密码' : '显示密码'}
                >
                  {showPassword ? <EyeOff size={SIZES.input.iconSize} /> : <Eye size={SIZES.input.iconSize} />}
                </button>
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
          <button
            type="submit"
            disabled={loading}
            className={`relative isolate overflow-hidden w-full bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 disabled:bg-indigo-400 text-white ${SIZES.button.text} font-semibold ${SIZES.button.py} rounded-lg transition-all duration-150 shadow-md shadow-indigo-500/20 hover:-translate-y-0.5 active:translate-y-0.5 active:scale-[0.97] focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-400 flex items-center justify-center gap-2 mt-1 disabled:cursor-not-allowed before:absolute before:inset-0 before:bg-black/20 before:opacity-0 before:transition-opacity before:duration-150 active:before:opacity-30`}
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
          </button>
          <div className="border-t border-slate-100 dark:border-white/10 mt-6" />
          </form>
        </div>

        {/* 底部版权区域 - 固定高度防止中英文切换时布局抖动 */}
        <div className="text-legal text-slate-400 dark:text-slate-500 text-center mt-3 select-none min-h-[48px]">
          <p className="font-mono tracking-wide">© {new Date().getFullYear()} {t('brand.name')} {t('brand.tagline')}</p>
          <p className="mt-1 text-slate-500 dark:text-slate-400">{t('brand.slogan')}</p>
        </div>
      </div>
    </div>
  )
}

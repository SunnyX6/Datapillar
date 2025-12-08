/**
 * 登录页面主组件
 *
 * 功能：
 * 1. 左右布局：左侧演示动画 + 右侧登录表单
 * 2. 响应式设计
 */

import { useEffect, useState, useRef } from 'react'
import { AppLayout, SplitGrid } from '@/layouts/responsive'
import { ThemeToggle } from '@/components'
import { DemoCanvas } from './DemoCanvas'
import { LoginForm } from './LoginForm'
import { ChevronDown, Check } from 'lucide-react'
import { useI18nStore, type Language } from '@/stores'

/**
 * 登录页面
 */
export function LoginPage() {
  // 禁用 body 滚动
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
          <>
            {/* 语言和主题切换按钮 - 固定在右上角 */}
            <div className="absolute top-4 right-0.5 z-30 flex items-center gap-1">
              <ThemeToggle />
              <LoginLanguageDropdown />
            </div>
            <LoginForm />
          </>
        }
      />
    </AppLayout>
  )
}

function LoginLanguageDropdown() {
  const language = useI18nStore((state) => state.language)
  const setLanguage = useI18nStore((state) => state.setLanguage)
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement | null>(null)

  const label = language === 'zh-CN' ? '简体中文' : 'English'

  const handleSelect = (next: Language) => {
    if (next !== language) {
      setLanguage(next)
    }
    setIsOpen(false)
  }

  useEffect(() => {
    const handler = (event: MouseEvent) => {
      const target = event.target as Node
      if (dropdownRef.current && !dropdownRef.current.contains(target)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="flex items-center gap-1.5 px-2.5 py-1.5 w-32 rounded-lg text-body-sm text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
      >
        <span>{label}</span>
        <ChevronDown size={12} className={`transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`} />
      </button>
      <div
        className={`absolute right-0 top-full mt-2 w-36 bg-white dark:bg-[#0F172A] border border-slate-200 dark:border-slate-800 rounded-lg shadow-lg overflow-hidden z-30 transition-all duration-150 origin-top-right ${
          isOpen ? 'opacity-100 scale-100 pointer-events-auto' : 'opacity-0 scale-95 pointer-events-none'
        }`}
      >
        {[
          { id: 'zh-CN', label: '简体中文' },
          { id: 'en-US', label: 'English' }
        ].map((option) => (
          <button
            key={option.id}
            type="button"
            onClick={() => handleSelect(option.id as Language)}
            className="w-full px-3 py-1.5 text-left text-body-sm text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 flex items-center justify-between gap-2"
            aria-pressed={language === option.id}
          >
            <span>{option.label}</span>
            {language === option.id && <Check size={14} className="text-indigo-500" />}
          </button>
        ))}
      </div>
    </div>
  )
}

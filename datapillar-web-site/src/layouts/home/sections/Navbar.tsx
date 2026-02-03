import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown, Monitor, BarChart3, BotMessageSquare, Globe } from 'lucide-react'
import { cn } from '@/lib/utils'
import brandLogo from '@/assets/icons/logo.png'
import { useI18nStore } from '@/stores/i18nStore'

interface NavbarProps {
  onRequestAccess: () => void
}

export function Navbar({ onRequestAccess }: NavbarProps) {
  const [isScrolled, setIsScrolled] = useState(false)
  const [isProductsOpen, setIsProductsOpen] = useState(false)
  const [isLanguageOpen, setIsLanguageOpen] = useState(false)
  const language = useI18nStore((state) => state.language)
  const setLanguage = useI18nStore((state) => state.setLanguage)
  const { t } = useTranslation('navigation')
  const productsRef = useRef<HTMLDivElement>(null)
  const languageRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20)
    }
    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  useEffect(() => {
    if (!isProductsOpen) return
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node
      if (!productsRef.current?.contains(target)) {
        setIsProductsOpen(false)
      }
    }
    const handleEsc = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsProductsOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEsc)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEsc)
    }
  }, [isProductsOpen])

  useEffect(() => {
    if (!isLanguageOpen) return
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node
      if (!languageRef.current?.contains(target)) {
        setIsLanguageOpen(false)
      }
    }
    const handleEsc = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsLanguageOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEsc)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEsc)
    }
  }, [isLanguageOpen])

  return (
    <nav
      className={cn(
        'fixed left-0 right-0 top-0 z-50 border-b pt-5 pb-4 transition-colors duration-300',
        isScrolled
          ? 'bg-[#020410]/80 backdrop-blur-md border-violet-500/10'
          : 'bg-transparent border-transparent'
      )}
    >
      <div className="mx-auto flex max-w-7xl items-center justify-between px-8">
        <div className="flex items-center space-x-2.5 group cursor-pointer">
          <div className="flex h-10 w-10 items-center justify-center">
            <img src={brandLogo} alt="Datapillar" className="h-10 w-10 object-contain" />
          </div>
          <div className="flex flex-col">
            <span className="text-lg font-bold text-white tracking-tight leading-none">Datapillar</span>
            <span className="text-micro text-cyan-400 tracking-widest font-mono">{t('brand.tagline')}</span>
          </div>
        </div>

        <div className="flex items-center space-x-8">
          <div ref={productsRef} className="relative">
            <button
              type="button"
              onClick={() => {
                setIsLanguageOpen(false)
                setIsProductsOpen((prev) => !prev)
              }}
              className="flex items-center gap-1 text-slate-300 hover:text-white transition-colors text-sm font-medium"
              aria-haspopup="true"
              aria-expanded={isProductsOpen}
            >
              {t('menu.products')}
              <ChevronDown className={`w-4 h-4 transition-transform ${isProductsOpen ? 'rotate-180' : ''}`} />
            </button>
            {isProductsOpen && (
              <div className="absolute left-0 mt-5 w-72 rounded-xl border border-slate-800 bg-[#0b0f19] shadow-2xl overflow-hidden">
                <a
                  href="#studio"
                  onClick={() => setIsProductsOpen(false)}
                  className="flex items-start gap-3 px-4 py-3 hover:bg-white/5 transition-colors"
                >
                  <div className="w-9 h-9 rounded-lg bg-violet-500/15 border border-violet-500/30 flex items-center justify-center shrink-0">
                    <Monitor className="w-4 h-4 text-violet-300" />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-semibold text-white">{t('products.studio.name')}</span>
                      <span className="text-micro px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                        {t('products.studio.status')}
                      </span>
                    </div>
                    <span className="text-xs text-slate-500">{t('products.studio.desc')}</span>
                  </div>
                </a>
                <div className="h-px bg-white/5" />
                <div className="flex items-start gap-3 px-4 py-3 opacity-50 cursor-not-allowed">
                  <div className="w-9 h-9 rounded-lg bg-cyan-500/10 border border-cyan-500/20 flex items-center justify-center shrink-0">
                    <BarChart3 className="w-4 h-4 text-cyan-300" />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-semibold text-slate-300">{t('products.analytics.name')}</span>
                      <span className="text-micro px-2 py-0.5 rounded-full bg-slate-700/30 text-slate-400 border border-slate-700/40">
                        {t('products.analytics.status')}
                      </span>
                    </div>
                    <span className="text-xs text-slate-600">{t('products.analytics.desc')}</span>
                  </div>
                </div>
                <div className="h-px bg-white/5" />
                <div className="flex items-start gap-3 px-4 py-3 opacity-50 cursor-not-allowed">
                  <div className="w-9 h-9 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center shrink-0">
                    <BotMessageSquare className="w-4 h-4 text-emerald-300" />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-semibold text-slate-300">{t('products.insights.name')}</span>
                      <span className="text-micro px-2 py-0.5 rounded-full bg-slate-700/30 text-slate-400 border border-slate-700/40">
                        {t('products.insights.status')}
                      </span>
                    </div>
                    <span className="text-xs text-slate-600">{t('products.insights.desc')}</span>
                  </div>
                </div>
              </div>
            )}
          </div>
          <a href="#solutions" className="text-slate-300 hover:text-white transition-colors text-sm font-medium">
            {t('menu.solutions')}
          </a>
          <a href="#pricing" className="text-slate-300 hover:text-white transition-colors text-sm font-medium">
            {t('menu.pricing')}
          </a>
          <div ref={languageRef} className="relative">
            <button
              type="button"
              onClick={() => {
                setIsProductsOpen(false)
                setIsLanguageOpen((prev) => !prev)
              }}
              className="flex items-center gap-1.5 text-slate-300 hover:text-white transition-colors text-sm font-medium"
              aria-haspopup="true"
              aria-expanded={isLanguageOpen}
            >
              <Globe className="w-4 h-4 text-slate-400" />
              <span>{language === 'zh-CN' ? t('language.zh') : t('language.en')}</span>
              <ChevronDown className={`w-4 h-4 transition-transform ${isLanguageOpen ? 'rotate-180' : ''}`} />
            </button>
            {isLanguageOpen && (
              <div className="absolute right-0 mt-3 w-32 rounded-xl border border-slate-800 bg-[#0b0f19] shadow-2xl overflow-hidden">
                <button
                  type="button"
                  onClick={() => {
                    setLanguage('zh-CN')
                    setIsLanguageOpen(false)
                  }}
                  className={`w-full px-4 py-2 text-sm text-left transition-colors ${
                    language === 'zh-CN' ? 'bg-white/5 text-white' : 'text-slate-300 hover:bg-white/5'
                  }`}
                >
                  <span className="inline-flex items-center gap-2">
                    <span className="text-base leading-none">🇨🇳</span>
                    {t('language.zh')}
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setLanguage('en-US')
                    setIsLanguageOpen(false)
                  }}
                  className={`w-full px-4 py-2 text-sm text-left transition-colors ${
                    language === 'en-US' ? 'bg-white/5 text-white' : 'text-slate-300 hover:bg-white/5'
                  }`}
                >
                  <span className="inline-flex items-center gap-2">
                    <span className="text-base leading-none">🇺🇸</span>
                    {t('language.en')}
                  </span>
                </button>
              </div>
            )}
          </div>
          <button
            onClick={onRequestAccess}
            className="rounded-full bg-[#5558ff] px-5 py-2 text-xs font-bold text-white shadow-[0_0_15px_rgba(85,88,255,0.4)] transition-all hover:bg-[#4548e6]"
          >
            {t('menu.trial')}
          </button>
        </div>
      </div>
    </nav>
  )
}

import { useEffect, useRef, useState } from 'react'
import {
  Bell,
  Check,
  CreditCard,
  FolderKanban,
  Globe,
  LayoutGrid,
  LogOut,
  Moon,
  Share2,
  Search,
  ShieldCheck,
  Sun,
  Users,
  ChevronDown,
  Layers,
  Book,
  User as UserIcon,
  X
} from 'lucide-react'
import { ExpandToggle } from './ExpandToggle'
import { useLocation, useNavigate } from 'react-router-dom'
import { useI18nStore, useSearchStore, type Language, type SearchContext } from '@/stores'
import { useTranslation } from 'react-i18next'
import { iconSizeToken } from '@/design-tokens/dimensions'

type View = 'dashboard' | 'workflow' | 'wiki' | 'profile' | 'ide'

const LANGUAGE_OPTIONS: { id: Language; label: string }[] = [
  { id: 'zh-CN', label: '简体中文' },
  { id: 'en-US', label: 'English' }
]

interface UserInfo {
  name: string
  role: string
  email?: string
}

interface TopNavProps {
  isDark: boolean
  toggleTheme: () => void
  user: UserInfo
  onNavigate: (view: View) => void
  currentView: View
  onLogout: () => void
  isSidebarCollapsed: boolean
  onToggleSidebar: () => void
  onProfile: () => void
}

export function TopNav({
  isDark,
  toggleTheme,
  user,
  onNavigate,
  currentView,
  onLogout,
  isSidebarCollapsed,
  onToggleSidebar,
  onProfile
}: TopNavProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const language = useI18nStore((state) => state.language)
  const setLanguage = useI18nStore((state) => state.setLanguage)
  const { t } = useTranslation('navigation')
  const [isDropdownOpen, setIsDropdownOpen] = useState(false)
  const [isGovernanceOpen, setIsGovernanceOpen] = useState(false)
  const [isLanguageOpen, setIsLanguageOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement | null>(null)
  const governanceRef = useRef<HTMLDivElement | null>(null)
  const languageRef = useRef<HTMLDivElement | null>(null)
  const searchInputRef = useRef<HTMLInputElement | null>(null)

  // 全局搜索状态
  const searchTerm = useSearchStore((state) => state.searchTerm)
  const setSearchTerm = useSearchStore((state) => state.setSearchTerm)
  const _searchContext = useSearchStore((state) => state.context)
  const setSearchContext = useSearchStore((state) => state.setContext)
  const isSearchOpen = useSearchStore((state) => state.isOpen)
  const setIsSearchOpen = useSearchStore((state) => state.setIsOpen)
  const getContextConfig = useSearchStore((state) => state.getContextConfig)

  // 根据路由自动设置搜索上下文
  useEffect(() => {
    const path = location.pathname
    let context: SearchContext = 'default'

    if (path.startsWith('/governance/metadata')) {
      context = 'metadata'
    } else if (path.includes('/governance/semantic/metrics')) {
      context = 'semantic-metrics'
    } else if (path.includes('/governance/semantic/glossary')) {
      context = 'semantic-glossary'
    } else if (path.startsWith('/governance/semantic')) {
      context = 'semantic'
    } else if (path.startsWith('/governance/knowledge')) {
      context = 'knowledge'
    } else if (path === '/' || path.startsWith('/dashboard')) {
      context = 'dashboard'
    }

    setSearchContext(context)
  }, [location.pathname, setSearchContext])

  // 键盘快捷键 ⌘K
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault()
        setIsSearchOpen(true)
        requestAnimationFrame(() => searchInputRef.current?.focus())
      }
      if (e.key === 'Escape' && isSearchOpen) {
        setIsSearchOpen(false)
        setSearchTerm('')
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isSearchOpen, setIsSearchOpen, setSearchTerm])

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node
      if (dropdownRef.current && !dropdownRef.current.contains(target)) setIsDropdownOpen(false)
      if (governanceRef.current && !governanceRef.current.contains(target)) setIsGovernanceOpen(false)
      if (languageRef.current && !languageRef.current.contains(target)) setIsLanguageOpen(false)
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleProfileClick = () => {
    onProfile()
    setIsDropdownOpen(false)
  }

  const handleLanguageSelect = (next: Language) => {
    if (language !== next) {
      setLanguage(next)
    }
    setIsLanguageOpen(false)
  }

  const isGovernanceActive = location.pathname.startsWith('/governance')
  const isDashboardActive = currentView === 'dashboard' && !isGovernanceActive
  const _languageLabel = language === 'zh-CN' ? t('language.zh', { defaultValue: '简体中文' }) : t('language.en', { defaultValue: 'English' })
  const orgLabel = t('top.org', { defaultValue: 'Acme Corp' })

  const handleNavigateGovernance = (path: string) => {
    navigate(path)
    setIsGovernanceOpen(false)
  }
  const handleGovernanceEnter = () => setIsGovernanceOpen(true)
  const handleGovernanceLeave = (event: React.MouseEvent) => {
    const nextTarget = event.relatedTarget as Node | null
    if (governanceRef.current && nextTarget && governanceRef.current.contains(nextTarget)) {
      return
    }
    setIsGovernanceOpen(false)
  }

  return (
    <div className="@container relative h-14 w-full bg-white/80 dark:bg-[#0B1120]/80 backdrop-blur-xl border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-4 sticky top-0 z-40 transition-colors duration-300">
      {isSidebarCollapsed && (
        <ExpandToggle variant="topnav" onToggle={onToggleSidebar} className="absolute left-0 bottom-0" />
      )}
      <div className="flex items-center gap-4 min-w-0 @md:min-w-40">
        <button
          type="button"
          className="flex items-center gap-2 group"
          onClick={() => onNavigate('dashboard')}
        >
        </button>

        <button
          type="button"
          className="hidden @md:flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800/50 transition-colors text-body-sm text-slate-600 dark:text-slate-400"
        >
          <div className="w-5 h-5 rounded bg-slate-200 dark:bg-slate-700 flex items-center justify-center text-micro">
            A
          </div>
          <span>{orgLabel}</span>
          <ChevronDown size={iconSizeToken.tiny} className="opacity-50" />
        </button>
      </div>

      <div className="flex items-center flex-1 min-w-0 mx-2">
      <div className="flex items-center p-1 bg-slate-100/50 dark:bg-slate-900/50 rounded-lg border border-slate-200/50 dark:border-slate-800/50 gap-0.5 mx-auto">
        <TabItem icon={<LayoutGrid size={iconSizeToken.small} />} label={t('top.tabs.dashboard')} active={isDashboardActive} onClick={() => onNavigate('dashboard')} />
        <div
          className="relative"
          ref={governanceRef}
          onMouseEnter={handleGovernanceEnter}
          onMouseLeave={handleGovernanceLeave}
        >
          <button
            type="button"
            onClick={() => setIsGovernanceOpen((prev) => !prev)}
            className={`
              flex items-center gap-2 px-2 @md:px-3 py-1.5 rounded-md text-body transition-all duration-200 relative
              ${isGovernanceActive ? 'text-indigo-600 dark:text-indigo-300 bg-white dark:bg-slate-800 shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-white/50 dark:hover:bg-slate-800/50'}
            `}
          >
            <ShieldCheck size={iconSizeToken.small} />
            <span className="hidden @md:inline">{t('top.tabs.governance')}</span>
            <ChevronDown
              size={iconSizeToken.tiny}
              className={`transition-transform duration-200 ${isGovernanceOpen ? 'rotate-180' : ''} ${isGovernanceActive ? 'text-indigo-500' : 'text-slate-400 dark:text-slate-500'}`}
            />
            {isGovernanceActive && <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-indigo-500" />}
          </button>
          <div
            className="absolute left-0 top-full h-3 w-full"
            onMouseEnter={handleGovernanceEnter}
            onMouseLeave={handleGovernanceLeave}
            aria-hidden
          />
          <div
            className={`absolute left-0 top-[calc(100%+12px)] w-72 bg-white dark:bg-[#0F172A] border border-slate-200 dark:border-slate-800 rounded-xl shadow-xl overflow-hidden z-[60] transition-all duration-150 origin-top-left ${
              isGovernanceOpen ? 'opacity-100 scale-100 pointer-events-auto' : 'opacity-0 scale-95 pointer-events-none'
            }`}
            onMouseEnter={handleGovernanceEnter}
            onMouseLeave={handleGovernanceLeave}
          >
            <div className="px-4 py-2.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-micro font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest">
                {t('top.dropdown.governanceHeader', { defaultValue: 'Governance & One Meta' })}
              </span>
            </div>
            <div className="p-2">
              <button
                type="button"
                className="w-full px-3 py-2.5 text-left hover:bg-slate-50 dark:hover:bg-slate-800/50 rounded-lg flex items-start gap-3 transition-colors group"
                onClick={() => handleNavigateGovernance('/governance/metadata')}
              >
                <div className="p-1.5 bg-purple-50 dark:bg-purple-900/30 rounded-lg text-purple-500 shrink-0 group-hover:bg-purple-100 dark:group-hover:bg-purple-900/50 transition-colors">
                  <Layers size={iconSizeToken.small} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-body-sm font-medium text-slate-800 dark:text-slate-200">{t('top.dropdown.metadata')}</div>
                  <div className="text-legal text-slate-400 dark:text-slate-500 mt-0.5">{t('top.dropdown.metadataDesc', { defaultValue: '统一物理资产元数据' })}</div>
                </div>
              </button>
              <button
                type="button"
                className="w-full px-3 py-2.5 text-left hover:bg-slate-50 dark:hover:bg-slate-800/50 rounded-lg flex items-start gap-3 transition-colors group"
                onClick={() => handleNavigateGovernance('/governance/semantic')}
              >
                <div className="p-1.5 bg-blue-50 dark:bg-blue-900/30 rounded-lg text-blue-500 shrink-0 group-hover:bg-blue-100 dark:group-hover:bg-blue-900/50 transition-colors">
                  <Book size={iconSizeToken.small} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-body-sm font-medium text-slate-800 dark:text-slate-200">{t('top.dropdown.semantic', { defaultValue: '元语义' })}</div>
                  <div className="text-legal text-slate-400 dark:text-slate-500 mt-0.5">{t('top.dropdown.semanticDesc', { defaultValue: '指标、词根、API等语义资产空间' })}</div>
                </div>
              </button>
              <button
                type="button"
                className="w-full px-3 py-2.5 text-left hover:bg-slate-50 dark:hover:bg-slate-800/50 rounded-lg flex items-start gap-3 transition-colors group"
                onClick={() => handleNavigateGovernance('/governance/knowledge')}
              >
                <div className="p-1.5 bg-indigo-50 dark:bg-indigo-900/30 rounded-lg text-indigo-500 shrink-0 group-hover:bg-indigo-100 dark:group-hover:bg-indigo-900/50 transition-colors">
                  <Share2 size={iconSizeToken.small} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-body-sm font-medium text-slate-800 dark:text-slate-200">{t('top.dropdown.knowledge')}</div>
                  <div className="text-legal text-slate-400 dark:text-slate-500 mt-0.5">{t('top.dropdown.knowledgeDesc', { defaultValue: '知识图谱可视化与关系探索' })}</div>
                </div>
              </button>
            </div>
          </div>
        </div>
        <TabItem icon={<FolderKanban size={iconSizeToken.small} />} label={t('top.tabs.projects')} />
        <TabItem icon={<Users size={iconSizeToken.small} />} label={t('top.tabs.team')} />
      </div>
      </div>

      <div className="flex items-center gap-2.5 min-w-0 @md:min-w-40 justify-end">
        <div className="hidden @md:flex items-center relative">
          <div
            className={`flex items-center gap-2 w-56 bg-slate-50 dark:bg-slate-900 border rounded-lg transition-all duration-200 shadow-sm ${
              isSearchOpen || searchTerm
                ? 'border-indigo-500/50 bg-white dark:bg-slate-800 ring-2 ring-indigo-500/20'
                : 'border-slate-200 dark:border-slate-800 hover:border-indigo-500/30 hover:bg-white dark:hover:bg-slate-800'
            }`}
          >
            <Search size={iconSizeToken.small} className="ml-3 shrink-0 text-slate-400" />
            <input
              ref={searchInputRef}
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onFocus={() => setIsSearchOpen(true)}
              onBlur={() => {
                if (!searchTerm) setIsSearchOpen(false)
              }}
              placeholder={getContextConfig().placeholder}
              className="flex-1 min-w-0 py-1.5 bg-transparent text-body-sm text-slate-700 dark:text-slate-200 placeholder:text-caption placeholder:text-slate-400 placeholder:truncate focus:outline-none truncate"
            />
            {searchTerm ? (
              <button
                type="button"
                onClick={() => {
                  setSearchTerm('')
                  searchInputRef.current?.focus()
                }}
                className="p-1.5 mr-1 shrink-0 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded transition-colors"
              >
                <X size={iconSizeToken.small} />
              </button>
            ) : (
              <span className="flex items-center gap-0.5 shrink-0 text-micro font-mono bg-white dark:bg-slate-800 px-1.5 py-0.5 mr-2 rounded border border-slate-200 dark:border-slate-700 text-slate-400">
                <span className="text-caption">⌘</span> K
              </span>
            )}
          </div>
        </div>

        <button
          type="button"
          onClick={toggleTheme}
          className="p-2 rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
        >
          {isDark ? <Sun size={iconSizeToken.large} /> : <Moon size={iconSizeToken.large} />}
        </button>

        <button
          type="button"
          className="p-2 rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors relative"
        >
          <Bell size={iconSizeToken.large} />
          <span className="absolute top-2 right-2.5 w-1.5 h-1.5 bg-rose-500 rounded-full ring-2 ring-white dark:ring-[#0B1120] animate-pulse" />
        </button>

        <div className="relative" ref={languageRef}>
          <button
            type="button"
            onClick={() => setIsLanguageOpen((prev) => !prev)}
            className="flex items-center gap-1.5 p-2 rounded-lg text-body-sm text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800 transition-colors"
          >
            <Globe size={iconSizeToken.large} className="shrink-0" />
            <ChevronDown size={iconSizeToken.tiny} className={`transition-transform duration-200 ${isLanguageOpen ? 'rotate-180' : ''} text-slate-400 shrink-0`} />
          </button>

          <div
            className={`absolute right-0 top-full mt-3 w-36 bg-white dark:bg-[#0F172A] border border-slate-200 dark:border-slate-800 rounded-lg shadow-lg overflow-hidden z-[60] transition-all duration-150 origin-top-right ${
              isLanguageOpen ? 'opacity-100 scale-100 pointer-events-auto' : 'opacity-0 scale-95 pointer-events-none'
            }`}
          >
            {LANGUAGE_OPTIONS.map((option) => (
              <button
                key={option.id}
                type="button"
                onClick={() => handleLanguageSelect(option.id)}
                className="w-full px-3 py-1.5 text-left text-body-sm text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 flex items-center justify-between gap-2"
                aria-pressed={language === option.id}
              >
                <span>{option.label}</span>
                {language === option.id && <Check size={iconSizeToken.small} className="text-indigo-500" />}
              </button>
            ))}
          </div>
        </div>

        <div className="ml-1 pl-2.5 border-l border-slate-200 dark:border-slate-800 relative" ref={dropdownRef}>
          <button type="button" className="flex items-center gap-2" onClick={() => setIsDropdownOpen((prev) => !prev)}>
            <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-600 p-px hover:shadow-md transition-shadow">
              <div className="w-full h-full rounded-full bg-white dark:bg-slate-900 flex items-center justify-center text-caption uppercase text-slate-700 dark:text-slate-200">
                {user.name.slice(0, 2)}
              </div>
            </div>
          </button>

          {isDropdownOpen && (
            <div className="absolute right-0 top-full mt-3 w-48 bg-white dark:bg-[#0F172A] border border-slate-200 dark:border-slate-700 rounded-lg shadow-lg overflow-hidden z-[60] animate-in fade-in zoom-in-95 duration-200 origin-top-right">
              <div className="px-3 py-2.5 border-b border-slate-100 dark:border-slate-700/50 bg-slate-50 dark:bg-[#0B1120]/50">
                <p className="text-body-sm font-semibold text-slate-800 dark:text-slate-200">{user.name}</p>
                <p className="text-caption text-slate-500 truncate">{user.email || 'engineer@datapillar.ai'}</p>
              </div>

              <div className="p-1">
                <DropdownButton icon={<UserIcon size={iconSizeToken.small} />} label={t('top.profile.profile')} onClick={handleProfileClick} />
                <DropdownButton icon={<CreditCard size={iconSizeToken.small} />} label={t('top.profile.billing')} />
              </div>

              <div className="p-1 border-t border-slate-100 dark:border-slate-700/50">
                <button
                  type="button"
                  onClick={onLogout}
                  className="w-full text-left px-2.5 py-1.5 text-body-sm text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/20 rounded-lg flex items-center gap-2 transition-colors"
                >
                  <LogOut size={iconSizeToken.small} />
                  {t('top.profile.logout')}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

interface TabItemProps {
  icon: React.ReactNode
  label: string
  active?: boolean
  onClick?: () => void
}

function TabItem({ icon, label, active, onClick }: TabItemProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`
        flex items-center gap-2 px-2 @md:px-3 py-1.5 rounded-md text-body transition-all duration-200 relative
        ${active ? 'text-indigo-600 dark:text-indigo-400 bg-white dark:bg-slate-800 shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200 hover:bg-white/50 dark:hover:bg-slate-800/50'}
      `}
    >
      {icon}
      <span className="hidden @md:inline">{label}</span>
      {active && <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-indigo-500" />}
    </button>
  )
}

interface DropdownButtonProps {
  icon: React.ReactNode
  label: string
  onClick?: () => void
}

function DropdownButton({ icon, label, onClick }: DropdownButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full text-left px-2.5 py-1.5 text-body-sm text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg flex items-center gap-2 transition-colors"
    >
      {icon}
      {label}
    </button>
  )
}

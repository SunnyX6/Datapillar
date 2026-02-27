import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
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
  Shield,
  Sun,
  Users,
  ChevronDown,
  Layers,
  Book,
  User as UserIcon,
  X
} from 'lucide-react'
import { ExpandToggle } from './ExpandToggle'
import { useAuthStore, useI18nStore, useSearchStore, type Language } from '@/state'
import { useTranslation } from 'react-i18next'
import { iconSizeToken, inputContainerWidthClassMap, menuWidthClassMap } from '@/design-tokens/dimensions'
import { Button } from '@/components/ui'
import type { Menu } from '@/services/types/auth'
import { isMenuVisible } from '@/services/menuPermissionService'

const LANGUAGE_OPTIONS: { id: Language; label: string }[] = [
  { id: 'zh-CN', label: '简体中文' },
  { id: 'en-US', label: 'English' }
]

interface UserInfo {
  name: string
  email?: string
}

interface TopNavProps {
  isDark: boolean
  toggleTheme: () => void
  user: UserInfo
  menus: Menu[]
  currentPath: string
  onNavigate: (path: string) => void
  onLogout: () => void
  isSidebarCollapsed: boolean
  onToggleSidebar: () => void
  onProfile: () => void
}

export function TopNav({
  isDark,
  toggleTheme,
  user,
  menus,
  currentPath,
  onNavigate,
  onLogout,
  isSidebarCollapsed,
  onToggleSidebar,
  onProfile
}: TopNavProps) {
  const authUser = useAuthStore((state) => state.user)
  const normalizedUserName = useMemo(() => {
    const name = user.name.trim()
    return name.length > 0 ? name : 'User'
  }, [user.name])
  const normalizedUserEmail = useMemo(() => {
    const email = user.email?.trim()
    return email && email.length > 0 ? email : '-'
  }, [user.email])
  const language = useI18nStore((state) => state.language)
  const setLanguage = useI18nStore((state) => state.setLanguage)
  const { t, i18n } = useTranslation('navigation')
  const [isDropdownOpen, setIsDropdownOpen] = useState(false)
  const [isGovernanceOpen, setIsGovernanceOpen] = useState(false)
  const [isLanguageOpen, setIsLanguageOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement | null>(null)
  const dropdownPanelRef = useRef<HTMLDivElement | null>(null)
  const governanceRef = useRef<HTMLDivElement | null>(null)
  const languageRef = useRef<HTMLDivElement | null>(null)
  const governancePanelRef = useRef<HTMLDivElement | null>(null)
  const languagePanelRef = useRef<HTMLDivElement | null>(null)
  const searchInputRef = useRef<HTMLInputElement | null>(null)
  const governanceLeaveTimerRef = useRef<number | null>(null)

  // 全局搜索状态
  const searchTerm = useSearchStore((state) => state.searchTerm)
  const setSearchTerm = useSearchStore((state) => state.setSearchTerm)
  const isSearchOpen = useSearchStore((state) => state.isOpen)
  const setIsSearchOpen = useSearchStore((state) => state.setIsOpen)
  const getContextConfig = useSearchStore((state) => state.getContextConfig)

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
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(target) &&
        (!dropdownPanelRef.current || !dropdownPanelRef.current.contains(target))
      ) {
        setIsDropdownOpen(false)
      }
      if (
        governanceRef.current &&
        !governanceRef.current.contains(target) &&
        (!governancePanelRef.current || !governancePanelRef.current.contains(target))
      ) {
        setIsGovernanceOpen(false)
      }
      if (
        languageRef.current &&
        !languageRef.current.contains(target) &&
        (!languagePanelRef.current || !languagePanelRef.current.contains(target))
      ) {
        setIsLanguageOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  useEffect(() => {
    return () => {
      if (governanceLeaveTimerRef.current) {
        window.clearTimeout(governanceLeaveTimerRef.current)
        governanceLeaveTimerRef.current = null
      }
    }
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
    setIsGovernanceOpen(false)
    setIsDropdownOpen(false)
  }

  const topMenus = useMemo(
    () => menus.filter((menu) => menu.location === 'TOP' && isMenuVisible(menu)),
    [menus],
  )
  const governanceMenu = useMemo(() => topMenus.find((menu) => menu.path === '/governance'), [topMenus])
  const profileItems = useMemo(() => {
    const itemMap = new Map<string, Menu>()

    const collectProfileItems = (items: Menu[]) => {
      items.forEach((item) => {
        if (!item || !isMenuVisible(item)) {
          return
        }
        if (item.location === 'PROFILE' && item.path && !itemMap.has(item.path)) {
          itemMap.set(item.path, item)
        }
        if (Array.isArray(item.children) && item.children.length > 0) {
          collectProfileItems(item.children)
        }
      })
    }

    collectProfileItems(menus)
    return Array.from(itemMap.values())
  }, [menus])

  const isMenuActive = (path: string) => {
    if (path === '/home') {
      return currentPath === '/' || currentPath === '/home' || currentPath.startsWith('/home/')
    }
    return currentPath === path || currentPath.startsWith(`${path}/`)
  }

  const isGovernanceActive = governanceMenu ? isMenuActive(governanceMenu.path) : false

  const topMenuIconMap: Record<string, React.ReactNode> = {
    '/home': <LayoutGrid size={iconSizeToken.small} />,
    '/governance': <ShieldCheck size={iconSizeToken.small} />,
    '/projects': <FolderKanban size={iconSizeToken.small} />,
    '/collaboration': <Users size={iconSizeToken.small} />
  }

  const getTopMenuIcon = (path: string) => {
    return topMenuIconMap[path] ?? <Layers size={iconSizeToken.small} />
  }

  const governanceItemMetaMap: Record<
    string,
    { icon: typeof Layers; className: string; hoverClassName: string }
  > = {
    '/governance/metadata': {
      icon: Layers,
      className: 'bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-300',
      hoverClassName: 'group-hover:bg-blue-100/70 dark:group-hover:bg-blue-500/20'
    },
    '/governance/semantic': {
      icon: Book,
      className: 'bg-purple-50 text-purple-600 dark:bg-purple-500/10 dark:text-purple-300',
      hoverClassName: 'group-hover:bg-purple-100/70 dark:group-hover:bg-purple-500/20'
    },
    '/governance/knowledge': {
      icon: Share2,
      className: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300',
      hoverClassName: 'group-hover:bg-emerald-100/70 dark:group-hover:bg-emerald-500/20'
    }
  }

  const getGovernanceItemMeta = (path: string) => {
    return (
      governanceItemMetaMap[path] ?? {
        icon: Layers,
        className: 'bg-slate-50 text-slate-500 dark:bg-slate-900/30 dark:text-slate-400',
        hoverClassName: 'group-hover:bg-slate-100 dark:group-hover:bg-slate-900/50'
      }
    )
  }

  const getTopMenuLabel = (menu: Menu) => {
    switch (menu.path) {
      case '/home':
        return t('top.tabs.dashboard', { defaultValue: menu.name })
      case '/governance':
        return t('top.tabs.governance', { defaultValue: menu.name })
      case '/projects':
        return t('top.tabs.projects', { defaultValue: menu.name })
      case '/collaboration':
        return t('top.tabs.team', { defaultValue: menu.name })
      default:
        return menu.name ?? menu.path
    }
  }

  const getGovernanceItemLabel = (menu: Menu) => {
    switch (menu.path) {
      case '/governance/metadata':
        return t('top.dropdown.metadata', { defaultValue: menu.name })
      case '/governance/semantic':
        return t('top.dropdown.semantic', { defaultValue: menu.name })
      case '/governance/knowledge':
        return t('top.dropdown.knowledge', { defaultValue: menu.name })
      default:
        return menu.name ?? menu.path
    }
  }

  const getGovernanceItemDescription = (menu: Menu) => {
    switch (menu.path) {
      case '/governance/metadata':
        return t('top.dropdown.metadataDesc', { defaultValue: menu.path })
      case '/governance/semantic':
        return t('top.dropdown.semanticDesc', { defaultValue: menu.path })
      case '/governance/knowledge':
        return t('top.dropdown.knowledgeDesc', { defaultValue: menu.path })
      default:
        return menu.path
    }
  }

  const getProfileLabel = (menu: Menu) => {
    switch (menu.path) {
      case '/profile':
        return t('top.profile.profile', { defaultValue: menu.name })
      case '/profile/permission':
        return t('top.profile.permission', { defaultValue: menu.name })
      case '/profile/llm/models':
        return t('top.profile.models', { defaultValue: menu.name })
      case '/profile/billing':
        return t('top.profile.billing', { defaultValue: menu.name })
      default:
        return menu.name ?? menu.path
    }
  }

  const profileIconMap: Record<string, React.ReactNode> = {
    '/profile': <UserIcon size={iconSizeToken.small} className="text-indigo-500" />,
    '/profile/permission': <Shield size={iconSizeToken.small} className="text-amber-500" />,
    '/profile/llm/models': <LayoutGrid size={iconSizeToken.small} className="text-blue-500" />,
    '/profile/billing': <CreditCard size={iconSizeToken.small} className="text-emerald-500" />
  }

  const getProfileIcon = (path: string) => {
    return profileIconMap[path] ?? <UserIcon size={iconSizeToken.small} className="text-slate-400" />
  }
  const orgLabel = authUser?.tenantName?.trim() ?? ''
  const orgInitial = orgLabel.slice(0, 1).toUpperCase()

  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number } | null>(null)
  const [governancePos, setGovernancePos] = useState<{ top: number; left: number } | null>(null)
  const [languagePos, setLanguagePos] = useState<{ top: number; right: number } | null>(null)

  useLayoutEffect(() => {
    if (!isDropdownOpen) return
    const updatePosition = () => {
      const target = dropdownRef.current
      if (!target) return
      const rect = target.getBoundingClientRect()
      setDropdownPos({
        top: rect.bottom + 12,
        left: rect.right
      })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [isDropdownOpen])

  useLayoutEffect(() => {
    if (!isGovernanceOpen) return
    const updatePosition = () => {
      const target = governanceRef.current
      if (!target) return
      const rect = target.getBoundingClientRect()
      setGovernancePos({
        top: rect.bottom + 12,
        left: rect.left
      })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [isGovernanceOpen])

  useEffect(() => {
    const handleLanguageChanged = () => {
      setIsGovernanceOpen(false)
      setIsDropdownOpen(false)
      setIsLanguageOpen(false)
      setGovernancePos(null)
      setDropdownPos(null)
      setLanguagePos(null)
    }
    i18n.on('languageChanged', handleLanguageChanged)
    return () => {
      i18n.off('languageChanged', handleLanguageChanged)
    }
  }, [i18n])

  useLayoutEffect(() => {
    if (!isLanguageOpen) return
    const updatePosition = () => {
      const target = languageRef.current
      if (!target) return
      const rect = target.getBoundingClientRect()
      setLanguagePos({
        top: rect.bottom + 12,
        right: rect.right
      })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [isLanguageOpen])

  const dropdownStyle = useMemo(() => {
    if (!dropdownPos) return undefined
    return {
      '--dropdown-top': `${dropdownPos.top}px`,
      '--dropdown-right': `${dropdownPos.left}px`
    } as CSSProperties
  }, [dropdownPos])

  const governanceDropdownStyle = useMemo(() => {
    if (!governancePos) return undefined
    return {
      '--dropdown-top': `${governancePos.top}px`,
      '--dropdown-left': `${governancePos.left}px`
    } as CSSProperties
  }, [governancePos])

  const languageDropdownStyle = useMemo(() => {
    if (!languagePos) return undefined
    return {
      '--dropdown-top': `${languagePos.top}px`,
      '--dropdown-right': `${languagePos.right}px`
    } as CSSProperties
  }, [languagePos])

  const handleNavigateGovernance = (path: string) => {
    if (governanceLeaveTimerRef.current) {
      window.clearTimeout(governanceLeaveTimerRef.current)
      governanceLeaveTimerRef.current = null
    }
    onNavigate(path)
    setIsGovernanceOpen(false)
    setGovernancePos(null)
  }

  const handleProfileNavigate = (path: string) => {
    if (path === '/profile') {
      handleProfileClick()
      return
    }
    onNavigate(path)
    setIsDropdownOpen(false)
  }
  const handleGovernanceEnter = () => {
    if (governanceLeaveTimerRef.current) {
      window.clearTimeout(governanceLeaveTimerRef.current)
      governanceLeaveTimerRef.current = null
    }
    if (!isGovernanceOpen) {
      setGovernancePos(null)
      setIsGovernanceOpen(true)
    }
  }
  const handleGovernanceLeave = (event: React.MouseEvent) => {
    const nextTarget = event.relatedTarget instanceof Node ? event.relatedTarget : null
    if (
      nextTarget &&
      ((governanceRef.current && governanceRef.current.contains(nextTarget)) ||
        (governancePanelRef.current && governancePanelRef.current.contains(nextTarget)))
    ) {
      return
    }
    if (governanceLeaveTimerRef.current) {
      window.clearTimeout(governanceLeaveTimerRef.current)
    }
    governanceLeaveTimerRef.current = window.setTimeout(() => {
      setIsGovernanceOpen(false)
      setGovernancePos(null)
      governanceLeaveTimerRef.current = null
    }, 150)
  }

  return (
    <div className="@container relative h-14 w-full bg-white/80 dark:bg-slate-900 backdrop-blur-xl border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-4 sticky top-0 z-40 transition-colors duration-300">
      {isSidebarCollapsed && (
        <ExpandToggle variant="topnav" onToggle={onToggleSidebar} className="absolute left-0 bottom-0" />
      )}
      <div className="flex items-center gap-4 min-w-0 @md:min-w-40">
        <Button
          type="button"
          variant="ghost"
          size="tiny"
          className="flex items-center gap-2 group p-0"
          onClick={() => onNavigate('/home')}
        >
        </Button>

        <Button
          type="button"
          variant="ghost"
          size="small"
          className="hidden @md:flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800/50 transition-colors text-body-sm text-slate-600 dark:text-slate-400"
        >
          <div className="w-5 h-5 rounded bg-slate-200 dark:bg-slate-700 flex items-center justify-center text-micro">
            {orgInitial}
          </div>
          <span>{orgLabel}</span>
          <ChevronDown size={iconSizeToken.tiny} className="opacity-50" />
        </Button>
      </div>

      <div className="flex items-center flex-1 min-w-0 mx-2">
      <div className="flex items-center p-1 bg-slate-100/50 dark:bg-slate-900/50 rounded-lg border border-slate-200/50 dark:border-slate-800/50 gap-0.5 mx-auto">
        {topMenus.map((menu) => {
          if (menu.path === '/governance') {
            return (
              <div
                key={menu.id}
                className="relative"
                ref={governanceRef}
                onMouseEnter={handleGovernanceEnter}
                onMouseLeave={handleGovernanceLeave}
              >
                <Button
                  type="button"
                  variant="ghost"
                  size="small"
                  onClick={() => {
                    setIsGovernanceOpen((prev) => {
                      const next = !prev
                      if (next) {
                        setGovernancePos(null)
                      }
                      return next
                    })
                  }}
                  className={`
                    flex items-center gap-2 px-2 @md:px-3 py-1.5 rounded-md text-body transition-none! duration-0! relative
                    ${
                      isGovernanceActive
                        ? 'text-indigo-600 dark:text-indigo-300 bg-white dark:bg-slate-800 shadow-sm hover:bg-white! hover:text-indigo-600! active:bg-white! active:text-indigo-600! dark:hover:bg-slate-800! dark:hover:text-indigo-300! dark:active:bg-slate-800! dark:active:text-indigo-300!'
                        : 'text-slate-500 dark:text-slate-400 hover:text-slate-800! dark:hover:text-slate-200! hover:bg-white/50! dark:hover:bg-slate-800/50! active:bg-white/50! dark:active:bg-slate-800/50!'
                    }
                  `}
                >
                  {getTopMenuIcon(menu.path)}
                  <span className="hidden @md:inline">{getTopMenuLabel(menu)}</span>
                  <ChevronDown
                    size={iconSizeToken.tiny}
                    className={`transition-transform duration-200 ${isGovernanceOpen ? 'rotate-180' : ''} ${isGovernanceActive ? 'text-indigo-500' : 'text-slate-400 dark:text-slate-500'}`}
                  />
                  {isGovernanceActive && <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-indigo-500" />}
                </Button>
                <div
                  className="absolute left-0 top-full h-3 w-full"
                  onMouseEnter={handleGovernanceEnter}
                  onMouseLeave={handleGovernanceLeave}
                  aria-hidden
                />
                {isGovernanceOpen &&
                  governanceDropdownStyle &&
                  createPortal(
                    <div
                      ref={governancePanelRef}
                      style={governanceDropdownStyle}
                      className={`fixed z-[1000000] ${menuWidthClassMap.wide} bg-white dark:bg-[#0F172A] border border-slate-200 dark:border-slate-800 rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150 origin-top-left top-[var(--dropdown-top)] left-[var(--dropdown-left)]`}
                      onMouseEnter={handleGovernanceEnter}
                      onMouseLeave={handleGovernanceLeave}
                    >
                      <div className="px-4 py-2.5 border-b border-slate-100 dark:border-slate-800">
                        <span className="text-micro font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest">
                          {t('top.dropdown.governanceHeader', { defaultValue: menu.name })}
                        </span>
                      </div>
                      <div className="p-2">
                        {(menu.children ?? [])
                          .filter((child) => isMenuVisible(child))
                          .map((child) => {
                          const meta = getGovernanceItemMeta(child.path)
                          const Icon = meta.icon
                          return (
                            <Button
                              key={child.id}
                              type="button"
                              variant="ghost"
                              size="normal"
                              className="w-full whitespace-normal px-3 py-2.5 text-left hover:bg-slate-50 dark:hover:bg-slate-800/50 rounded-lg flex items-start gap-3 transition-colors group"
                              onClick={() => handleNavigateGovernance(child.path)}
                            >
                              <div className={`p-1.5 rounded-lg shrink-0 transition-colors ${meta.className} ${meta.hoverClassName}`}>
                                <Icon size={iconSizeToken.small} />
                              </div>
                              <div className="flex-1 min-w-0">
                                <div className="text-body-sm font-medium text-slate-800 dark:text-slate-200">{getGovernanceItemLabel(child)}</div>
                                <div className="text-legal text-slate-400 dark:text-slate-500 mt-0.5">{getGovernanceItemDescription(child)}</div>
                              </div>
                            </Button>
                          )
                        })}
                      </div>
                    </div>,
                    document.body
                  )}
              </div>
            )
          }

          const isActive = isMenuActive(menu.path)
          return (
            <TabItem
              key={menu.id}
              icon={getTopMenuIcon(menu.path)}
              label={getTopMenuLabel(menu)}
              active={isActive}
              onClick={() => onNavigate(menu.path)}
            />
          )
        })}
      </div>
      </div>

      <div className="flex items-center gap-2.5 min-w-0 @md:min-w-40 justify-end">
        <div className="hidden @md:flex items-center relative">
          <div
            className={`flex items-center gap-2 ${inputContainerWidthClassMap.compact} bg-white dark:bg-slate-900 border border-brand-300/70 dark:border-brand-400/40 rounded-lg transition-all duration-200 hover:border-brand-400/80 focus-within:border-brand-500 focus-within:ring-2 focus-within:ring-brand-500/20`}
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
              <Button
                type="button"
                variant="ghost"
                size="iconSm"
                onClick={() => {
                  setSearchTerm('')
                  searchInputRef.current?.focus()
                }}
                className="p-1.5 mr-1 shrink-0 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded transition-colors"
              >
                <X size={iconSizeToken.small} />
              </Button>
            ) : (
              <span className="flex items-center gap-0.5 shrink-0 text-micro font-mono bg-white dark:bg-slate-800 px-1.5 py-0.5 mr-2 rounded border border-slate-200 dark:border-slate-700 text-slate-400">
                <span className="text-caption">⌘</span> K
              </span>
            )}
          </div>
        </div>

        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={toggleTheme}
          className="p-2 rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
        >
          {isDark ? <Sun size={iconSizeToken.large} /> : <Moon size={iconSizeToken.large} />}
        </Button>

        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="p-2 rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors relative"
        >
          <Bell size={iconSizeToken.large} />
          <span className="absolute top-2 right-2.5 w-1.5 h-1.5 bg-rose-500 rounded-full ring-2 ring-white dark:ring-slate-900 animate-pulse" />
        </Button>

        <div className="relative" ref={languageRef}>
          <Button
            type="button"
            variant="ghost"
            size="small"
            onClick={() => setIsLanguageOpen((prev) => !prev)}
            className="flex items-center gap-1.5 p-2 rounded-lg text-body-sm text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800 transition-colors"
          >
            <Globe size={iconSizeToken.large} className="shrink-0" />
            <ChevronDown size={iconSizeToken.tiny} className={`transition-transform duration-200 ${isLanguageOpen ? 'rotate-180' : ''} text-slate-400 shrink-0`} />
          </Button>

          {isLanguageOpen &&
            languageDropdownStyle &&
            createPortal(
              <div
                ref={languagePanelRef}
                style={languageDropdownStyle}
                className={`fixed z-[1000000] ${menuWidthClassMap.compact} bg-white dark:bg-[#0F172A] border border-slate-200 dark:border-slate-800 rounded-lg shadow-lg overflow-hidden animate-in fade-in zoom-in-95 duration-150 origin-top-right top-[var(--dropdown-top)] right-[calc(100vw-var(--dropdown-right))]`}
              >
                {LANGUAGE_OPTIONS.map((option) => (
                  <Button
                    key={option.id}
                    type="button"
                    variant="ghost"
                    size="small"
                    onClick={() => handleLanguageSelect(option.id)}
                    className="w-full px-3 py-1.5 text-left text-body-sm text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 flex items-center justify-between gap-2"
                    aria-pressed={language === option.id}
                  >
                    <span>{option.label}</span>
                    {language === option.id && <Check size={iconSizeToken.small} className="text-indigo-500" />}
                  </Button>
                ))}
              </div>,
              document.body
            )}
        </div>

        <div className="ml-1 pl-2.5 border-l border-slate-200 dark:border-slate-800 relative" ref={dropdownRef}>
          <Button type="button" variant="ghost" size="iconSm" className="flex items-center gap-2" onClick={() => setIsDropdownOpen((prev) => !prev)}>
            <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-600 p-px hover:shadow-md transition-shadow">
              <div className="w-full h-full rounded-full bg-white dark:bg-slate-900 flex items-center justify-center text-caption uppercase text-slate-700 dark:text-slate-200">
                {normalizedUserName.slice(0, 2)}
              </div>
            </div>
          </Button>

          {isDropdownOpen &&
            dropdownStyle &&
            createPortal(
              <div
                ref={dropdownPanelRef}
                style={dropdownStyle}
                className={`fixed z-[1000000] ${menuWidthClassMap.large} bg-white dark:bg-[#0F172A] border border-slate-200 dark:border-slate-700 rounded-lg shadow-lg overflow-hidden animate-in fade-in zoom-in-95 duration-200 origin-top-right top-[var(--dropdown-top)] right-[calc(100vw-var(--dropdown-right))]`}
              >
                <div className="px-3 py-2.5 border-b border-slate-100 dark:border-slate-700/50 bg-slate-50 dark:bg-slate-900/50">
                  <p className="text-body-sm font-semibold text-slate-800 dark:text-slate-200">{normalizedUserName}</p>
                  <p className="text-caption text-slate-500 truncate">{normalizedUserEmail}</p>
                </div>

                <div className="p-1">
                  {profileItems.map((item) => (
                    <DropdownButton
                      key={item.id}
                      icon={getProfileIcon(item.path)}
                      label={getProfileLabel(item)}
                      onClick={() => handleProfileNavigate(item.path)}
                    />
                  ))}
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
              </div>,
              document.body
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
    <Button
      type="button"
      variant="ghost"
      size="small"
      onClick={onClick}
      className={`
        flex items-center gap-2 px-2 @md:px-3 py-1.5 rounded-md text-body transition-none! duration-0! relative
        ${
          active
            ? 'text-indigo-600 dark:text-indigo-400 bg-white dark:bg-slate-800 shadow-sm hover:bg-white! hover:text-indigo-600! active:bg-white! active:text-indigo-600! dark:hover:bg-slate-800! dark:hover:text-indigo-400! dark:active:bg-slate-800! dark:active:text-indigo-400!'
            : 'text-slate-500 dark:text-slate-400 hover:text-slate-800! dark:hover:text-slate-200! hover:bg-white/50! dark:hover:bg-slate-800/50! active:bg-white/50! dark:active:bg-slate-800/50!'
        }
      `}
    >
      {icon}
      <span className="hidden @md:inline">{label}</span>
      {active && <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-indigo-500" />}
    </Button>
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

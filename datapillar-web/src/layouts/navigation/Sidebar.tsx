import {
  Cloud,
  Code2,
  Cpu,
  GitBranch,
  History,
  BookOpen,
  Play,
  Settings,
  Sparkles,
  Terminal,
  Workflow
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { BrandLogo } from '@/components'
import {
  sidebarWidthClassMap,
  sidebarPaddingClassMap,
  sidebarSpacingClassMap,
  iconSizeToken
} from '@/design-tokens/dimensions'
import { Tooltip } from '@/components/ui'
import { ExpandToggle } from './ExpandToggle'

type View = 'dashboard' | 'workflow' | 'wiki' | 'profile' | 'ide' | 'projects' | 'collaboration'

interface SidebarProps {
  onNavigate: (view: View) => void
  currentView: View
  collapsed: boolean
  onToggleCollapse: () => void
}

export function Sidebar({ onNavigate, currentView, collapsed, onToggleCollapse }: SidebarProps) {
  const { t } = useTranslation(['login', 'navigation'])
  const sidebarWidth = collapsed ? sidebarWidthClassMap.collapsed : sidebarWidthClassMap.normal
  const sectionPadding = collapsed ? sidebarPaddingClassMap.collapsed : sidebarPaddingClassMap.normal
  const stackSpacing = collapsed ? sidebarSpacingClassMap.collapsed : sidebarSpacingClassMap.normal
  const logoHeight = collapsed ? 'h-14' : 'h-[4.5rem]'
  const logoSpacing = collapsed ? 'mb-0' : 'mb-3'
  const contentPaddingY = collapsed ? 'py-0' : 'py-2'
  const topOffset = collapsed ? '' : 'xl:pt-[7px]'

  const brandNameClass = collapsed
    ? undefined
    : 'text-lg font-bold leading-tight tracking-tight text-indigo-600 dark:text-indigo-200'

  const brandTaglineClass = collapsed
    ? undefined
    : 'text-legal font-medium text-slate-400 dark:text-slate-500 mt-0.5 leading-snug'

  return (
    <aside className={`${sidebarWidth} ${topOffset} flex-shrink-0 bg-[#F9FAFB] dark:bg-[#0B1120] border-r border-slate-200 dark:border-slate-800 flex flex-col h-full z-30 overflow-visible relative transition-[width] duration-200 ease-out`}>
      {/* 品牌 Logo 区域 */}
      <div className={`${sectionPadding} relative ${logoHeight} flex items-center ${logoSpacing}`}>
        <div
          className={`flex items-center ${collapsed ? 'w-14 h-14 justify-center mx-auto' : 'w-full h-full gap-2.5 -ml-1'}`}
        >
          <BrandLogo
            size={collapsed ? 32 : 42}
            showText={!collapsed}
            brandName={t('brand.name')}
            brandTagline={t('brand.tagline')}
            nameClassName={brandNameClass}
            taglineClassName={brandTaglineClass}
          />
        </div>

        {!collapsed && (
          <ExpandToggle
            variant="sidebar"
            onToggle={onToggleCollapse}
            className="absolute right-0 bottom-4 xl:-translate-y-[7px]"
          />
        )}
      </div>

      <div className={`flex-1 overflow-y-auto ${sectionPadding} ${contentPaddingY} ${stackSpacing} scrollbar-invisible`}>
        <NavSection title={t('side.sections.build', { ns: 'navigation' })} collapsed={collapsed}>
          <NavItem collapsed={collapsed} icon={<BookOpen size={iconSizeToken.normal} />} label={t('side.items.wiki', { ns: 'navigation' })} active={currentView === 'wiki'} onClick={() => onNavigate('wiki')} />
          <NavItem collapsed={collapsed} icon={<Workflow size={iconSizeToken.normal} />} label={t('side.items.workflow', { ns: 'navigation' })} active={currentView === 'workflow'} onClick={() => onNavigate('workflow')} shortcut="W" />
          <NavItem collapsed={collapsed} icon={<Code2 size={iconSizeToken.normal} />} label={t('side.items.ide', { ns: 'navigation' })} active={currentView === 'ide'} onClick={() => onNavigate('ide')} shortcut="I" />
        </NavSection>

        <NavSection title={t('side.sections.compute', { ns: 'navigation' })} collapsed={collapsed}>
          <NavItem collapsed={collapsed} icon={<Cloud size={iconSizeToken.normal} />} label={t('side.items.warehouses', { ns: 'navigation' })} />
          <NavItem collapsed={collapsed} icon={<Cpu size={iconSizeToken.normal} />} label={t('side.items.jobs', { ns: 'navigation' })} />
          <NavItem collapsed={collapsed} icon={<Settings size={iconSizeToken.normal} />} label={t('side.items.config', { ns: 'navigation' })} />
        </NavSection>

        <NavSection title={t('side.sections.observe', { ns: 'navigation' })} collapsed={collapsed}>
          <NavItem collapsed={collapsed} icon={<Play size={iconSizeToken.normal} />} label={t('side.items.deployments', { ns: 'navigation' })} />
          <NavItem collapsed={collapsed} icon={<Terminal size={iconSizeToken.normal} />} label={t('side.items.logs', { ns: 'navigation' })} />
          <NavItem collapsed={collapsed} icon={<History size={iconSizeToken.normal} />} label={t('side.items.history', { ns: 'navigation' })} />
          <NavItem collapsed={collapsed} icon={<GitBranch size={iconSizeToken.normal} />} label={t('side.items.git', { ns: 'navigation' })} />
        </NavSection>
      </div>

      {!collapsed && (
        <div className="p-4 border-t border-slate-200 dark:border-slate-800">
          <div className="bg-gradient-to-br from-[#1e1b4b] to-[#312e81] dark:from-[#0B1120] dark:to-[#1e293b] rounded-xl p-4 border border-indigo-500/20 shadow-lg relative overflow-hidden group">
            <div className="absolute top-0 right-0 w-20 h-20 bg-indigo-500/20 rounded-full blur-2xl -translate-y-1/2 translate-x-1/2 group-hover:bg-indigo-500/30 transition-colors" />
            <div className="flex items-center justify-between mb-3 relative z-10">
              <div className="flex items-center gap-2 text-white">
                <Sparkles size={14} className="text-indigo-300" />
                <span className="text-caption font-semibold">{t('side.cta.credits', { ns: 'navigation' })}</span>
              </div>
              <span className="text-micro font-mono text-indigo-200">2,400 / 5,000</span>
            </div>
            <div className="w-full h-1.5 bg-indigo-900/50 rounded-full overflow-hidden relative z-10">
              <div className="h-full bg-gradient-to-r from-indigo-400 to-purple-400 w-1/2 rounded-full shadow-[0_0_10px_rgba(129,140,248,0.5)]" />
            </div>
            <button
              type="button"
              className="mt-3 w-full py-1.5 rounded-lg bg-white/10 hover:bg-white/20 border border-white/10 text-micro text-indigo-100 transition-colors relative z-10"
            >
              {t('side.cta.upgrade', { ns: 'navigation' })}
            </button>
          </div>
        </div>
      )}

    </aside>
  )
}

interface NavSectionProps {
  title: string
  children: React.ReactNode
  collapsed: boolean
}

function NavSection({ title, children, collapsed }: NavSectionProps) {
  return (
    <section>
      {collapsed ? (
        <div className="h-px w-full bg-slate-200 dark:bg-slate-800/70 mb-2" />
      ) : (
        <div className="px-2 mb-2 text-caption font-semibold text-slate-400 dark:text-slate-600 uppercase tracking-wider flex items-center gap-2">
          {title}
          <span className="h-px flex-1 bg-slate-200 dark:bg-slate-800" />
        </div>
      )}
      <div className={collapsed ? 'flex flex-col items-center space-y-0.5' : 'space-y-1'}>{children}</div>
    </section>
  )
}

interface NavItemProps {
  icon: React.ReactNode
  label: string
  active?: boolean
  shortcut?: string
  onClick?: () => void
  collapsed?: boolean
}

function NavItem({ icon, label, active, shortcut, onClick, collapsed }: NavItemProps) {
  const buttonStateClass = active
    ? 'bg-white dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 shadow-[0_1px_2px_rgba(0,0,0,0.05)] ring-1 ring-slate-200 dark:ring-slate-700'
    : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800/50'

  const button = (
    <button
      type="button"
      onClick={onClick}
      className={`
        flex items-center transition-all duration-200 group relative overflow-hidden
        ${collapsed ? 'mx-auto size-9 justify-center rounded-2xl' : 'w-full justify-between px-3 py-2 rounded-lg'}
        ${buttonStateClass}
      `}
    >
      <span
        className={`flex items-center ${collapsed ? 'justify-center' : 'gap-3'} relative z-10 ${active ? 'text-indigo-500' : 'opacity-80 group-hover:opacity-100'}`}
      >
        {icon}
        {!collapsed && <span className="text-body-sm">{label}</span>}
      </span>
      {!collapsed && shortcut && (
        <span className={`text-micro font-mono opacity-0 group-hover:opacity-50 transition-opacity ${active ? 'opacity-50' : ''}`}>
          {shortcut}
        </span>
      )}
    </button>
  )

  if (!collapsed) {
    return button
  }

  return (
    <Tooltip content={label} side="right" className="w-full flex justify-center">
      {button}
    </Tooltip>
  )
}

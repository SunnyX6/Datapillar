import { useMemo, useState } from 'react'
import {
  AppWindow,
  ArrowLeft,
  ArrowRight,
  Box,
  Check,
  CheckCircle2,
  Cloud,
  Cpu,
  ExternalLink,
  FileText,
  FlaskConical,
  Globe,
  HardDrive,
  Hash,
  Key,
  LayoutGrid,
  Layers,
  Lock,
  MessagesSquare,
  Monitor,
  Palette,
  SearchCode,
  Settings,
  Shield,
  Terminal,
  Workflow,
  X,
  Zap
} from 'lucide-react'
import { Button } from '@/components/ui'
import { panelWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { RoleItem } from '../permission/Permission'
import { PERMISSION_LEVELS, type PermissionLevel } from '../permission/permissionConstants'
import { FEATURE_SCHEMA, type FeatureModule, type FeatureNode } from './featureSchema'
import { FeatureStepAudit } from './FeatureStepAudit'
import { FeatureStepBase } from './FeatureStepBase'
import { FeatureStepGrant } from './FeatureStepGrant'
import { FeatureStepNavigation } from './FeatureStepNavigation'

const STEPS = [
  { id: 'design', label: '基础定义', desc: '身份与原子动作' },
  { id: 'visibility', label: '导航挂载', desc: '菜单拓扑与权重' },
  { id: 'grant', label: '初始授权', desc: '角色访问矩阵' },
  { id: 'audit', label: '发布审计', desc: '变更差分确认' }
]

const PRESET_ICONS = [
  { id: 'LayoutGrid', component: LayoutGrid },
  { id: 'Terminal', component: Terminal },
  { id: 'Box', component: Box },
  { id: 'Database', component: Layers },
  { id: 'Shield', component: Shield },
  { id: 'Globe', component: Globe },
  { id: 'Cpu', component: Cpu },
  { id: 'Monitor', component: Monitor },
  { id: 'Workflow', component: Workflow },
  { id: 'FlaskConical', component: FlaskConical },
  { id: 'HardDrive', component: HardDrive },
  { id: 'Cloud', component: Cloud },
  { id: 'FileText', component: FileText },
  { id: 'SearchCode', component: SearchCode },
  { id: 'MessagesSquare', component: MessagesSquare },
  { id: 'AppWindow', component: AppWindow },
  { id: 'Settings', component: Settings },
  { id: 'ExternalLink', component: ExternalLink },
  { id: 'Layers', component: Layers },
  { id: 'Key', component: Key },
  { id: 'Lock', component: Lock },
  { id: 'Zap', component: Zap },
  { id: 'Hash', component: Hash }
]

const PRESET_COLORS = [
  { name: 'Indigo', hex: '#6366F1' },
  { name: 'Rose', hex: '#F43F5E' },
  { name: 'Emerald', hex: '#10B981' },
  { name: 'Amber', hex: '#F59E0B' },
  { name: 'Sky', hex: '#0EA5E9' },
  { name: 'Violet', hex: '#8B5CF6' },
  { name: 'Slate', hex: '#64748B' },
  { name: 'Dark', hex: '#1E293B' }
]

const hexToRgb = (hex: string) => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result
    ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
      }
    : { r: 99, g: 102, b: 241 }
}

const rgbToHex = (r: number, g: number, b: number) => {
  return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`
}

const STEP_CARD_CLASS = 'rounded-xl @md:rounded-xl shadow-sm dark:shadow-[0_12px_30px_-16px_rgba(0,0,0,0.45)]'
const STEP_SECTION_TITLE = cn(TYPOGRAPHY.micro, 'font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider')

interface FeatureStepProps {
  selectedId: string
  roles: RoleItem[]
  currentStepIndex: number
  onStepChange: (index: number) => void
}

export type ActiveNode =
  | (FeatureModule & { nodeType: 'module' })
  | (FeatureNode & { nodeType: 'feature'; parent: FeatureModule })

export function FeatureStep({ selectedId, roles, currentStepIndex, onStepChange }: FeatureStepProps) {
  const menuWeight = 10
  const menuBadge: string | null = null
  const [releaseStageId, setReleaseStageId] = useState<'ga' | 'beta' | 'alpha' | 'deprecated'>('ga')
  const [navPlacement, setNavPlacement] = useState<'global' | 'sidebar'>('sidebar')
  const [isIconModalOpen, setIsIconModalOpen] = useState(false)
  const [activeIconId, setActiveIconId] = useState('LayoutGrid')
  const [activeColor, setActiveColor] = useState('#6366F1')
  const [rgb, setRgb] = useState(hexToRgb('#6366F1'))
  const [targetModuleId, setTargetModuleId] = useState<string>(FEATURE_SCHEMA[0]?.id ?? '')
  const [initialPerms, setInitialPerms] = useState<Record<string, PermissionLevel>>(() => {
    const firstRole = roles[0]
    return firstRole ? { [firstRole.id]: 'WRITE' } : {}
  })

  const activeNode = (() => {
    for (const mod of FEATURE_SCHEMA) {
      if (mod.id === selectedId) return { ...mod, nodeType: 'module' as const }
      const child = mod.children.find((res) => res.id === selectedId)
      if (child) return { ...child, nodeType: 'feature' as const, parent: mod }
    }
    return null
  })()

  const ActiveIconComponent = useMemo(() => {
    return PRESET_ICONS.find((icon) => icon.id === activeIconId)?.component || LayoutGrid
  }, [activeIconId])

  if (!activeNode) return null

  const isLastStep = currentStepIndex === STEPS.length - 1
  const isFirstStep = currentStepIndex === 0

  const nextStep = () => {
    if (!isLastStep) onStepChange(currentStepIndex + 1)
  }

  const prevStep = () => {
    if (!isFirstStep) onStepChange(currentStepIndex - 1)
  }

  const handleRgbChange = (channel: 'r' | 'g' | 'b', value: number) => {
    const newRgb = { ...rgb, [channel]: value }
    setRgb(newRgb)
    setActiveColor(rgbToHex(newRgb.r, newRgb.g, newRgb.b))
  }

  return (
    <div className="flex-1 flex flex-col overflow-hidden relative">
      {isIconModalOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <div
            className="absolute inset-0 bg-slate-900/40 backdrop-blur-md animate-in fade-in duration-300"
            onClick={() => setIsIconModalOpen(false)}
          />

          <div className="relative bg-white dark:bg-slate-900 rounded-[32px] shadow-[0_24px_64px_-16px_rgba(0,0,0,0.25)] w-full max-w-3xl h-[560px] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200 border border-slate-200/60 dark:border-slate-800">
            <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between bg-white dark:bg-slate-900 shrink-0">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 bg-slate-900 dark:bg-slate-800 rounded-lg flex items-center justify-center text-white">
                  <Palette size={16} />
                </div>
                <h3 className={cn(TYPOGRAPHY.bodySm, 'font-black text-slate-900 dark:text-slate-100 tracking-tight uppercase')}>Identity Workshop</h3>
              </div>
              <button
                type="button"
                onClick={() => setIsIconModalOpen(false)}
                className="w-8 h-8 flex items-center justify-center hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full text-slate-400 dark:text-slate-500 transition-colors"
              >
                <X size={18} />
              </button>
            </div>

            <div className="flex-1 flex overflow-hidden">
              <div className={`${panelWidthClassMap.wide} border-r border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/80 p-6 flex flex-col overflow-y-auto`}>
                <div className="mb-8">
                  <label className={cn(TYPOGRAPHY.nano, 'font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest mb-4 block')}>
                    Visual Identity
                  </label>
                  <div className="flex justify-center py-6 relative">
                    <div
                      className="absolute w-24 h-24 rounded-full blur-[32px] opacity-20 transition-all duration-700"
                      style={{ backgroundColor: activeColor }}
                    />
                    <div
                      className="w-20 h-20 rounded-[28%] flex items-center justify-center shadow-[0_12px_24px_-4px_rgba(0,0,0,0.15)] transition-all duration-500 z-10"
                      style={{ backgroundColor: activeColor, color: '#fff' }}
                    >
                      <ActiveIconComponent size={36} strokeWidth={2.5} />
                    </div>
                  </div>
                </div>

                <div className="space-y-6">
                  <div className="space-y-3">
                    <label className={cn(TYPOGRAPHY.nano, 'font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest block')}>
                      Palette
                    </label>
                    <div className="grid grid-cols-4 gap-y-4 gap-x-2">
                      {PRESET_COLORS.map((color) => {
                        const isSelected = activeColor === color.hex
                        return (
                          <div key={color.hex} className="flex justify-center">
                            <button
                              type="button"
                              onClick={() => {
                                setActiveColor(color.hex)
                                setRgb(hexToRgb(color.hex))
                              }}
                              className={`w-8 h-8 rounded-full transition-all relative flex items-center justify-center ${
                                isSelected ? 'scale-110' : 'hover:scale-110'
                              }`}
                              style={{ backgroundColor: color.hex }}
                            >
                              {isSelected && (
                                <div className="absolute -inset-1 rounded-full border-2 border-slate-900 dark:border-slate-100 animate-in zoom-in-75 duration-300" />
                              )}
                              {isSelected && <Check size={14} className="text-white relative z-10" strokeWidth={3} />}
                            </button>
                          </div>
                        )
                      })}
                    </div>
                  </div>

                  <div className="space-y-4">
                    <label className={cn(TYPOGRAPHY.nano, 'font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest block')}>
                      RGB Control
                    </label>
                    {(['r', 'g', 'b'] as const).map((channel) => (
                      <div key={channel} className="space-y-2">
                        <div className="flex items-center justify-between">
                          <span className={cn(TYPOGRAPHY.nano, 'font-black uppercase text-slate-400 dark:text-slate-500')}>{channel}</span>
                          <span className={cn(TYPOGRAPHY.micro, 'font-mono font-bold text-slate-700 dark:text-slate-200')}>{rgb[channel]}</span>
                        </div>
                        <input
                          type="range"
                          min="0"
                          max="255"
                          value={rgb[channel]}
                          onChange={(e) => handleRgbChange(channel, parseInt(e.target.value))}
                          className="w-full h-1 bg-slate-100 dark:bg-slate-800 rounded-full appearance-none cursor-pointer accent-brand-500"
                        />
                      </div>
                    ))}
                  </div>

                  <div className="space-y-2 pt-2 border-t border-slate-100 dark:border-slate-800">
                    <label className={cn(TYPOGRAPHY.nano, 'font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest block')}>
                      Hex Code
                    </label>
                    <div className="relative group">
                      <Hash size={12} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
                      <input
                        type="text"
                        value={activeColor.toUpperCase()}
                        onChange={(e) => {
                          const val = e.target.value
                          setActiveColor(val)
                          if (/^#[0-9A-F]{6}$/i.test(val)) setRgb(hexToRgb(val))
                        }}
                        className={cn(
                          TYPOGRAPHY.legal,
                          'w-full pl-8 pr-12 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl font-mono font-black outline-none focus:border-slate-900 dark:focus:border-slate-400 shadow-sm transition-all text-slate-900 dark:text-slate-100'
                        )}
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex-1 p-6 overflow-y-auto bg-white dark:bg-slate-900 custom-scrollbar">
                <div className="grid grid-cols-6 gap-2.5">
                  {PRESET_ICONS.map((icon) => {
                    const IconComp = icon.component
                    const isSelected = activeIconId === icon.id
                    return (
                      <button
                        key={icon.id}
                        type="button"
                        onClick={() => setActiveIconId(icon.id)}
                        className={`aspect-square rounded-xl flex items-center justify-center transition-all ${
                          isSelected
                            ? 'bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 shadow-lg scale-105 ring-1 ring-slate-900 dark:ring-slate-100 ring-offset-2'
                            : 'bg-slate-50 dark:bg-slate-800 text-slate-400 dark:text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700'
                        }`}
                      >
                        <IconComp size={18} strokeWidth={isSelected ? 2.5 : 2} />
                      </button>
                    )
                  })}
                </div>
              </div>
            </div>

            <div className="px-6 py-4 border-t border-slate-100 dark:border-slate-800 bg-slate-50/30 dark:bg-slate-900/80 flex items-center justify-end gap-3 shrink-0">
              <Button
                variant="ghost"
                size="small"
                className={cn(TYPOGRAPHY.micro, 'h-9 px-5 font-black uppercase')}
                onClick={() => setIsIconModalOpen(false)}
              >
                Discard
              </Button>
              <Button
                size="small"
                className={cn(
                  TYPOGRAPHY.micro,
                  'h-9 px-8 bg-slate-900 text-white font-black uppercase rounded-xl hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-slate-200'
                )}
                onClick={() => setIsIconModalOpen(false)}
              >
                Confirm Assets
              </Button>
            </div>
          </div>
        </div>
      )}

      <div className="bg-white dark:bg-slate-900/90 border-b border-slate-200 dark:border-slate-800">
        <div className="px-6 py-4 flex items-center justify-between">
          {STEPS.map((step, index) => (
            <div key={step.id} className="flex-1 flex items-center">
              <div className="flex items-center gap-3 group">
                <div
                  className={cn(
                    TYPOGRAPHY.caption,
                    `w-8 h-8 rounded-xl flex items-center justify-center font-black border-2 transition-all ${
                      index === currentStepIndex
                        ? 'bg-slate-900 border-slate-900 text-white shadow-xl scale-110 dark:bg-slate-100 dark:text-slate-900 dark:border-slate-100'
                        : index < currentStepIndex
                          ? 'bg-emerald-500 border-emerald-500 text-white dark:bg-emerald-500 dark:border-emerald-500'
                          : 'bg-white border-slate-100 text-slate-300 dark:bg-slate-900 dark:border-slate-700 dark:text-slate-600'
                    }`
                  )}
                >
                  {index < currentStepIndex ? <Check size={16} strokeWidth={3} /> : index + 1}
                </div>
                <div className="flex flex-col">
                  <span
                    className={cn(
                      TYPOGRAPHY.caption,
                      `font-black uppercase tracking-widest leading-none ${
                        index === currentStepIndex ? 'text-slate-900 dark:text-slate-100' : 'text-slate-400 dark:text-slate-500'
                      }`
                    )}
                  >
                    {step.label}
                  </span>
                  <span className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 mt-1 font-medium')}>{step.desc}</span>
                </div>
              </div>
              {index < STEPS.length - 1 && (
                <div
                  className={`flex-1 h-0.5 mx-4 rounded-full ${
                    index < currentStepIndex ? 'bg-emerald-500' : 'bg-slate-100 dark:bg-slate-800'
                  }`}
                />
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto custom-scrollbar bg-slate-50 dark:bg-slate-950/35">
        <div className="px-6 py-6">
          {currentStepIndex === 0 && (
            <FeatureStepBase
              activeNode={activeNode}
              activeColor={activeColor}
              ActiveIcon={ActiveIconComponent}
              onOpenIconModal={() => setIsIconModalOpen(true)}
              releaseStageId={releaseStageId}
              onReleaseStageChange={setReleaseStageId}
              cardClassName={STEP_CARD_CLASS}
              sectionTitleClassName={STEP_SECTION_TITLE}
            />
          )}

          {currentStepIndex === 1 && (
            <FeatureStepNavigation
              activeNode={activeNode}
              activeColor={activeColor}
              ActiveIcon={ActiveIconComponent}
              targetModuleId={targetModuleId}
              onTargetModuleChange={setTargetModuleId}
              navPlacement={navPlacement}
              onNavPlacementChange={setNavPlacement}
              cardClassName={STEP_CARD_CLASS}
              sectionTitleClassName={STEP_SECTION_TITLE}
            />
          )}

          {currentStepIndex === 2 && (
            <FeatureStepGrant
              roles={roles}
              initialPerms={initialPerms}
              permissionLevels={PERMISSION_LEVELS}
              onPermissionChange={(roleId, level) => setInitialPerms((prev) => ({ ...prev, [roleId]: level }))}
              cardClassName={STEP_CARD_CLASS}
              sectionTitleClassName={STEP_SECTION_TITLE}
            />
          )}

          {currentStepIndex === 3 && (
            <FeatureStepAudit
              activeNode={activeNode}
              activeColor={activeColor}
              activeIconId={activeIconId}
              targetModuleId={targetModuleId}
              releaseStageId={releaseStageId}
              menuWeight={menuWeight}
              menuBadge={menuBadge}
              cardClassName={STEP_CARD_CLASS}
              sectionTitleClassName={STEP_SECTION_TITLE}
            />
          )}
        </div>
      </div>

      <div className="px-6 h-14 bg-white dark:bg-slate-900/90 border-t border-slate-200 dark:border-slate-800 flex items-center justify-center z-30 shrink-0 shadow-[0_-4px_20px_-10px_rgba(0,0,0,0.05)] dark:shadow-[0_-4px_20px_-10px_rgba(0,0,0,0.35)]">
        <div className="w-full flex items-center justify-between">
          <Button
            variant="outline"
            size="small"
            onClick={prevStep}
            disabled={isFirstStep}
            className="px-5 dark:border-slate-700 dark:text-slate-200"
          >
            <ArrowLeft size={14} className="mr-2" />
            上一步
          </Button>
          <Button
            size="small"
            variant="primary"
            onClick={nextStep}
            className={`px-6 shadow-sm ${isLastStep ? 'bg-emerald-600 hover:bg-emerald-700 dark:bg-emerald-500 dark:hover:bg-emerald-400' : ''}`}
          >
            {isLastStep ? '确认发布' : '继续配置'}
            {!isLastStep && <ArrowRight size={14} className="ml-2" />}
            {isLastStep && <CheckCircle2 size={14} className="ml-2" />}
          </Button>
        </div>
      </div>
    </div>
  )
}

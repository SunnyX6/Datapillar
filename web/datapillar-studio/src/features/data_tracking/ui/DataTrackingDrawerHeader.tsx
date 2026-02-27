import { Book, MousePointerClick, Save, X } from 'lucide-react'
import { Button } from '@/components/ui'
import { iconSizeToken } from '@/design-tokens/dimensions'
import type { DrawerMode } from '../utils/types'

const headerStyleMap: Record<DrawerMode, { bg: string; border: string; accent: string; text: string; subtitle: string }> = {
  SCHEMA: {
    bg: 'bg-purple-50 dark:bg-purple-500/10',
    border: 'border-purple-200/70 dark:border-purple-500/20',
    accent: 'bg-purple-600 text-white',
    text: 'text-purple-900 dark:text-purple-100',
    subtitle: 'text-purple-600 dark:text-purple-300'
  },
  TRACKING: {
    bg: 'bg-emerald-50 dark:bg-emerald-500/10',
    border: 'border-emerald-200/70 dark:border-emerald-500/20',
    accent: 'bg-emerald-600 text-white',
    text: 'text-emerald-900 dark:text-emerald-100',
    subtitle: 'text-emerald-600 dark:text-emerald-300'
  }
}

interface DataTrackingDrawerHeaderProps {
  mode: DrawerMode
  onClose: () => void
}

export function DataTrackingDrawerHeader({ mode, onClose }: DataTrackingDrawerHeaderProps) {
  const style = headerStyleMap[mode]
  const Icon = mode === 'SCHEMA' ? Book : MousePointerClick
  const title = mode === 'SCHEMA' ? '定义元事件 (Schema)' : '新增埋点 (Tracking)'
  const subtitle = mode === 'SCHEMA' ? '统一事件模型标准，支持跨端复用。' : '基于 5W1H 规范完成埋点实施配置。'
  const actionLabel = mode === 'SCHEMA' ? '保存元事件' : '保存埋点'

  return (
    <div className={`h-16 px-6 flex items-center justify-between border-b ${style.bg} ${style.border}`}>
      <div className="flex items-center gap-3 min-w-0">
        <div className={`size-9 rounded-lg flex items-center justify-center ${style.accent} shadow-sm`}>
          <Icon size={iconSizeToken.normal} />
        </div>
        <div className="min-w-0">
          <div className={`text-body-sm font-bold ${style.text}`}>{title}</div>
          <div className={`text-micro ${style.subtitle}`}>{subtitle}</div>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Button
          size="small"
          className={`${mode === 'SCHEMA' ? 'bg-purple-600 hover:bg-purple-700' : 'bg-emerald-600 hover:bg-emerald-700'} text-white shadow-sm`}
        >
          <Save size={iconSizeToken.small} />
          {actionLabel}
        </Button>
        <button
          type="button"
          className="p-2 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-white/60 dark:hover:bg-slate-800 transition-colors"
          onClick={onClose}
          aria-label="关闭抽屉"
        >
          <X size={iconSizeToken.normal} />
        </button>
      </div>
    </div>
  )
}

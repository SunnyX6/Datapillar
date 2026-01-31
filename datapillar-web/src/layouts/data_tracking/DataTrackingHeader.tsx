import { Book, MousePointerClick, Target } from 'lucide-react'
import { Button } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { iconSizeToken } from '@/design-tokens/dimensions'

interface DataTrackingHeaderProps {
  onCreateSchema?: () => void
  onCreateTracking?: () => void
}

export function DataTrackingHeader({ onCreateSchema, onCreateTracking }: DataTrackingHeaderProps) {
  return (
    <div className="flex flex-col gap-4 @md:flex-row @md:items-end @md:justify-between">
      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-3">
          <div className="size-11 rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300 flex items-center justify-center shadow-sm">
            <Target size={iconSizeToken.large} />
          </div>
          <div>
            <h1 className={`${TYPOGRAPHY.title} @md:text-display font-black tracking-tight text-slate-900 dark:text-slate-100`}>
              数据埋点
              <span className="ml-2 text-body-sm font-semibold text-slate-400 dark:text-slate-500">(Data Tracking)</span>
            </h1>
            <p className="text-body-sm text-slate-500 dark:text-slate-400 mt-1 max-w-2xl leading-relaxed">
              一站式管理事件模型（Schema）与埋点方案（Plan），将业务动作抽象为标准事件，并在各端进行落地实施。
            </p>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="header"
          className="border-purple-200 text-purple-700 hover:border-purple-300 hover:text-purple-800 dark:border-purple-500/30 dark:text-purple-200 dark:hover:border-purple-400"
          onClick={onCreateSchema}
        >
          <Book size={iconSizeToken.small} />
          定义元事件
        </Button>
        <Button
          size="header"
          className="bg-slate-900 text-white hover:bg-slate-800 shadow-lg shadow-slate-200/60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:shadow-none"
          onClick={onCreateTracking}
        >
          <MousePointerClick size={iconSizeToken.small} />
          新增埋点
        </Button>
      </div>
    </div>
  )
}

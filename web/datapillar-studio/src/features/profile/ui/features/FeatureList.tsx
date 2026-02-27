import { LayoutGrid, Plus, Search } from 'lucide-react'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import { FEATURE_SCHEMA } from './featureSchema'

interface FeatureListProps {
  selectedId: string
  onSelect: (id: string) => void
}

export function FeatureList({ selectedId, onSelect }: FeatureListProps) {
  return (
    <div className="w-80 flex flex-col border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 shrink-0 h-full">
      <div className="sticky top-0 z-10 bg-white/95 dark:bg-slate-900/90 backdrop-blur border-b border-slate-100 dark:border-slate-800/80">
        <div className="h-14 px-4 flex items-center justify-between">
          <span className={cn(TYPOGRAPHY.caption, 'font-semibold text-slate-900 dark:text-slate-100 uppercase tracking-wider')}>功能列表</span>
          <button
            type="button"
            className="text-brand-600 hover:bg-brand-50 dark:hover:bg-brand-500/10 p-1.5 rounded-lg transition-colors"
            aria-label="新增资源"
          >
            <Plus size={16} />
          </button>
        </div>
        <div className="px-4 pb-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500" size={12} />
            <input
              type="text"
              placeholder="搜索资源 ID..."
              className={cn(
                TYPOGRAPHY.legal,
                'w-full pl-9 pr-3 py-2 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl outline-none focus:bg-white dark:focus:bg-slate-900 focus:ring-4 focus:ring-brand-500/5 transition-all text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500'
              )}
            />
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-1 custom-scrollbar">
        {FEATURE_SCHEMA.map((mod) => (
          <div key={mod.id} className="mb-3">
            <div
              onClick={() => onSelect(mod.id)}
              className={`flex items-center gap-2.5 px-3 py-2 rounded-xl cursor-pointer transition-all ${
                selectedId === mod.id
                  ? 'text-slate-900 dark:text-slate-100 font-semibold'
                  : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/70 hover:text-slate-900 dark:hover:text-slate-100'
              }`}
            >
              <LayoutGrid size={14} className={selectedId === mod.id ? 'text-brand-600 dark:text-brand-300' : 'text-slate-400 dark:text-slate-500'} />
              <span className={cn(TYPOGRAPHY.bodyXs, 'font-bold flex-1 truncate')}>{mod.name}</span>
            </div>
            <div className="mt-1 ml-4 pl-3 border-l-2 border-slate-100 dark:border-slate-800 space-y-0.5">
              {mod.children.map((res) => (
                <div
                  key={res.id}
                  onClick={() => onSelect(res.id)}
                  className={cn(
                    TYPOGRAPHY.legal,
                    `flex items-center px-3 py-1.5 rounded-lg cursor-pointer transition-all group ${
                      selectedId === res.id
                        ? 'text-brand-600 dark:text-brand-300 font-bold bg-brand-50 dark:bg-brand-500/10'
                        : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-800/70'
                    }`
                  )}
                >
                  <span className="truncate flex-1">{res.name}</span>
                  {selectedId === res.id && <div className="w-1.5 h-1.5 rounded-full bg-brand-500 dark:bg-brand-400" />}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

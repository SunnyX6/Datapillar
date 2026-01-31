import { Book, Combine, MousePointerClick, Plus, Search, Settings } from 'lucide-react'
import { Button } from '@/components/ui'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { EVENT_SCHEMAS } from './data'
import { DEFAULT_DOMAIN_ACCENT, DOMAIN_ACCENTS } from './styles'

interface EventLibraryGridProps {
  onCreateSchema?: () => void
}

export function EventLibraryGrid({ onCreateSchema }: EventLibraryGridProps) {
  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 @md:flex-row @md:items-center @md:justify-between">
        <div className="relative w-full @md:max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={iconSizeToken.small} />
          <input
            type="text"
            placeholder="搜索元事件..."
            className="w-full pl-9 pr-4 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg text-body-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-brand-500 dark:focus:border-brand-400 outline-none transition-colors"
          />
        </div>
        <Button variant="outline" size="small" className="self-start @md:self-auto">
          批量导入
        </Button>
      </div>

      <div className="grid grid-cols-1 @md:grid-cols-2 @xl:grid-cols-3 gap-4">
        {EVENT_SCHEMAS.map((schema) => {
          const accent = DOMAIN_ACCENTS[schema.domain] ?? DEFAULT_DOMAIN_ACCENT
          const isComposite = schema.kind === 'COMPOSITE'
          const Icon = isComposite ? Combine : Book

          return (
            <div
              key={schema.id}
              className={`relative rounded-2xl border bg-white dark:bg-slate-900 overflow-hidden group transition-all ${
                isComposite
                  ? 'border-amber-200/80 hover:border-amber-300 dark:border-amber-500/20 dark:hover:border-amber-400/40'
                  : 'border-slate-200 hover:border-brand-300 dark:border-slate-800 dark:hover:border-brand-500/40'
              }`}
            >
              <div className={`h-1 w-full ${isComposite ? 'bg-amber-500' : accent.bar}`} />
              <div className="p-5 flex flex-col h-full">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className={`size-10 rounded-lg flex items-center justify-center ${isComposite ? 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-300' : accent.icon}`}>
                      <Icon size={20} />
                    </div>
                    <div>
                      <h3 className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 group-hover:text-brand-600 dark:group-hover:text-brand-300 transition-colors">
                        {schema.name}
                      </h3>
                      <div className="text-micro text-slate-400 font-mono mt-0.5">{schema.key}</div>
                    </div>
                  </div>
                  <span className={`text-nano px-2 py-1 rounded border font-semibold ${
                    isComposite ? 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-500/10 dark:text-amber-200 dark:border-amber-500/20' : accent.badge
                  }`}
                  >
                    {isComposite ? 'COMPOSITE' : schema.domain}
                  </span>
                </div>

                <p className="text-caption text-slate-500 dark:text-slate-400 leading-relaxed line-clamp-2 min-h-[40px]">
                  {schema.description}
                </p>

                <div className="mt-auto pt-4 space-y-2">
                  {isComposite ? (
                    <>
                      <div className="flex items-center justify-between">
                        <span className="text-nano font-semibold text-amber-500 uppercase tracking-wider">
                          组合逻辑
                        </span>
                      </div>
                      <div className="flex items-center gap-2 bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-200 px-2 py-1.5 rounded-lg text-caption font-mono border border-amber-100 dark:border-amber-500/20">
                        <MousePointerClick size={12} />
                        Recursive Formula
                      </div>
                    </>
                  ) : (
                    <>
                      <div className="flex items-center justify-between">
                        <span className="text-nano font-semibold text-slate-400 uppercase tracking-wider">
                          标准属性
                        </span>
                        <span className="text-nano text-slate-400 flex items-center bg-slate-50 dark:bg-slate-800 px-2 py-1 rounded">
                          <MousePointerClick size={10} className="mr-1" />
                          {schema.usageCount} implementations
                        </span>
                      </div>
                      <div className="flex flex-wrap gap-1.5">
                        {schema.standardProperties.length === 0 ? (
                          <span className="text-nano text-slate-400">暂无标准属性</span>
                        ) : (
                          schema.standardProperties.map((property) => (
                            <span
                              key={property.id}
                              className="px-1.5 py-0.5 rounded border border-purple-100 dark:border-purple-500/30 bg-purple-50/50 dark:bg-purple-500/10 text-purple-700 dark:text-purple-200 text-nano font-mono"
                            >
                              {property.name}
                            </span>
                          ))
                        )}
                      </div>
                    </>
                  )}
                </div>
              </div>

              <button
                type="button"
                className="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-lg text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800"
              >
                <Settings size={iconSizeToken.small} />
              </button>
            </div>
          )
        })}

        <button
          type="button"
          onClick={onCreateSchema}
          className="border-2 border-dashed border-slate-200 dark:border-slate-800 rounded-2xl p-5 flex flex-col items-center justify-center text-slate-400 dark:text-slate-500 hover:border-brand-300 hover:text-brand-600 dark:hover:border-brand-500/60 dark:hover:text-brand-300 hover:bg-brand-50/30 dark:hover:bg-brand-500/10 transition-all group"
        >
          <div className="size-12 rounded-full bg-slate-50 dark:bg-slate-800 flex items-center justify-center mb-3 group-hover:bg-white dark:group-hover:bg-slate-900 group-hover:shadow-sm transition-all">
            <Plus size={24} />
          </div>
          <span className="text-body-sm font-bold">新增元事件</span>
          <span className="text-caption mt-1 text-center">创建可复用的事件模板</span>
        </button>
      </div>
    </div>
  )
}

import {
  Globe,
  MoreHorizontal,
  MousePointer2,
  Search,
  Smartphone,
  Server
} from 'lucide-react'
import { Button } from '@/components/ui'
import { RESPONSIVE_TYPOGRAPHY } from '@/design-tokens/typography'
import { iconSizeToken, tableColumnWidthClassMap } from '@/design-tokens/dimensions'
import { TRACKING_POINTS } from '../utils/data'
import { STATUS_STYLES } from '../utils/styles'

const platformMeta = {
  Web: { icon: Globe, className: 'text-blue-500' },
  App: { icon: Smartphone, className: 'text-purple-500' },
  Server: { icon: Server, className: 'text-amber-500' }
} as const

export function TrackingPlanTable() {
  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 @md:flex-row @md:items-center @md:justify-between">
        <div className="relative w-full @md:max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={iconSizeToken.small} />
          <input
            type="text"
            placeholder="搜索埋点方案..."
            className="w-full pl-9 pr-4 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg text-body-sm text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-brand-500 dark:focus:border-brand-400 outline-none transition-colors"
          />
        </div>
        <Button variant="outline" size="small" className="self-start @md:self-auto">
          按页面筛选
        </Button>
      </div>

      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl overflow-hidden shadow-sm">
        <table className="w-full text-left">
          <thead className="bg-slate-50 dark:bg-slate-800/60 border-b border-slate-200 dark:border-slate-800">
            <tr>
              <th className={`px-6 py-4 ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-semibold text-slate-500 uppercase tracking-wider w-[30%]`}>
                事件 (Schema)
              </th>
              <th className={`px-6 py-4 ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-semibold text-slate-500 uppercase tracking-wider w-[26%]`}>
                上下文 (Where)
              </th>
              <th className={`px-6 py-4 ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-semibold text-slate-500 uppercase tracking-wider w-[24%]`}>
                触发描述
              </th>
              <th className={`px-6 py-4 ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-semibold text-slate-500 uppercase tracking-wider text-center w-[10%]`}>
                状态
              </th>
              <th className={`px-6 py-4 ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-semibold text-slate-500 uppercase tracking-wider text-right`}>
                操作
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            {TRACKING_POINTS.map((point) => {
              const statusStyle = STATUS_STYLES[point.status]
              const platform = platformMeta[point.platform]
              const PlatformIcon = platform.icon

              return (
                <tr key={point.id} className="group hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className="p-2 rounded-lg bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300">
                        <MousePointer2 size={iconSizeToken.normal} />
                      </div>
                      <div>
                        <div className="text-body-sm font-semibold text-slate-900 dark:text-slate-100">
                          {point.schemaName}
                        </div>
                        <div className="text-micro text-slate-400 font-mono mt-0.5">
                          Schema: {point.schemaId}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex flex-col gap-1">
                      <div className="flex items-center text-caption font-semibold text-slate-700 dark:text-slate-200">
                        <PlatformIcon size={iconSizeToken.small} className={`mr-1 ${platform.className}`} />
                        {point.viewPath}
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {point.contextProperties.map((property) => (
                          <span
                            key={property.id}
                            className="px-1.5 py-0.5 rounded border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-500 dark:text-slate-400 text-nano font-mono"
                          >
                            {property.name}
                          </span>
                        ))}
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <div className={`text-caption text-slate-600 dark:text-slate-300 leading-relaxed ${tableColumnWidthClassMap['6xl']}`}>
                      {point.triggerDescription}
                    </div>
                  </td>
                  <td className="px-6 py-4 text-center">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded border text-nano font-semibold uppercase tracking-wide ${statusStyle.className}`}>
                      {statusStyle.label}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <button
                      type="button"
                      className="p-2 rounded-lg text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                    >
                      <MoreHorizontal size={iconSizeToken.normal} />
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

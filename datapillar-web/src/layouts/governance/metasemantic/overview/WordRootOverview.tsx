import { Book, X, Info, GitBranch, Layers, Share2, ExternalLink } from 'lucide-react'
import type { WordRoot } from '../types'
import { drawerWidthClassMap, iconSizeToken } from '@/design-tokens/dimensions'
import { formatTime } from '@/lib/utils'

interface WordRootOverviewProps {
  wordRoot: WordRoot
  onClose: () => void
}

export function WordRootOverview({ wordRoot, onClose }: WordRootOverviewProps) {
  return (
    <aside className={`fixed right-0 top-14 bottom-0 z-30 ${drawerWidthClassMap.responsive} bg-white dark:bg-slate-900 shadow-2xl border-l border-slate-200 dark:border-slate-800 flex flex-col animate-in slide-in-from-right duration-500`}>
      <div className="h-12 md:h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 md:px-6 flex items-center justify-between flex-shrink-0 shadow-sm">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-blue-600 text-white rounded-lg shadow-sm">
            <Book size={iconSizeToken.medium} />
          </div>
          <h2 className="text-body-sm font-semibold text-slate-800 dark:text-slate-100">词根详情</h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
          aria-label="关闭词根详情"
        >
          <X size={iconSizeToken.large} className="text-slate-400" />
        </button>
      </div>

      <div className="flex-1 min-h-0 overflow-auto p-6 custom-scrollbar">
        <div className="mb-6">
          <div className="flex items-center gap-2 mb-2">
            <h1 className="text-heading font-semibold text-slate-900 dark:text-slate-100 tracking-tight">{wordRoot.name}</h1>
            <span className="px-2.5 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 rounded-full text-body-sm font-mono font-semibold border border-blue-100 dark:border-blue-800 uppercase">
              {wordRoot.code}
            </span>
          </div>
          <p className="text-slate-500 dark:text-slate-400 text-body-sm leading-relaxed">{wordRoot.comment || '暂无详细描述...'}</p>
        </div>

        <div className="space-y-6">
          <section>
            <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Info size={iconSizeToken.small} className="text-blue-500" /> 基础信息 (Basic Info)
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">词根编码</div>
                <div className="font-mono text-body-sm font-semibold text-blue-700 dark:text-blue-400 uppercase">{wordRoot.code}</div>
              </div>
              <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 text-center">
                <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">数据类型</div>
                <div className="font-mono text-body-sm font-semibold text-cyan-600 dark:text-cyan-400">{wordRoot.dataType || '-'}</div>
              </div>
              {wordRoot.audit?.creator && (
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">创建人</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">{wordRoot.audit.creator}</div>
                </div>
              )}
              {wordRoot.audit?.createTime && (
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">创建时间</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">{formatTime(wordRoot.audit.createTime)}</div>
                </div>
              )}
            </div>
          </section>

          <section>
            <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <GitBranch size={iconSizeToken.small} className="text-blue-500" /> 关联字段预览
            </div>
            <div className="h-32 rounded-xl border-2 border-dashed border-slate-100 dark:border-slate-800 flex items-center justify-center bg-slate-50/50 dark:bg-slate-800/50">
              <div className="flex flex-col items-center gap-1.5 text-slate-300 dark:text-slate-600">
                <Layers size={iconSizeToken.huge} />
                <span className="text-caption font-medium">关联字段加载中...</span>
              </div>
            </div>
          </section>
        </div>
      </div>

      <div className="p-5 border-t border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 flex gap-3 flex-shrink-0">
        <button className="flex-1 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 py-2.5 rounded-xl font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 transition-all flex items-center justify-center gap-1.5 shadow-sm text-body-sm">
          <Share2 size={iconSizeToken.medium} /> 资产分享
        </button>
        <button className="flex-1 bg-blue-600 text-white py-2.5 rounded-xl font-medium hover:bg-blue-700 shadow-lg transition-all flex items-center justify-center gap-1.5 text-body-sm">
          <ExternalLink size={iconSizeToken.medium} /> 查看使用示例
        </button>
      </div>
    </aside>
  )
}

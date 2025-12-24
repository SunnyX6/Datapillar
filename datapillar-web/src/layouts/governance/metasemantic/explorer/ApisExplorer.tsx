import { ArrowLeft, Plus, Box } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'

interface ApisExplorerProps {
  onBack: () => void
}

export function ApisExplorer({ onBack }: ApisExplorerProps) {
  return (
    <div className="flex-1 flex flex-col overflow-hidden bg-slate-50/30 dark:bg-slate-950/50 animate-in slide-in-from-right-4 duration-300">
      <div className="h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-6 flex items-center justify-between shadow-sm z-10 flex-shrink-0">
        <div className="flex items-center gap-3">
          <button onClick={onBack} className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 transition-all">
            <ArrowLeft size={iconSizeToken.large} />
          </button>
          <h2 className="text-subtitle font-semibold text-slate-800 dark:text-slate-100">数据服务</h2>
        </div>
        <button className="bg-slate-900 dark:bg-blue-600 text-white px-4 py-1.5 rounded-lg text-body-sm font-medium flex items-center gap-1.5 shadow-md hover:bg-blue-600 dark:hover:bg-blue-500 transition-all">
          <Plus size={iconSizeToken.medium} /> 新增服务
        </button>
      </div>

      <div className="flex-1 overflow-auto p-6 custom-scrollbar">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col items-center justify-center py-32 text-slate-400">
            <Box size={iconSizeToken.huge} className="opacity-10 mb-3" />
            <p className="text-subtitle font-medium">数据服务模块开发中...</p>
            <p className="text-body-sm mt-1">敬请期待</p>
          </div>
        </div>
      </div>
    </div>
  )
}

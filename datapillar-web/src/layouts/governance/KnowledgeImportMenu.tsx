/**
 * 外部知识导入菜单
 *
 * 功能：
 * 1. 导入词汇表（Excel / CSV）
 * 2. 创建实体（手动录入）
 * 3. 上传文档（PDF / Markdown）
 */

import type { LucideIcon } from 'lucide-react'
import { FileSpreadsheet, FileText, PlusSquare, UploadCloud, X } from 'lucide-react'

type KnowledgeItem = {
  id: string
  title: string
  description: string
  icon: LucideIcon
  actionIcon?: LucideIcon
}

type KnowledgeImportMenuProps = {
  open: boolean
  onClose: () => void
  isDark: boolean
}

const KNOWLEDGE_ITEMS: KnowledgeItem[] = [
  { id: 'import-glossary', title: 'Import Glossary', description: 'Excel / CSV', icon: FileSpreadsheet, actionIcon: UploadCloud },
  { id: 'create-entity', title: 'Create Entity', description: 'Manual Entry', icon: PlusSquare, actionIcon: UploadCloud },
  { id: 'upload-docs', title: 'Upload Docs', description: 'PDF / Markdown', icon: FileText, actionIcon: UploadCloud }
]

export function KnowledgeImportMenu({ open, onClose, isDark }: KnowledgeImportMenuProps) {
  return (
    <div
      className={`absolute bottom-full left-4 mb-4 ${
        isDark ? 'bg-slate-900/90 border-white/10' : 'bg-white/95 border-slate-200'
      } backdrop-blur-xl border rounded-xl shadow-2xl overflow-hidden transition-all duration-300 origin-bottom-left w-72 ${
        open ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-4 pointer-events-none'
      }`}
    >
      <div
        className={`p-3 border-b flex justify-between items-center ${
          isDark ? 'border-white/5 bg-white/5 text-slate-300' : 'border-slate-200 bg-slate-50 text-slate-700'
        }`}
      >
        <span className="text-xs font-bold uppercase tracking-wide">Knowledge Import</span>
        <button type="button" onClick={onClose}>
          <X size={14} className={isDark ? 'text-slate-400 hover:text-white' : 'text-slate-500 hover:text-slate-900'} />
        </button>
      </div>
      <div className="p-2 space-y-1">
        {KNOWLEDGE_ITEMS.map((item) => (
          <button
            key={item.id}
            type="button"
            className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-colors group text-left ${
              isDark ? 'hover:bg-white/5' : 'hover:bg-slate-100'
            }`}
          >
            <div
              className={`p-1.5 rounded-md ${
                isDark ? 'bg-slate-800 text-slate-300' : 'bg-slate-100 text-slate-600'
              }`}
            >
              <item.icon size={14} />
            </div>
            <div className="flex-1 flex items-center justify-between gap-3">
              <div className="flex-1">
                <div
                  className={`text-sm font-medium ${
                    isDark ? 'text-slate-300 group-hover:text-white' : 'text-slate-700 group-hover:text-slate-900'
                  }`}
                >
                  {item.title}
                </div>
                <span className="text-micro text-slate-500">{item.description}</span>
              </div>
              {item.actionIcon && (
                <item.actionIcon
                  size={14}
                  className={isDark ? 'text-slate-500 group-hover:text-slate-300' : 'text-slate-400 group-hover:text-slate-600'}
                />
              )}
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}

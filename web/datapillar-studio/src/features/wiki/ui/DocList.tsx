import { FileText, CheckCircle, Clock, AlertCircle, File, FileCode, MoreVertical } from 'lucide-react'
import { Card } from '@/components/ui'
import { RESPONSIVE_TYPOGRAPHY, TYPOGRAPHY } from '@/design-tokens/typography'
import type { Document } from '../utils/types'

const StatusBadge = ({ status }: { status: Document['status'] }) => {
  switch (status) {
    case 'indexed':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full ${RESPONSIVE_TYPOGRAPHY.badge} font-medium bg-emerald-100 text-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-200`}>
          <CheckCircle size={12} className="mr-1" /> 已索引
        </span>
      )
    case 'processing':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full ${RESPONSIVE_TYPOGRAPHY.badge} font-medium bg-blue-100 text-blue-800 dark:bg-blue-500/15 dark:text-blue-200`}>
          <Clock size={12} className="mr-1 animate-pulse" /> 处理中
        </span>
      )
    case 'error':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full ${RESPONSIVE_TYPOGRAPHY.badge} font-medium bg-rose-100 text-rose-800 dark:bg-rose-500/15 dark:text-rose-200`}>
          <AlertCircle size={12} className="mr-1" /> 失败
        </span>
      )
    default:
      return null
  }
}

const FileIcon = ({ type }: { type: Document['type'] }) => {
  const baseClass = 'w-7 h-7 rounded flex items-center justify-center mr-3'
  switch (type) {
    case 'pdf':
      return (
        <div className={`${baseClass} bg-rose-100 text-rose-600 dark:bg-rose-500/20 dark:text-rose-300`}>
          <FileText size={14} />
        </div>
      )
    case 'docx':
      return (
        <div className={`${baseClass} bg-blue-100 text-blue-600 dark:bg-blue-500/20 dark:text-blue-300`}>
          <FileText size={14} />
        </div>
      )
    case 'md':
      return (
        <div className={`${baseClass} bg-slate-900 text-white dark:bg-slate-800 dark:text-slate-100`}>
          <FileCode size={14} />
        </div>
      )
    default:
      return (
        <div className={`${baseClass} bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-300`}>
          <File size={14} />
        </div>
      )
  }
}

interface DocListProps {
  spaceId: string
  documents: Document[]
}

export default function DocList({ spaceId, documents }: DocListProps) {
  const filteredDocs = documents.filter((doc) => doc.spaceId === spaceId)

  return (
    <Card padding="none" className="overflow-hidden shadow-sm">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 dark:divide-slate-800">
          <thead className="bg-slate-50 dark:bg-slate-800/60">
            <tr>
              <th scope="col" className={`px-4 py-2.5 text-left ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-medium text-slate-500 uppercase tracking-wider`}>文档名称</th>
              <th scope="col" className={`px-4 py-2.5 text-left ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-medium text-slate-500 uppercase tracking-wider`}>状态</th>
              <th scope="col" className={`px-4 py-2.5 text-left ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-medium text-slate-500 uppercase tracking-wider`}>切片数 / 长度</th>
              <th scope="col" className={`px-4 py-2.5 text-left ${RESPONSIVE_TYPOGRAPHY.tableHeader} font-medium text-slate-500 uppercase tracking-wider`}>上传时间</th>
              <th scope="col" className="relative px-4 py-2.5"><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody className="bg-white dark:bg-slate-900 divide-y divide-slate-200 dark:divide-slate-800">
            {filteredDocs.length > 0 ? (
              filteredDocs.map((doc) => (
                <tr key={doc.id} className="hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors group">
                  <td className="px-4 py-3 whitespace-nowrap">
                    <div className="flex items-center">
                      <FileIcon type={doc.type} />
                      <div>
                        <div className={`${TYPOGRAPHY.bodySm} font-medium text-slate-900 dark:text-slate-100`}>{doc.title}</div>
                        <div className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>{doc.size}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap">
                    <StatusBadge status={doc.status} />
                  </td>
                  <td className={`px-4 py-3 whitespace-nowrap ${TYPOGRAPHY.bodySm} text-slate-500 font-mono`}>
                    <span className="bg-slate-100 dark:bg-slate-800 px-2 py-1 rounded text-slate-700 dark:text-slate-200">
                      {doc.chunkCount} / {doc.tokenCount.toLocaleString()}
                    </span>
                  </td>
                  <td className={`px-4 py-3 whitespace-nowrap ${TYPOGRAPHY.bodySm} text-slate-500 dark:text-slate-400`}>
                    {doc.uploadDate}
                  </td>
                  <td className={`px-4 py-3 whitespace-nowrap text-right ${TYPOGRAPHY.bodySm} font-medium`}>
                    <button className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200">
                      <MoreVertical size={14} />
                    </button>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={5} className={`px-4 py-6 text-center ${TYPOGRAPHY.bodySm} text-slate-500 dark:text-slate-400`}>
                  此空间暂无文档
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Card>
  )
}

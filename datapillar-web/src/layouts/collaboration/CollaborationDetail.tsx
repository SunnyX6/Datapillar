import {
  ArrowRight,
  CheckCircle2,
  Database,
  FileCode,
  GitPullRequest,
  Server,
  ShieldAlert,
  XCircle
} from 'lucide-react'
import { Button } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { Ticket, TicketView } from './types'
import { priorityConfigMap, requestTypeMap, statusConfigMap } from './constants'

interface CollaborationDetailProps {
  selectedTicketView: TicketView
  selectedTicket: Ticket | null
  isDiffOpen: boolean
  commentText: string
  onToggleDiff: () => void
  onCommentTextChange: (value: string) => void
  onApprove: () => void
  onReject: () => void
  onCancel: () => void
  onAddComment: () => void
}

function StatusBadge({ status }: { status: Ticket['status'] }) {
  const config = statusConfigMap[status]
  const Icon = config.icon
  return (
    <span className={cn('flex items-center px-2 py-0.5 rounded text-micro font-bold border uppercase tracking-wide', config.color)}>
      <Icon size={10} className="mr-1.5" />
      {config.label}
    </span>
  )
}

export function CollaborationDetail({
  selectedTicketView,
  selectedTicket,
  isDiffOpen,
  commentText,
  onToggleDiff,
  onCommentTextChange,
  onApprove,
  onReject,
  onCancel,
  onAddComment
}: CollaborationDetailProps) {
  if (!selectedTicket) {
    return (
      <div className="flex-1 flex flex-col bg-white dark:bg-slate-900 min-w-0">
        <div className="flex flex-col items-center justify-center h-full text-slate-400 dark:text-slate-500">
          <div className="w-16 h-16 bg-slate-50 dark:bg-slate-900 rounded-full flex items-center justify-center mb-4">
            <ShieldAlert size={24} className="text-slate-300 dark:text-slate-600" />
          </div>
          <p className={cn(TYPOGRAPHY.bodySm, 'font-medium text-slate-500 dark:text-slate-400')}>未选择工单</p>
          <p className={cn(TYPOGRAPHY.caption, 'text-slate-400 dark:text-slate-500 mt-1')}>请从列表选择工单查看详情。</p>
        </div>
      </div>
    )
  }

  const isGenericType =
    selectedTicket.type === 'API_PUBLISH'
    || selectedTicket.type === 'SCHEMA_CHANGE'
    || selectedTicket.type === 'DQ_REPORT'

  return (
    <div className="flex-1 flex flex-col bg-white dark:bg-slate-900 min-w-0">
      <div className="h-14 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-6 flex-shrink-0">
        <div className="flex items-center space-x-4">
          <div className="flex flex-col">
            <span className={cn(TYPOGRAPHY.caption, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider font-bold mb-0.5')}>
              当前处理人
            </span>
            <div className="flex items-center space-x-2">
              <div className="w-5 h-5 rounded-full bg-gradient-to-br from-indigo-500 to-purple-500 text-white text-nano flex items-center justify-center font-bold">
                {selectedTicket.assignee.avatar}
              </div>
              <span className={cn(TYPOGRAPHY.bodySm, 'font-bold text-slate-900 dark:text-slate-100')}>{selectedTicket.assignee.name}</span>
            </div>
          </div>
        </div>

        <div className="flex items-center space-x-3">
          {selectedTicketView === 'INBOX' && (
            <>
              <Button onClick={onReject} variant="dangerOutline" size="header" className="py-1.5 @md:py-2">
                <XCircle size={14} />
                拒绝
              </Button>
              <Button onClick={onApprove} size="header" className="py-1.5 @md:py-2">
                <CheckCircle2 size={14} />
                批准请求
              </Button>
            </>
          )}
          {selectedTicketView === 'SENT' && (
            <Button onClick={onCancel} variant="dangerOutline" size="header" className="py-1.5 @md:py-2">
              撤回申请
            </Button>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto custom-scrollbar p-6">
        <div className="mb-6">
          <div className="flex items-center space-x-3 mb-4">
            <StatusBadge status={selectedTicket.status} />
            <span className={cn(TYPOGRAPHY.caption, 'text-slate-400 dark:text-slate-500 font-mono')}>#{selectedTicket.id}</span>
            <span
              className={cn(
                'px-2 py-0.5 rounded text-micro font-bold uppercase tracking-wide',
                priorityConfigMap[selectedTicket.details.priority].className
              )}
            >
              {priorityConfigMap[selectedTicket.details.priority].label}优先级
            </span>
          </div>
          <h1 className={cn(TYPOGRAPHY.heading, 'font-bold text-slate-900 dark:text-slate-100 leading-tight mb-4')}>
            {selectedTicket.title}
          </h1>

          <div className="bg-slate-50 dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-5 relative overflow-hidden">
            <div className="absolute top-0 left-0 w-1 h-full bg-brand-500"></div>

            {selectedTicket.type === 'DATA_ACCESS' && (
              <div>
                <div className="flex items-center space-x-2 mb-4">
                  <Database size={16} className="text-slate-400 dark:text-slate-500" />
                  <span className={cn(TYPOGRAPHY.bodySm, 'font-bold text-slate-900 dark:text-slate-100')}>{selectedTicket.details.target}</span>
                </div>
                <div className="grid grid-cols-2 gap-4 mb-4">
                  <div className="p-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg">
                    <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold mb-1">权限</div>
                    <div className="flex flex-wrap gap-1">
                      {selectedTicket.details.permissions?.map((permission) => (
                        <span
                          key={permission}
                          className="px-2 py-0.5 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 text-micro rounded font-mono font-bold border border-blue-100 dark:border-blue-800"
                        >
                          {permission}
                        </span>
                      ))}
                    </div>
                    {selectedTicket.details.duration && (
                      <div className="mt-2 text-micro text-slate-400 dark:text-slate-500">期限：{selectedTicket.details.duration}</div>
                    )}
                  </div>
                </div>

                {selectedTicket.details.selectedColumns && (
                  <div className="mb-4">
                    <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold mb-2">申请字段</div>
                    <div className="flex flex-wrap gap-2">
                      {selectedTicket.details.selectedColumns.map((column) => (
                        <span
                          key={column}
                          className={cn(
                            'px-2 py-1 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded text-slate-600 dark:text-slate-300 font-mono',
                            TYPOGRAPHY.caption
                          )}
                        >
                          {column}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                <p className={cn(TYPOGRAPHY.bodySm, 'text-slate-600 dark:text-slate-300 leading-relaxed bg-white dark:bg-slate-900 p-3 rounded-lg border border-slate-200 dark:border-slate-800')}>
                  <span className="text-micro text-slate-400 dark:text-slate-500 block mb-1 uppercase font-bold">申请理由</span>
                  {selectedTicket.details.description}
                </p>
              </div>
            )}

	            {selectedTicket.type === 'CODE_REVIEW' && (
	              <div>
	                <div className="flex items-center justify-between mb-4">
	                  <div className="flex items-center space-x-2">
	                    <FileCode size={16} className="text-slate-400 dark:text-slate-500" />
	                    <span className={cn(TYPOGRAPHY.bodySm, 'font-bold text-slate-900 dark:text-slate-100')}>{selectedTicket.details.target}</span>
	                  </div>
	                  <div className={cn('flex items-center space-x-3 font-mono', TYPOGRAPHY.caption)}>
	                    <span className="text-green-600">+{selectedTicket.details.diff?.added}</span>
	                    <span className="text-red-600">-{selectedTicket.details.diff?.removed}</span>
	                  </div>
	                </div>
	                <p className={cn(TYPOGRAPHY.bodySm, 'text-slate-600 dark:text-slate-300 leading-relaxed mb-4')}>
	                  {selectedTicket.details.description}
	                </p>
	                <Button
	                  type="button"
	                  onClick={onToggleDiff}
	                  variant="outline"
	                  size="normal"
	                  className={cn(
	                    'w-full font-bold text-brand-600 dark:text-brand-300 hover:border-brand-300 dark:hover:border-brand-400/50 hover:bg-brand-50 dark:hover:bg-brand-900/20',
	                    TYPOGRAPHY.caption
	                  )}
	                >
	                  <GitPullRequest size={14} />
	                  {isDiffOpen ? '收起差异概览' : '查看代码差异'}
	                </Button>
	                {isDiffOpen && (
	                  <div className={cn(TYPOGRAPHY.caption, 'mt-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-3 text-slate-600 dark:text-slate-300')}>
	                    <div className="flex items-center justify-between">
	                      <span>新增 {selectedTicket.details.diff?.added ?? 0} 行</span>
	                      <span>删除 {selectedTicket.details.diff?.removed ?? 0} 行</span>
	                    </div>
	                    <div className="mt-2 text-micro text-slate-400 dark:text-slate-500">详细差异已同步到评审系统</div>
	                  </div>
	                )}
	              </div>
	            )}

	            {selectedTicket.type === 'RESOURCE_OPS' && (
	              <div>
	                <div className="flex items-center space-x-2 mb-4">
	                  <Server size={16} className="text-slate-400 dark:text-slate-500" />
	                  <span className={cn(TYPOGRAPHY.bodySm, 'font-bold text-slate-900 dark:text-slate-100')}>{selectedTicket.details.target}</span>
	                </div>
	                <div className="flex items-center justify-between bg-white dark:bg-slate-900 p-4 rounded-lg border border-slate-200 dark:border-slate-800 mb-4">
	                  <div>
	                    <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold">当前</div>
	                    <div className={cn(TYPOGRAPHY.bodySm, 'font-mono font-bold text-slate-900 dark:text-slate-100')}>{selectedTicket.details.resource?.current}</div>
	                  </div>
	                  <ArrowRight size={16} className="text-slate-300 dark:text-slate-600" />
	                  <div>
	                    <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold">申请</div>
	                    <div className={cn(TYPOGRAPHY.bodySm, 'font-mono font-bold text-brand-600')}>
	                      {selectedTicket.details.resource?.requested}
	                    </div>
	                  </div>
	                </div>
	                <p className={cn(TYPOGRAPHY.bodySm, 'text-slate-600 dark:text-slate-300 leading-relaxed')}>{selectedTicket.details.description}</p>
	              </div>
	            )}

	            {isGenericType && (
	              <div>
	                <div className="flex items-center space-x-2 mb-4">
                  {(() => {
                    const Icon = requestTypeMap[selectedTicket.type].icon
                    return <Icon size={16} className={requestTypeMap[selectedTicket.type].accentClass} />
                  })()}
	                  <span className={cn(TYPOGRAPHY.bodySm, 'font-bold text-slate-900 dark:text-slate-100')}>
	                    {requestTypeMap[selectedTicket.type].title}
	                  </span>
	                </div>
	                <div className="grid grid-cols-2 gap-4 mb-4">
	                  <div className="p-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg">
	                    <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold mb-1">目标对象</div>
	                    <div className={cn(TYPOGRAPHY.bodySm, 'text-slate-700 dark:text-slate-200 font-mono')}>{selectedTicket.details.target}</div>
	                  </div>
	                  <div className="p-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg">
	                    <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold mb-1">期望完成</div>
	                    <div className={cn(TYPOGRAPHY.bodySm, 'text-slate-700 dark:text-slate-200 font-mono')}>
	                      {selectedTicket.details.expectedDate || '未填写'}
	                    </div>
	                  </div>
	                </div>
	                {selectedTicket.details.tags.length > 0 && (
	                  <div className="mb-4">
	                    <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold mb-2">标签</div>
	                    <div className="flex flex-wrap gap-2">
	                      {selectedTicket.details.tags.map((tag) => (
	                        <span
	                          key={tag}
	                          className={cn(
	                            'px-2 py-1 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded text-slate-600 dark:text-slate-300',
	                            TYPOGRAPHY.caption
	                          )}
	                        >
	                          {tag}
	                        </span>
	                      ))}
	                    </div>
	                  </div>
	                )}
	                <p className={cn(TYPOGRAPHY.bodySm, 'text-slate-600 dark:text-slate-300 leading-relaxed bg-white dark:bg-slate-900 p-3 rounded-lg border border-slate-200 dark:border-slate-800')}>
	                  <span className="text-micro text-slate-400 dark:text-slate-500 block mb-1 uppercase font-bold">需求说明</span>
	                  {selectedTicket.details.description}
	                </p>
	              </div>
	            )}
          </div>
        </div>

	        <div className="border-t border-slate-200 dark:border-slate-800 pt-6">
	          <h3 className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-900 dark:text-slate-100 uppercase tracking-wider mb-6')}>流转记录</h3>
	          <div className="space-y-6 relative">
	            <div className="absolute left-3.5 top-0 bottom-0 w-px bg-slate-200 dark:bg-slate-800"></div>

            {selectedTicket.timeline.map((event) => (
              <div key={event.id} className="relative flex items-start group">
	                <div className="w-7 h-7 rounded-full bg-white dark:bg-slate-900 border-2 border-slate-200 dark:border-slate-800 flex items-center justify-center text-micro font-bold text-slate-500 dark:text-slate-400 relative z-10 mr-4 shadow-sm group-hover:border-brand-200 dark:group-hover:border-brand-400/50 group-hover:text-brand-600 dark:group-hover:text-brand-300 transition-colors">
	                  {event.user.avatar}
	                </div>
	                <div className="flex-1 bg-white dark:bg-slate-900 p-3 rounded-lg border border-slate-200 dark:border-slate-800 shadow-sm hover:shadow-md transition-shadow">
	                  <div className="flex justify-between items-start mb-1">
	                    <span className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-900 dark:text-slate-100')}>
	                      {event.user.name} <span className="font-normal text-slate-500 dark:text-slate-400">{event.action}</span>
	                    </span>
	                    <span className="text-micro text-slate-400 dark:text-slate-500">{event.time}</span>
	                  </div>
	                  {event.comment && (
	                    <p className={cn(TYPOGRAPHY.caption, 'text-slate-600 dark:text-slate-300 mt-2 bg-slate-50 dark:bg-slate-900 p-2 rounded border border-slate-100 dark:border-slate-800')}>
	                      {event.comment}
	                    </p>
	                  )}
	                </div>
              </div>
            ))}

            <div className="relative flex items-start pl-11 pt-2">
              <div className="flex-1">
                <div className="relative">
	                  <textarea
	                    value={commentText}
	                    onChange={(event) => onCommentTextChange(event.target.value)}
	                    className={cn(
	                      'w-full p-3 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:bg-white dark:focus:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition-all resize-none',
	                      TYPOGRAPHY.bodySm
	                    )}
	                    rows={3}
	                    placeholder="添加评论或备注..."
	                  ></textarea>
                  <div className="absolute bottom-2 right-2 flex space-x-2">
                    <Button
                      type="button"
                      onClick={onAddComment}
	                      disabled={!commentText.trim()}
	                      variant="primary"
	                      size="compact"
	                      className="font-bold"
	                    >
	                      评论
	                    </Button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

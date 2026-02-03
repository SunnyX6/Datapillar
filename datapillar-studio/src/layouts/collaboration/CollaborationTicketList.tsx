import { Filter, Inbox, Search } from 'lucide-react'
import { Button } from '@/components/ui'
import { menuWidthClassMap, panelWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { Ticket, TicketStatus, TicketView } from './types'
import { statusConfigMap, statusFilterOptions, typeIconMap } from './constants'

interface CollaborationTicketListProps {
  tickets: Ticket[]
  selectedTicketId: string | null
  listQuery: string
  statusFilter: TicketStatus | 'ALL'
  isFilterOpen: boolean
  getTicketView: (ticketId: string) => TicketView
  onListQueryChange: (value: string) => void
  onToggleFilter: () => void
  onSelectStatus: (status: TicketStatus | 'ALL') => void
  onSelectTicket: (ticketId: string) => void
}

function TypeIcon({ type }: { type: Ticket['type'] }) {
  const config = typeIconMap[type]
  const Icon = config.icon
  return <Icon size={14} className={config.className} />
}

function StatusBadge({ status }: { status: Ticket['status'] }) {
  const config = statusConfigMap[status]

  return (
    <span className={cn('flex items-center px-2 py-0.5 rounded text-micro font-bold border uppercase tracking-wide', config.color)}>
      {config.label}
    </span>
  )
}

export function CollaborationTicketList({
  tickets,
  selectedTicketId,
  listQuery,
  statusFilter,
  isFilterOpen,
  getTicketView,
  onListQueryChange,
  onToggleFilter,
  onSelectStatus,
  onSelectTicket
}: CollaborationTicketListProps) {
  return (
    <div className={cn(panelWidthClassMap.collaborationList, 'border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col flex-shrink-0')}>
      <div className="h-14 border-b border-slate-200 dark:border-slate-800 flex items-center px-4 flex-shrink-0 sticky top-0 bg-white dark:bg-slate-900 z-10">
        <div className="relative w-full">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500" size={14} />
          <input
            type="text"
            value={listQuery}
            onChange={(event) => onListQueryChange(event.target.value)}
            placeholder="搜索工单..."
            className={cn(
              'w-full pl-8 pr-4 py-1.5 bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 rounded-lg text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-1 focus:ring-brand-500 focus:bg-white dark:focus:bg-slate-900 transition-all',
              TYPOGRAPHY.bodySm
            )}
          />
        </div>
        <div className="relative">
          <Button
            type="button"
            onClick={onToggleFilter}
            variant="ghost"
            size="iconSm"
            className="ml-2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg"
          >
            <Filter size={14} />
          </Button>
          <div
            className={cn(
              menuWidthClassMap.small,
              'absolute right-0 top-full mt-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-lg overflow-hidden z-20 transition-all duration-150 origin-top-right',
              isFilterOpen ? 'opacity-100 scale-100 pointer-events-auto' : 'opacity-0 scale-95 pointer-events-none'
            )}
          >
            {statusFilterOptions.map((option) => (
              <Button
                key={option.value}
                type="button"
                onClick={() => onSelectStatus(option.value)}
                variant="ghost"
                size="small"
                className={cn(
                  'w-full px-3 py-2 justify-start text-left text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors',
                  TYPOGRAPHY.caption,
                  statusFilter === option.value && 'text-brand-600 dark:text-brand-300 font-semibold'
                )}
              >
                {option.label}
              </Button>
            ))}
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto custom-scrollbar">
        {tickets.length === 0 ? (
          <div className="p-8 text-center">
            <div className="w-12 h-12 bg-slate-50 dark:bg-slate-800/60 rounded-full flex items-center justify-center mx-auto mb-3">
              <Inbox size={20} className="text-slate-300 dark:text-slate-600" />
            </div>
            <p className={cn(TYPOGRAPHY.caption, 'text-slate-500 dark:text-slate-400')}>没有找到相关请求</p>
          </div>
        ) : (
          tickets.map((ticket) => {
            const ticketView = getTicketView(ticket.id)
            const userLabel =
              ticketView === 'INBOX'
                ? ticket.requester.name
                : ticketView === 'SENT'
                  ? `等待 ${ticket.assignee.name}`
                  : `发起人 ${ticket.requester.name}`

            const avatarLabel = ticketView === 'INBOX' ? ticket.requester.avatar : ticket.assignee.avatar

            return (
              <div
                key={ticket.id}
                onClick={() => onSelectTicket(ticket.id)}
                className={cn(
                  'p-4 border-b border-slate-100 dark:border-slate-800 cursor-pointer transition-all hover:bg-slate-50 dark:hover:bg-slate-800/60 relative group border-l-4',
                  selectedTicketId === ticket.id
                    ? 'bg-brand-50/50 dark:bg-brand-900/20 border-l-brand-600'
                    : 'border-l-transparent'
                )}
              >
                <div className="flex justify-between items-start mb-2">
                  <div className="flex items-center space-x-2">
                    <TypeIcon type={ticket.type} />
                    <span className="text-micro text-slate-400 dark:text-slate-500 font-mono">{ticket.id}</span>
                  </div>
                  <span className="text-micro text-slate-400 dark:text-slate-500">{ticket.updatedAt}</span>
                </div>

                <h3
                  className={cn(
                    'font-bold mb-1 leading-snug line-clamp-2',
                    TYPOGRAPHY.bodySm,
                    selectedTicketId === ticket.id ? 'text-brand-900 dark:text-brand-200' : 'text-slate-900 dark:text-slate-100'
                  )}
                >
                  {ticket.title}
                </h3>

                <div className="mt-3 flex items-center justify-between">
                  <div className="flex items-center">
                    <div className="flex items-center space-x-2">
                      <div className="w-5 h-5 rounded-full bg-slate-200 dark:bg-slate-800 border border-white dark:border-slate-900 shadow-sm flex items-center justify-center text-nano font-bold text-slate-600 dark:text-slate-200">
                        {avatarLabel}
                      </div>
                      <span className={cn(TYPOGRAPHY.caption, 'text-slate-500 dark:text-slate-400')}>{userLabel}</span>
                    </div>
                  </div>
                  <StatusBadge status={ticket.status} />
                </div>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}

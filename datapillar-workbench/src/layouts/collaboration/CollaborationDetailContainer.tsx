import { useState } from 'react'
import type { Ticket, TicketView } from './types'
import { CollaborationDetail } from './CollaborationDetail'

interface CollaborationDetailContainerProps {
  selectedTicketView: TicketView
  selectedTicket: Ticket | null
  onApprove: () => void
  onReject: () => void
  onCancel: () => void
  onAddComment: (comment: string) => void
}

/**
 * 只承载详情区的“纯 UI 状态”（diff 展开、评论输入）。
 * 通过在父组件使用 key（ticketId）挂载，切换工单时自动重置。
 */
export function CollaborationDetailContainer({
  selectedTicketView,
  selectedTicket,
  onApprove,
  onReject,
  onCancel,
  onAddComment
}: CollaborationDetailContainerProps) {
  const [isDiffOpen, setIsDiffOpen] = useState(false)
  const [commentText, setCommentText] = useState('')

  const handleAddComment = () => {
    const trimmed = commentText.trim()
    if (!trimmed) return
    onAddComment(trimmed)
    setCommentText('')
  }

  return (
    <CollaborationDetail
      selectedTicketView={selectedTicketView}
      selectedTicket={selectedTicket}
      isDiffOpen={isDiffOpen}
      commentText={commentText}
      onToggleDiff={() => setIsDiffOpen((prev) => !prev)}
      onCommentTextChange={setCommentText}
      onApprove={onApprove}
      onReject={onReject}
      onCancel={onCancel}
      onAddComment={handleAddComment}
    />
  )
}

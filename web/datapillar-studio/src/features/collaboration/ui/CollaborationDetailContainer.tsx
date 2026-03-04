import { useState } from 'react'
import type { Ticket,TicketView } from '../utils/types'
import { CollaborationDetail } from './CollaborationDetail'

interface CollaborationDetailContainerProps {
 selectedTicketView:TicketView
 selectedTicket:Ticket | null
 onApprove:() => void
 onReject:() => void
 onCancel:() => void
 onAddComment:(comment:string) => void
}

/**
 * Only carries the details area"pure UI Status"(diff Expand,Comment input).* By using in parent component key(ticketId)mount,Automatically reset when switching work orders.*/
export function CollaborationDetailContainer({
 selectedTicketView,selectedTicket,onApprove,onReject,onCancel,onAddComment
}:CollaborationDetailContainerProps) {
 const [isDiffOpen,setIsDiffOpen] = useState(false)
 const [commentText,setCommentText] = useState('')

 const handleAddComment = () => {
 const trimmed = commentText.trim()
 if (!trimmed) return
 onAddComment(trimmed)
 setCommentText('')
 }

 return (<CollaborationDetail
 selectedTicketView={selectedTicketView}
 selectedTicket={selectedTicket}
 isDiffOpen={isDiffOpen}
 commentText={commentText}
 onToggleDiff={() => setIsDiffOpen((prev) =>!prev)}
 onCommentTextChange={setCommentText}
 onApprove={onApprove}
 onReject={onReject}
 onCancel={onCancel}
 onAddComment={handleAddComment}
 />)
}

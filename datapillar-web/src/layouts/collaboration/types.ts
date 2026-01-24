export type TicketType = 'DATA_ACCESS' | 'CODE_REVIEW' | 'RESOURCE_OPS' | 'API_PUBLISH' | 'SCHEMA_CHANGE' | 'DQ_REPORT'
export type TicketStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED'
export type TicketPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type TicketView = 'INBOX' | 'SENT' | 'ARCHIVE'
export type SmartView = 'MENTIONED' | 'URGENT'
export type QuickFilter = Extract<TicketType, 'DATA_ACCESS' | 'CODE_REVIEW' | 'RESOURCE_OPS'>
export type CollaborationSidebarNav =
  | { kind: 'FOLDER'; view: TicketView }
  | { kind: 'SMART_VIEW'; view: SmartView }
  | { kind: 'QUICK_FILTER'; filter: QuickFilter }
export type CreateStep = 'SELECT_TYPE' | 'FILL_FORM'
export type PermissionType = 'SELECT' | 'EXPORT'

export interface UserProfile {
  name: string
  avatar: string
  role: string
}

export interface TicketDetails {
  target: string
  description: string
  priority: TicketPriority
  tags: string[]
  diff?: { added: number; removed: number }
  permissions?: string[]
  selectedColumns?: string[]
  resource?: { current: string; requested: string }
  duration?: string
  expectedDate?: string
}

export interface Ticket {
  id: string
  title: string
  type: TicketType
  status: TicketStatus
  createdAt: string
  updatedAt: string
  requester: UserProfile
  assignee: UserProfile
  details: TicketDetails
  timeline: {
    id: string
    user: UserProfile
    action: string
    comment?: string
    time: string
  }[]
}

export interface CatalogTable {
  id: string
  name: string
  description: string
  owner: string
  rows: string
  columns: { name: string; type: string; desc: string; isPII?: boolean }[]
}

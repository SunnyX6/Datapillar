export type StudioProjectStatus = 'active' | 'archived' | 'paused' | 'deleted'

export interface StudioProjectItem {
  id: number
  name: string
  description?: string | null
  ownerId: number
  ownerName?: string | null
  status: StudioProjectStatus
  tags?: string[] | null
  isFavorite?: boolean | null
  isVisible?: boolean | null
  memberCount?: number | null
  lastAccessedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface ListStudioProjectsParams {
  keyword?: string
  status?: StudioProjectStatus
  onlyFavorites?: boolean
  onlyVisible?: boolean
  limit?: number
  offset?: number
  maxLimit?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export interface ListStudioProjectsResult {
  items: StudioProjectItem[]
  total: number
  limit: number
  offset: number
}

export interface CreateStudioProjectRequest {
  name: string
  description?: string
  tags?: string[]
  isVisible?: boolean
}

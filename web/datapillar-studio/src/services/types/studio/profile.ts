export interface StudioUserProfile {
  id: number
  tenantId?: number | null
  username: string
  nickname?: string | null
  email?: string | null
  phone?: string | null
  status?: number | null
  createdAt?: string
  updatedAt?: string
}

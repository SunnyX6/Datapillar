import { API_BASE, API_PATH, requestData } from '@/lib/api'
import type { StudioUserProfile } from '@/types/studio/profile'

export type { StudioUserProfile } from '@/types/studio/profile'

export async function getMyProfile(): Promise<StudioUserProfile> {
  return requestData<StudioUserProfile>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.userProfile.me
  })
}

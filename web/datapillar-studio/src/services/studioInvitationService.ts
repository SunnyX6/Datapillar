import { API_BASE, API_PATH, requestData, requestEnvelope } from '@/api'
import type { InvitationDetailResponse, InvitationRegisterRequest } from '@/services/types/studio/tenant'

export type { InvitationDetailResponse, InvitationRegisterRequest } from '@/services/types/studio/tenant'

export async function getInvitationByCode(
  inviteCode: string
): Promise<InvitationDetailResponse> {
  const normalizedInviteCode = inviteCode.trim()
  return requestData<InvitationDetailResponse>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.invitation.detail(encodeURIComponent(normalizedInviteCode))
  })
}

export async function registerInvitation(
  request: InvitationRegisterRequest
): Promise<void> {
  await requestEnvelope<void, InvitationRegisterRequest>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.invitation.register,
    method: 'POST',
    data: request
  })
}

import { API_BASE, API_PATH, requestRaw } from '@/lib/api'

export interface StudioServiceHealthPayload {
  status?: unknown
}

export async function getStudioServiceHealth(): Promise<StudioServiceHealthPayload> {
  return requestRaw<StudioServiceHealthPayload>({
    baseURL: API_BASE.studioActuator,
    url: API_PATH.health.service,
    method: 'GET'
  })
}

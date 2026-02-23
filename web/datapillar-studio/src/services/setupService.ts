import { API_BASE, API_PATH, requestData } from '@/lib/api'
import type {
  SetupInitializeRequest,
  SetupInitializeResponse,
  SetupStatusResponse
} from '@/types/setup'

export async function getSetupStatus(): Promise<SetupStatusResponse> {
  return requestData<SetupStatusResponse>({
    baseURL: API_BASE.studioSetup,
    url: API_PATH.setup.status
  })
}

export async function initializeSetup(
  request: SetupInitializeRequest
): Promise<SetupInitializeResponse> {
  return requestData<SetupInitializeResponse, SetupInitializeRequest>({
    baseURL: API_BASE.studioSetup,
    url: API_PATH.setup.root,
    method: 'POST',
    data: request
  })
}

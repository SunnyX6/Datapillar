import {
  normalizeApiPayloadError,
  normalizeAxiosError,
  type ErrorContext
} from '@/api/errorCenter'
import type { ApiResponse, ErrorResponse } from '@/api/types/api'

export function normalizeApiPayloadFailure(
  payload: ApiResponse<unknown> | ErrorResponse,
  context: ErrorContext
) {
  return normalizeApiPayloadError(payload, context)
}

export function normalizeAxiosFailure(
  error: unknown,
  context: ErrorContext
) {
  return normalizeAxiosError(error, context)
}

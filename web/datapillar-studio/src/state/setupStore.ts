import { create } from 'zustand'
import { getSetupStatus } from '@/services/setupService'
import type { SetupStatusResponse } from '@/services/types/setup'

export type SetupGuardStatus = 'idle' | 'checking' | 'ready' | 'error'

export interface SetupStore {
  guardStatus: SetupGuardStatus
  schemaReady: boolean | null
  initialized: boolean | null
  currentStep: string | null
  checkedAt: number | null
  error: string | null
  applySetupStatus: (status: SetupStatusResponse) => void
  ensureSetupStatus: () => Promise<SetupStatusResponse | null>
  refreshSetupStatus: () => Promise<SetupStatusResponse | null>
  resetSetupStatus: () => void
}

let setupStatusPromise: Promise<SetupStatusResponse | null> | null = null

function resolveErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }
  return '初始化状态检查失败'
}

function buildReadyState(status: SetupStatusResponse) {
  return {
    guardStatus: 'ready' as const,
    schemaReady: status.schemaReady,
    initialized: status.initialized,
    currentStep: status.currentStep,
    checkedAt: Date.now(),
    error: null
  }
}

export const useSetupStore = create<SetupStore>((set, get) => ({
  guardStatus: 'idle',
  schemaReady: null,
  initialized: null,
  currentStep: null,
  checkedAt: null,
  error: null,

  applySetupStatus: (status: SetupStatusResponse): void => {
    set(buildReadyState(status))
  },

  ensureSetupStatus: async (): Promise<SetupStatusResponse | null> => {
    if (setupStatusPromise) {
      return setupStatusPromise
    }
    return get().refreshSetupStatus()
  },

  refreshSetupStatus: async (): Promise<SetupStatusResponse | null> => {
    if (setupStatusPromise) {
      return setupStatusPromise
    }

    set({
      guardStatus: 'checking',
      error: null,
    })

    setupStatusPromise = (async () => {
      try {
        const status = await getSetupStatus()
        set(buildReadyState(status))
        return status
      } catch (error) {
        set({
          guardStatus: 'error',
          error: resolveErrorMessage(error),
        })
        return null
      } finally {
        setupStatusPromise = null
      }
    })()

    return setupStatusPromise
  },

  resetSetupStatus: (): void => {
    setupStatusPromise = null
    set({
      guardStatus: 'idle',
      schemaReady: null,
      initialized: null,
      currentStep: null,
      checkedAt: null,
      error: null,
    })
  },
}))

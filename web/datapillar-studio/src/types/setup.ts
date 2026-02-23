export type SetupStepStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'

export interface SetupStep {
  code: string
  name: string
  description: string
  status: SetupStepStatus
}

export interface SetupStatusResponse {
  schemaReady: boolean
  initialized: boolean
  currentStep: string
  steps: SetupStep[]
}

export interface SetupInitializeRequest {
  organizationName: string
  adminName: string
  username: string
  email: string
  password: string
}

export interface SetupInitializeResponse {
  tenantId: number
  userId: number
}

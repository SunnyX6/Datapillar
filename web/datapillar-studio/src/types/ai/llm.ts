export interface LlmPlaygroundModelConfig {
  temperature?: number
  topP?: number
  thinkingEnabled?: boolean
  systemInstruction?: string
}

export interface LlmPlaygroundChatRequest {
  providerCode: string
  modelId: string
  message: string
  modelConfig?: LlmPlaygroundModelConfig
}

export interface LlmPlaygroundDoneEvent {
  content: string
  reasoning?: string
}

export interface LlmPlaygroundStreamCallbacks {
  onDelta: (delta: string) => void
  onReasoningDelta?: (delta: string) => void
  onDone: (event: LlmPlaygroundDoneEvent) => void
  onError: (message: string) => void
}

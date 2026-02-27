import { create } from 'zustand'
import type { ProcessActivity as SseProcessActivity, StreamStatus } from '@/services/aiWorkflowService'
import { emptyWorkflowGraph, type WorkflowGraph } from '@/services/workflowStudioService'

export const DEFAULT_WORKFLOW_AI_MODEL_ID: number | null = null

export type ChatRole = 'user' | 'assistant'

export type ProcessActivity = SseProcessActivity & { id: string; timestamp: number }
export type AgentActivity = ProcessActivity

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  timestamp: number
  processRows?: ProcessActivity[]
  agentRows?: ProcessActivity[]
  streamStatus?: StreamStatus
  interrupt?: {
    options: string[]
    interrupt_id?: string
  }
  recommendations?: string[]
}

interface WorkflowStudioState {
  messages: ChatMessage[]
  isGenerating: boolean
  isWaitingForResume: boolean
  isInitialized: boolean
  selectedAiModelId: number | null
  defaultAiModelId: number | null
  workflow: WorkflowGraph
  lastPrompt: string
  addMessage: (message: ChatMessage) => void
  updateMessage: (id: string, updater: Partial<ChatMessage> | ((msg: ChatMessage) => ChatMessage)) => void
  hydrateFromCache: (payload: {
    messages: ChatMessage[]
    workflow: WorkflowGraph
    lastPrompt: string
    isInitialized: boolean
    isWaitingForResume?: boolean
    selectedAiModelId?: number | null
    defaultAiModelId?: number | null
  }) => void
  setGenerating: (value: boolean) => void
  setWaitingForResume: (value: boolean) => void
  setInitialized: (value: boolean) => void
  setSelectedAiModelId: (value: number | null) => void
  setDefaultAiModelId: (value: number | null) => void
  setWorkflow: (workflow: WorkflowGraph) => void
  setLastPrompt: (prompt: string) => void
  reset: () => void
}

export const useWorkflowStudioStore = create<WorkflowStudioState>((set) => ({
  messages: [],
  isGenerating: false,
  isWaitingForResume: false,
  isInitialized: false,
  selectedAiModelId: DEFAULT_WORKFLOW_AI_MODEL_ID,
  defaultAiModelId: DEFAULT_WORKFLOW_AI_MODEL_ID,
  workflow: emptyWorkflowGraph,
  lastPrompt: '',
  addMessage: (message) =>
    set((state) => ({
      messages: [...state.messages, message]
    })),
  updateMessage: (id, updater) =>
    set((state) => ({
      messages: state.messages.map((msg) =>
        msg.id === id ? (typeof updater === 'function' ? updater(msg) : { ...msg, ...updater }) : msg
      )
    })),
  hydrateFromCache: (payload) =>
    set(() => {
      const resolvedDefaultAiModelId = payload.defaultAiModelId ?? DEFAULT_WORKFLOW_AI_MODEL_ID
      return {
        messages: payload.messages,
        workflow: payload.workflow,
        lastPrompt: payload.lastPrompt,
        isInitialized: payload.isInitialized,
        isWaitingForResume: payload.isWaitingForResume ?? false,
        isGenerating: false,
        selectedAiModelId: payload.selectedAiModelId ?? resolvedDefaultAiModelId,
        defaultAiModelId: resolvedDefaultAiModelId
      }
    }),
  setGenerating: (value) => set({ isGenerating: value }),
  setWaitingForResume: (value) => set({ isWaitingForResume: value }),
  setInitialized: (value) => set({ isInitialized: value }),
  setSelectedAiModelId: (value) => set({ selectedAiModelId: value }),
  setDefaultAiModelId: (value) => set({ defaultAiModelId: value }),
  setWorkflow: (workflow) => set({ workflow }),
  setLastPrompt: (prompt) => set({ lastPrompt: prompt }),
  reset: () =>
    set((state) => ({
      messages: [],
      isGenerating: false,
      isWaitingForResume: false,
      isInitialized: false,
      workflow: emptyWorkflowGraph,
      lastPrompt: '',
      selectedAiModelId: state.defaultAiModelId,
      defaultAiModelId: state.defaultAiModelId
    }))
}))

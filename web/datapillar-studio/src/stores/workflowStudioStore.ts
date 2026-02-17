import { create } from 'zustand'
import type { ProcessActivity as SseProcessActivity, StreamStatus } from '@/services/aiWorkflowService'
import { emptyWorkflowGraph, type WorkflowGraph } from '@/services/workflowStudioService'
import { DEFAULT_WORKFLOW_MODEL_ID } from '@/config/workflowModels'

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
  selectedModelId: string
  defaultModelId: string
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
    selectedModelId?: string
    defaultModelId?: string
  }) => void
  setGenerating: (value: boolean) => void
  setWaitingForResume: (value: boolean) => void
  setInitialized: (value: boolean) => void
  setSelectedModelId: (value: string) => void
  setDefaultModelId: (value: string) => void
  setWorkflow: (workflow: WorkflowGraph) => void
  setLastPrompt: (prompt: string) => void
  reset: () => void
}

export const useWorkflowStudioStore = create<WorkflowStudioState>((set) => ({
  messages: [],
  isGenerating: false,
  isWaitingForResume: false,
  isInitialized: false,
  selectedModelId: DEFAULT_WORKFLOW_MODEL_ID,
  defaultModelId: DEFAULT_WORKFLOW_MODEL_ID,
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
      const resolvedDefaultModelId = payload.defaultModelId ?? DEFAULT_WORKFLOW_MODEL_ID
      return {
        messages: payload.messages,
        workflow: payload.workflow,
        lastPrompt: payload.lastPrompt,
        isInitialized: payload.isInitialized,
        isWaitingForResume: payload.isWaitingForResume ?? false,
        isGenerating: false,
        selectedModelId: payload.selectedModelId ?? resolvedDefaultModelId,
        defaultModelId: resolvedDefaultModelId
      }
    }),
  setGenerating: (value) => set({ isGenerating: value }),
  setWaitingForResume: (value) => set({ isWaitingForResume: value }),
  setInitialized: (value) => set({ isInitialized: value }),
  setSelectedModelId: (value) => set({ selectedModelId: value }),
  setDefaultModelId: (value) => set({ defaultModelId: value }),
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
      selectedModelId: state.defaultModelId,
      defaultModelId: state.defaultModelId
    }))
}))

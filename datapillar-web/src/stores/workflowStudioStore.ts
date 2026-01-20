import { create } from 'zustand'
import type { ProcessActivity as SseProcessActivity, UiPayload } from '@/services/aiWorkflowService'
import { emptyWorkflowGraph, type WorkflowGraph } from '@/services/workflowStudioService'

export type ChatRole = 'user' | 'assistant'

export type ProcessActivity = SseProcessActivity & { timestamp: number }
export type AgentActivity = ProcessActivity

export interface ChatMessageOption {
  type: string
  name: string
  path: string
  description?: string
  tools?: string[]
  extra?: Record<string, unknown>
  // 兼容旧格式
  value?: string
  label?: string
}

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  timestamp: number
  processRows?: ProcessActivity[]
  agentRows?: ProcessActivity[]
  isStreaming?: boolean
  options?: ChatMessageOption[]
  uiPayload?: UiPayload
}

interface WorkflowStudioState {
  messages: ChatMessage[]
  isGenerating: boolean
  isInitialized: boolean
  workflow: WorkflowGraph
  lastPrompt: string
  addMessage: (message: ChatMessage) => void
  updateMessage: (id: string, updater: Partial<ChatMessage> | ((msg: ChatMessage) => ChatMessage)) => void
  setGenerating: (value: boolean) => void
  setInitialized: (value: boolean) => void
  setWorkflow: (workflow: WorkflowGraph) => void
  setLastPrompt: (prompt: string) => void
  reset: () => void
}

export const useWorkflowStudioStore = create<WorkflowStudioState>((set) => ({
  messages: [],
  isGenerating: false,
  isInitialized: false,
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
  setGenerating: (value) => set({ isGenerating: value }),
  setInitialized: (value) => set({ isInitialized: value }),
  setWorkflow: (workflow) => set({ workflow }),
  setLastPrompt: (prompt) => set({ lastPrompt: prompt }),
  reset: () =>
    set({
      messages: [],
      isGenerating: false,
      isInitialized: false,
      workflow: emptyWorkflowGraph,
      lastPrompt: ''
    })
}))

import { create } from 'zustand'
import { emptyWorkflowGraph, type WorkflowGraph } from '@/services/workflowStudioService'

export type ChatRole = 'user' | 'assistant'

export interface AgentActivity {
  id: string
  type: 'thought' | 'tool' | 'result' | 'error'
  state: 'thinking' | 'invoking' | 'waiting' | 'done' | 'error'
  level: 'info' | 'success' | 'warning' | 'error'
  agent: string
  message: string
  timestamp: number
}

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  timestamp: number
  agentRows?: AgentActivity[]
  isStreaming?: boolean
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

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
  workflow: WorkflowGraph
  lastPrompt: string
  addMessage: (message: ChatMessage) => void
  updateMessage: (id: string, updater: Partial<ChatMessage> | ((msg: ChatMessage) => ChatMessage)) => void
  setGenerating: (value: boolean) => void
  setWorkflow: (workflow: WorkflowGraph) => void
  setLastPrompt: (prompt: string) => void
  reset: () => void
}

const initialMessages: ChatMessage[] = [
  {
    id: 'assistant-welcome',
    role: 'assistant',
    content: '你好，我是 Workflow Architect。告诉我你的业务目标，比如“将订单和日志流清洗后写入 Delta Lake”。',
    timestamp: Date.now()
  }
]

export const useWorkflowStudioStore = create<WorkflowStudioState>((set) => ({
  messages: [...initialMessages],
  isGenerating: false,
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
  setWorkflow: (workflow) => set({ workflow }),
  setLastPrompt: (prompt) => set({ lastPrompt: prompt }),
  reset: () =>
    set({
      messages: [...initialMessages],
      isGenerating: false,
      workflow: emptyWorkflowGraph,
      lastPrompt: ''
    })
}))

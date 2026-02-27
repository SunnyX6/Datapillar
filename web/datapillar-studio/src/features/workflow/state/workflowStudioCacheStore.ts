/**
 * Workflow Studio 缓存状态（本地持久化）
 *
 * 目标：
 * 1. 刷新页面不丢消息与画布
 * 2. 恢复聊天滚动位置与高度缓存
 */

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type { StateSnapshot } from 'react-virtuoso'
import { emptyWorkflowGraph, type WorkflowGraph } from '@/services/workflowStudioService'
import { DEFAULT_WORKFLOW_AI_MODEL_ID, type ChatMessage } from './workflowStudioStore'

type WorkflowStudioCacheSnapshot = {
  messages: ChatMessage[]
  workflow: WorkflowGraph
  lastPrompt: string
  isInitialized: boolean
  virtuosoState: StateSnapshot | null
  selectedAiModelId: number | null
  defaultAiModelId: number | null
}

interface WorkflowStudioCacheState extends WorkflowStudioCacheSnapshot {
  setSnapshot: (snapshot: WorkflowStudioCacheSnapshot) => void
  reset: () => void
}

const DEFAULT_SNAPSHOT: WorkflowStudioCacheSnapshot = {
  messages: [],
  workflow: emptyWorkflowGraph,
  lastPrompt: '',
  isInitialized: false,
  virtuosoState: null,
  selectedAiModelId: DEFAULT_WORKFLOW_AI_MODEL_ID,
  defaultAiModelId: DEFAULT_WORKFLOW_AI_MODEL_ID
}

export const useWorkflowStudioCacheStore = create<WorkflowStudioCacheState>()(
  persist(
    (set) => ({
      ...DEFAULT_SNAPSHOT,
      setSnapshot: (snapshot) => set(snapshot),
      reset: () => set(DEFAULT_SNAPSHOT)
    }),
    {
      name: 'workflow-studio-cache',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        messages: state.messages,
        workflow: state.workflow,
        lastPrompt: state.lastPrompt,
        isInitialized: state.isInitialized,
        virtuosoState: state.virtuosoState,
        selectedAiModelId: state.selectedAiModelId,
        defaultAiModelId: state.defaultAiModelId
      })
    }
  )
)

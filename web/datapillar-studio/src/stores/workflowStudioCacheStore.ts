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
import { DEFAULT_WORKFLOW_MODEL_ID } from '@/config/workflowModels'
import type { ChatMessage } from './workflowStudioStore'

type WorkflowStudioCacheSnapshot = {
  messages: ChatMessage[]
  workflow: WorkflowGraph
  lastPrompt: string
  isInitialized: boolean
  virtuosoState: StateSnapshot | null
  selectedModelId: string
  defaultModelId: string
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
  selectedModelId: DEFAULT_WORKFLOW_MODEL_ID,
  defaultModelId: DEFAULT_WORKFLOW_MODEL_ID
}

const CACHE_VERSION = 4

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
      version: CACHE_VERSION,
      migrate: (persistedState, version) => {
        const legacyState = persistedState as Partial<WorkflowStudioCacheSnapshot>
        if (version < CACHE_VERSION) {
          return {
            ...DEFAULT_SNAPSHOT,
            ...legacyState,
            virtuosoState: null,
            defaultModelId:
              version < 4
                ? legacyState.selectedModelId ?? DEFAULT_WORKFLOW_MODEL_ID
                : legacyState.defaultModelId ?? DEFAULT_WORKFLOW_MODEL_ID
          }
        }
        return persistedState as WorkflowStudioCacheSnapshot
      },
      partialize: (state) => ({
        messages: state.messages,
        workflow: state.workflow,
        lastPrompt: state.lastPrompt,
        isInitialized: state.isInitialized,
        virtuosoState: state.virtuosoState,
        selectedModelId: state.selectedModelId,
        defaultModelId: state.defaultModelId
      })
    }
  )
)

// @vitest-environment jsdom

import { beforeEach, describe, expect, it } from 'vitest'
import { useWorkflowStudioCacheStore } from '@/stores'
import { DEFAULT_WORKFLOW_MODEL_ID } from '@/config/workflowModels'
import { emptyWorkflowGraph } from '@/services/workflowStudioService'

const resetWorkflowCacheStore = () => {
  useWorkflowStudioCacheStore.setState({
    messages: [],
    workflow: emptyWorkflowGraph,
    lastPrompt: '',
    isInitialized: false,
    virtuosoState: null,
    selectedModelId: DEFAULT_WORKFLOW_MODEL_ID,
    defaultModelId: DEFAULT_WORKFLOW_MODEL_ID
  })
}

describe('workflowStudioCacheStore', () => {
  beforeEach(async () => {
    localStorage.clear()
    resetWorkflowCacheStore()
    await useWorkflowStudioCacheStore.persist.rehydrate()
  })

  it('从 v3 迁移时应将 selectedModelId 回填为 defaultModelId', async () => {
    const legacyModelId = 'anthropic/claude-3.5-sonnet'

    localStorage.setItem(
      'workflow-studio-cache',
      JSON.stringify({
        state: {
          messages: [],
          workflow: emptyWorkflowGraph,
          lastPrompt: '',
          isInitialized: false,
          virtuosoState: null,
          selectedModelId: legacyModelId
        },
        version: 3
      })
    )

    await useWorkflowStudioCacheStore.persist.rehydrate()

    const state = useWorkflowStudioCacheStore.getState()
    expect(state.selectedModelId).toBe(legacyModelId)
    expect(state.defaultModelId).toBe(legacyModelId)
  })
})

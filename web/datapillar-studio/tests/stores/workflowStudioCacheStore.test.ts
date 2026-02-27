// @vitest-environment jsdom

import { beforeEach, describe, expect, it } from 'vitest'
import { DEFAULT_WORKFLOW_AI_MODEL_ID, useWorkflowStudioCacheStore } from '@/features/workflow/state'
import { emptyWorkflowGraph } from '@/services/workflowStudioService'

const resetWorkflowCacheStore = () => {
  useWorkflowStudioCacheStore.setState({
    messages: [],
    workflow: emptyWorkflowGraph,
    lastPrompt: '',
    isInitialized: false,
    virtuosoState: null,
    selectedAiModelId: DEFAULT_WORKFLOW_AI_MODEL_ID,
    defaultAiModelId: DEFAULT_WORKFLOW_AI_MODEL_ID
  })
}

describe('workflowStudioCacheStore', () => {
  beforeEach(async () => {
    localStorage.clear()
    resetWorkflowCacheStore()
    await useWorkflowStudioCacheStore.persist.rehydrate()
  })

  it('旧版本缓存不做迁移，直接回退默认模型', async () => {
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
    expect(state.selectedAiModelId).toBe(DEFAULT_WORKFLOW_AI_MODEL_ID)
    expect(state.defaultAiModelId).toBe(DEFAULT_WORKFLOW_AI_MODEL_ID)
  })
})

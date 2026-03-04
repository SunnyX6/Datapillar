import { afterEach, describe, expect, it } from 'vitest'
import { DEFAULT_WORKFLOW_AI_MODEL_ID, useWorkflowStudioStore } from '@/features/workflow/state'
import { emptyWorkflowGraph } from '@/services/workflowStudioService'

const resetStore = () => {
  useWorkflowStudioStore.getState().setDefaultAiModelId(DEFAULT_WORKFLOW_AI_MODEL_ID)
  useWorkflowStudioStore.getState().reset()
}

afterEach(() => {
  resetStore()
})

describe('workflowStudioStore Model selection', () => {
  it('Use preset model by default', () => {
    const state = useWorkflowStudioStore.getState()
    expect(state.selectedAiModelId).toBe(DEFAULT_WORKFLOW_AI_MODEL_ID)
  })

  it('hydrateFromCache Selected models can be restored', () => {
    const nextAiModelId = 2001
    useWorkflowStudioStore.getState().hydrateFromCache({
      messages: [],
      workflow: emptyWorkflowGraph,
      lastPrompt: '',
      isInitialized: false,
      selectedAiModelId: nextAiModelId
    })
    expect(useWorkflowStudioStore.getState().selectedAiModelId).toBe(nextAiModelId)
  })
})

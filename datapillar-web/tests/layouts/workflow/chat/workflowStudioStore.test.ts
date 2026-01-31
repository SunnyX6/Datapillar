import { afterEach, describe, expect, it } from 'vitest'
import { useWorkflowStudioStore } from '@/stores'
import { DEFAULT_WORKFLOW_MODEL_ID } from '@/config/workflowModels'
import { emptyWorkflowGraph } from '@/services/workflowStudioService'

const resetStore = () => {
  useWorkflowStudioStore.setState(
    {
      messages: [],
      isGenerating: false,
      isWaitingForResume: false,
      isInitialized: false,
      selectedModelId: DEFAULT_WORKFLOW_MODEL_ID,
      workflow: emptyWorkflowGraph,
      lastPrompt: ''
    },
    true
  )
}

afterEach(() => {
  resetStore()
})

describe('workflowStudioStore 模型选择', () => {
  it('默认使用预设模型', () => {
    const state = useWorkflowStudioStore.getState()
    expect(state.selectedModelId).toBe(DEFAULT_WORKFLOW_MODEL_ID)
  })

  it('hydrateFromCache 可恢复已选模型', () => {
    const nextModelId = 'deepseek/deepseek-chat-v3'
    useWorkflowStudioStore.getState().hydrateFromCache({
      messages: [],
      workflow: emptyWorkflowGraph,
      lastPrompt: '',
      isInitialized: false,
      selectedModelId: nextModelId
    })
    expect(useWorkflowStudioStore.getState().selectedModelId).toBe(nextModelId)
  })
})

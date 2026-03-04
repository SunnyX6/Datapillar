import { beforeEach, describe, expect, it } from 'vitest'
import { DEFAULT_WORKFLOW_AI_MODEL_ID, useWorkflowStudioStore } from '@/features/workflow/state'
import { emptyWorkflowGraph } from '@/services/workflowStudioService'

const CLAUDE_AI_MODEL_ID = 3001
const DEEPSEEK_AI_MODEL_ID = 3002

const resetWorkflowStore = () => {
  useWorkflowStudioStore.setState({
    messages: [],
    isGenerating: false,
    isWaitingForResume: false,
    isInitialized: false,
    selectedAiModelId: DEFAULT_WORKFLOW_AI_MODEL_ID,
    defaultAiModelId: DEFAULT_WORKFLOW_AI_MODEL_ID,
    workflow: emptyWorkflowGraph,
    lastPrompt: ''
  })
}

describe('workflowStudioStore', () => {
  beforeEach(() => {
    resetWorkflowStore()
  })

  it('hydrateFromCache Only the default model，The current model should fall back to the default model', () => {
    useWorkflowStudioStore.getState().hydrateFromCache({
      messages: [],
      workflow: emptyWorkflowGraph,
      lastPrompt: '',
      isInitialized: false,
      defaultAiModelId: CLAUDE_AI_MODEL_ID
    })

    const state = useWorkflowStudioStore.getState()
    expect(state.defaultAiModelId).toBe(CLAUDE_AI_MODEL_ID)
    expect(state.selectedAiModelId).toBe(CLAUDE_AI_MODEL_ID)
  })

  it('reset The current default model should be retained after，and reset the selected model to the default model', () => {
    useWorkflowStudioStore.getState().setDefaultAiModelId(DEEPSEEK_AI_MODEL_ID)
    useWorkflowStudioStore.getState().setSelectedAiModelId(CLAUDE_AI_MODEL_ID)

    useWorkflowStudioStore.getState().reset()

    const state = useWorkflowStudioStore.getState()
    expect(state.defaultAiModelId).toBe(DEEPSEEK_AI_MODEL_ID)
    expect(state.selectedAiModelId).toBe(DEEPSEEK_AI_MODEL_ID)
  })
})

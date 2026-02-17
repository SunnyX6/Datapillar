import { beforeEach, describe, expect, it } from 'vitest'
import { useWorkflowStudioStore } from '@/stores'
import { DEFAULT_WORKFLOW_MODEL_ID } from '@/config/workflowModels'
import { emptyWorkflowGraph } from '@/services/workflowStudioService'

const CLAUDE_MODEL_ID = 'anthropic/claude-3.5-sonnet'
const DEEPSEEK_MODEL_ID = 'deepseek/deepseek-chat-v3'

const resetWorkflowStore = () => {
  useWorkflowStudioStore.setState({
    messages: [],
    isGenerating: false,
    isWaitingForResume: false,
    isInitialized: false,
    selectedModelId: DEFAULT_WORKFLOW_MODEL_ID,
    defaultModelId: DEFAULT_WORKFLOW_MODEL_ID,
    workflow: emptyWorkflowGraph,
    lastPrompt: ''
  })
}

describe('workflowStudioStore', () => {
  beforeEach(() => {
    resetWorkflowStore()
  })

  it('hydrateFromCache 只有默认模型时，当前模型应回落到默认模型', () => {
    useWorkflowStudioStore.getState().hydrateFromCache({
      messages: [],
      workflow: emptyWorkflowGraph,
      lastPrompt: '',
      isInitialized: false,
      defaultModelId: CLAUDE_MODEL_ID
    })

    const state = useWorkflowStudioStore.getState()
    expect(state.defaultModelId).toBe(CLAUDE_MODEL_ID)
    expect(state.selectedModelId).toBe(CLAUDE_MODEL_ID)
  })

  it('reset 后应保留当前默认模型，并将选中模型重置为默认模型', () => {
    useWorkflowStudioStore.getState().setDefaultModelId(DEEPSEEK_MODEL_ID)
    useWorkflowStudioStore.getState().setSelectedModelId(CLAUDE_MODEL_ID)

    useWorkflowStudioStore.getState().reset()

    const state = useWorkflowStudioStore.getState()
    expect(state.defaultModelId).toBe(DEEPSEEK_MODEL_ID)
    expect(state.selectedModelId).toBe(DEEPSEEK_MODEL_ID)
  })
})

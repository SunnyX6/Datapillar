import { describe, expect, it } from 'vitest'
import { MAX_COMPARE_MODELS, collectConnectedModelIds, connectModel, filterModels, toggleModelSelection } from '@/features/llm/utils'
import type { ModelFilters, ModelRecord } from '@/features/llm/utils/types'

describe('LLM Filter tool function', () => {
  it('Filter model list by criteria', () => {
    const models: ModelRecord[] = [
      {
        aiModelId: 1,
        providerModelId: 'openai/gpt-4o',
        name: 'OpenAI: GPT-4o',
        provider: 'openai',
        description: 'flagship',
        tags: ['chat'],
        type: 'chat',
        contextGroup: '128K',
        stats: { context: '128K context', inputPrice: '$5', outputPrice: '$15' }
      },
      {
        aiModelId: 2,
        providerModelId: 'openai/text-embedding-3-large',
        name: 'OpenAI: Text Embedding 3 Large',
        provider: 'openai',
        description: 'embedding',
        tags: ['embeddings'],
        type: 'embeddings',
        contextGroup: '8K',
        stats: { context: '8K context', inputPrice: '$0.1', outputPrice: '-' }
      },
      {
        aiModelId: 3,
        providerModelId: 'deepseek/deepseek-chat-v3',
        name: 'DeepSeek V3',
        provider: 'deepseek',
        description: 'chat model',
        tags: ['chat'],
        type: 'chat',
        contextGroup: '64K',
        stats: { context: '64K context', inputPrice: '$0.1', outputPrice: '$0.2' }
      }
    ]

    const providerFilters: ModelFilters = { providers: ['openai'], types: [], contexts: [] }
    expect(filterModels(models, '', providerFilters)).toHaveLength(2)

    const typeFilters: ModelFilters = { providers: ['openai'], types: ['embeddings'], contexts: [] }
    expect(filterModels(models, '', typeFilters)).toHaveLength(1)

    const contextFilters: ModelFilters = { providers: [], types: [], contexts: ['64K'] }
    expect(filterModels(models, '', contextFilters)[0].provider).toBe('deepseek')

    const queryFilters: ModelFilters = { providers: [], types: [], contexts: [] }
    expect(filterModels(models, 'embedding', queryFilters)[0].type).toBe('embeddings')
  })

  it('Avoid duplicate writes when connecting models', () => {
    const connected = [1]
    expect(connectModel(connected, 1)).toEqual([1])
    expect(connectModel(connected, 3)).toEqual([
      1,
      3
    ])
  })

  it('Extract connected model ID Ignore unconnected models when', () => {
    const connectedIds = collectConnectedModelIds([
      { aiModelId: 11, hasApiKey: true },
      { aiModelId: 12, hasApiKey: false },
      { aiModelId: 13, hasApiKey: undefined }
    ])
    expect(connectedIds).toEqual([11])
  })

  it('Limit the number of comparison selections and allow cancellation', () => {
    const initial = [1, 3, 4]
    expect(initial).toHaveLength(MAX_COMPARE_MODELS)

    const blocked = toggleModelSelection(initial, 2)
    expect(blocked).toEqual(initial)

    const removed = toggleModelSelection(initial, 3)
    expect(removed).toEqual([1, 4])

    const added = toggleModelSelection(removed, 2)
    expect(added).toEqual([1, 4, 2])
  })
})

import { describe, expect, it } from 'vitest'
import { MAX_COMPARE_MODELS, collectConnectedModelIds, connectModel, filterModels, toggleModelSelection } from '@/layouts/llm/utils'
import type { ModelFilters, ModelRecord } from '@/layouts/llm/types'

describe('LLM 筛选工具函数', () => {
  it('按条件筛选模型列表', () => {
    const models: ModelRecord[] = [
      {
        id: 'openai/gpt-4o',
        name: 'OpenAI: GPT-4o',
        provider: 'openai',
        description: 'flagship',
        tags: ['chat'],
        type: 'chat',
        contextGroup: '128K',
        stats: { context: '128K context', inputPrice: '$5', outputPrice: '$15' }
      },
      {
        id: 'openai/text-embedding-3-large',
        name: 'OpenAI: Text Embedding 3 Large',
        provider: 'openai',
        description: 'embedding',
        tags: ['embeddings'],
        type: 'embeddings',
        contextGroup: '8K',
        stats: { context: '8K context', inputPrice: '$0.1', outputPrice: '-' }
      },
      {
        id: 'deepseek/deepseek-chat-v3',
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

  it('连接模型时避免重复写入', () => {
    const connected = ['openai/gpt-4o']
    expect(connectModel(connected, 'openai/gpt-4o')).toEqual(['openai/gpt-4o'])
    expect(connectModel(connected, 'deepseek/deepseek-chat-v3')).toEqual([
      'openai/gpt-4o',
      'deepseek/deepseek-chat-v3'
    ])
  })

  it('提取已连接模型 ID 时忽略未连接模型', () => {
    const connectedIds = collectConnectedModelIds([
      { id: 'model-a', hasApiKey: true },
      { id: 'model-b', hasApiKey: false },
      { id: 'model-c', hasApiKey: undefined }
    ])
    expect(connectedIds).toEqual(['model-a'])
  })

  it('限制对比选择数量并允许取消', () => {
    const initial = ['openai/gpt-4o', 'deepseek/deepseek-chat-v3', 'meta-llama/llama-3-70b-instruct']
    expect(initial).toHaveLength(MAX_COMPARE_MODELS)

    const blocked = toggleModelSelection(initial, 'openai/text-embedding-3-large')
    expect(blocked).toEqual(initial)

    const removed = toggleModelSelection(initial, 'deepseek/deepseek-chat-v3')
    expect(removed).toEqual(['openai/gpt-4o', 'meta-llama/llama-3-70b-instruct'])

    const added = toggleModelSelection(removed, 'openai/text-embedding-3-large')
    expect(added).toEqual(['openai/gpt-4o', 'meta-llama/llama-3-70b-instruct', 'openai/text-embedding-3-large'])
  })
})

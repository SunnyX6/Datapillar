import { describe, expect, it } from 'vitest'
import { mapAdminModelToRecord, resolveProviderLabel } from '@/layouts/llm/modelAdapters'
import type { StudioLlmModel } from '@/services/studioLlmService'

describe('LLM 模型适配器', () => {
  it('将后端模型响应映射为前端模型结构', () => {
    const input: StudioLlmModel = {
      id: 101,
      modelId: 'openai/text-embedding-3-large',
      name: 'Text Embedding 3 Large',
      providerId: 1,
      providerCode: 'OpenAI',
      providerName: 'OpenAI',
      modelType: 'EMBEDDING',
      description: 'embedding model',
      tags: ['embeddings'],
      contextTokens: 8192,
      inputPriceUsd: '0.13',
      outputPriceUsd: '0',
      embeddingDimension: 3072,
      baseUrl: ' https://api.openai.com/v1 ',
      maskedApiKey: ' sk-1*****7890 ',
      status: 'active',
      hasApiKey: true,
      createdBy: 1,
      updatedBy: 1,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00'
    }

    const record = mapAdminModelToRecord(input)

    expect(record.id).toBe('openai/text-embedding-3-large')
    expect(record.provider).toBe('openai')
    expect(record.type).toBe('embeddings')
    expect(record.contextGroup).toBe('8192')
    expect(record.stats.inputPrice).toBe('$0.13/M')
    expect(record.stats.params).toBe('3072 dims')
    expect(record.baseUrl).toBe('https://api.openai.com/v1')
    expect(record.maskedApiKey).toBe('sk-1*****7890')
    expect(record.hasApiKey).toBe(true)
  })

  it('在缺失 providerName 时按 provider code 生成展示文案', () => {
    expect(resolveProviderLabel('deepseek')).toBe('DeepSeek')
    expect(resolveProviderLabel('my_provider')).toBe('My Provider')
  })

  it('未知模型类型不做降级映射并直接抛错', () => {
    const input: StudioLlmModel = {
      id: 102,
      modelId: 'custom/unknown-model',
      name: 'Unknown Model',
      providerId: 1,
      providerCode: 'custom',
      providerName: 'Custom',
      modelType: 'UNKNOWN_TYPE',
      description: null,
      tags: [],
      contextTokens: 4096,
      inputPriceUsd: null,
      outputPriceUsd: null,
      embeddingDimension: null,
      baseUrl: null,
      status: 'active',
      hasApiKey: false,
      createdBy: 1,
      updatedBy: 1,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00'
    }

    expect(() => mapAdminModelToRecord(input)).toThrowError('Unsupported model type')
  })
})

export type WorkflowModelOption = {
  id: string
  label: string
  provider: string
  providerLabel: string
  tone: 'emerald' | 'violet' | 'blue'
  badge: string
}

export const WORKFLOW_MODEL_OPTIONS: WorkflowModelOption[] = [
  {
    id: 'openai/gpt-4o',
    label: 'GPT-4o',
    provider: 'openai',
    providerLabel: 'OpenAI',
    tone: 'emerald',
    badge: 'O'
  },
  {
    id: 'anthropic/claude-3.5-sonnet',
    label: 'Claude 3.5 Sonnet',
    provider: 'anthropic',
    providerLabel: 'Anthropic',
    tone: 'violet',
    badge: 'A'
  },
  {
    id: 'deepseek/deepseek-chat-v3',
    label: 'DeepSeek V3',
    provider: 'deepseek',
    providerLabel: 'DeepSeek',
    tone: 'blue',
    badge: 'D'
  }
]

export const DEFAULT_WORKFLOW_MODEL_ID = WORKFLOW_MODEL_OPTIONS[0]?.id ?? ''

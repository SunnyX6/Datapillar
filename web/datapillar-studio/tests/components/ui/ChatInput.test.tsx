// @vitest-environment jsdom

import { describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { ChatInput, type ChatModelOption } from '@/components/ui/ChatInput'

const MODEL_OPTIONS: ChatModelOption[] = [
  { id: 'openai/gpt-4o', label: 'GPT-4o', badge: 'O', tone: 'emerald', providerLabel: 'OpenAI' },
  {
    id: 'anthropic/claude-3.5-sonnet',
    label: 'Claude 3.5 Sonnet',
    badge: 'A',
    tone: 'violet',
    providerLabel: 'Anthropic'
  },
  {
    id: 'deepseek/deepseek-chat-v3',
    label: 'DeepSeek V3',
    badge: 'D',
    tone: 'blue',
    providerLabel: 'DeepSeek'
  }
]

const render = (ui: JSX.Element) => {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(ui)
  })
  return { container, root }
}

const unmount = (root: ReturnType<typeof createRoot>, container: HTMLDivElement) => {
  act(() => {
    root.unmount()
  })
  container.remove()
}

describe('ChatInput', () => {
  it('模型下拉卡片支持将模型设为默认', () => {
    const onDefaultModelChange = vi.fn()
    const { container, root } = render(
      <ChatInput
        input=""
        onInputChange={vi.fn()}
        onSend={vi.fn()}
        onAbort={vi.fn()}
        canSend={false}
        isGenerating={false}
        isWaitingForResume={false}
        onCompositionStart={vi.fn()}
        onCompositionEnd={vi.fn()}
        onKeyDown={vi.fn()}
        selectedModelId="openai/gpt-4o"
        defaultModelId="openai/gpt-4o"
        modelOptions={MODEL_OPTIONS}
        onModelChange={vi.fn()}
        onDefaultModelChange={onDefaultModelChange}
        commandOptions={[]}
        onCommand={vi.fn()}
      />
    )

    const modelTrigger = container.querySelector('button[title="选择模型"]')
    expect(modelTrigger).toBeTruthy()

    act(() => {
      modelTrigger?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const starButtons = Array.from(document.body.querySelectorAll('[aria-label="设为默认模型"]'))
    expect(starButtons.length).toBe(2)

    act(() => {
      starButtons[0]?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onDefaultModelChange).toHaveBeenCalledTimes(1)
    expect(onDefaultModelChange).toHaveBeenCalledWith('anthropic/claude-3.5-sonnet')

    unmount(root, container)
  })
})

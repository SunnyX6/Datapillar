// @vitest-environment jsdom

import { describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { ChatInput, type ChatModelOption } from '@/components/ui/ChatInput'

const MODEL_OPTIONS: ChatModelOption[] = [
  { aiModelId: 101, providerModelId: 'openai/gpt-4o', label: 'GPT-4o', badge: 'O', tone: 'emerald', providerLabel: 'OpenAI' },
  {
    aiModelId: 102,
    providerModelId: 'anthropic/claude-3.5-sonnet',
    label: 'Claude 3.5 Sonnet',
    badge: 'A',
    tone: 'violet',
    providerLabel: 'Anthropic'
  },
  {
    aiModelId: 103,
    providerModelId: 'deepseek/deepseek-chat-v3',
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

const createRect = ({
  top = 0,
  left = 0,
  width = 0,
  height = 0
}: {
  top?: number
  left?: number
  width?: number
  height?: number
}): DOMRect => ({
  x: left,
  y: top,
  top,
  left,
  width,
  height,
  right: left + width,
  bottom: top + height,
  toJSON: () => ({})
} as DOMRect)

describe('ChatInput', () => {
  it('The model drop-down card supports setting the model as default', () => {
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
        selectedModelId={101}
        defaultModelId={101}
        modelOptions={MODEL_OPTIONS}
        onModelChange={vi.fn()}
        onDefaultModelChange={onDefaultModelChange}
        commandOptions={[]}
        onCommand={vi.fn()}
      />
    )

    const modelTrigger = container.querySelector('button[title="Select model"]')
    expect(modelTrigger).toBeTruthy()

    act(() => {
      modelTrigger?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const starButtons = Array.from(document.body.querySelectorAll('[aria-label="Set as default model"]'))
    expect(starButtons.length).toBe(2)

    act(() => {
      starButtons[0]?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onDefaultModelChange).toHaveBeenCalledTimes(1)
    expect(onDefaultModelChange).toHaveBeenCalledWith(102)

    unmount(root, container)
  })

  it('The model drop-down card calculates its upward expansion position based on the real height.', () => {
    const modelOptions: ChatModelOption[] = [{ aiModelId: 88, providerModelId: 'glm-test', label: 'glm-test', badge: 'G', tone: 'blue', providerLabel: 'GLM' }]
    const innerHeightDescriptor = Object.getOwnPropertyDescriptor(window, 'innerHeight')
    Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: 760 })

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
        selectedModelId={88}
        modelOptions={modelOptions}
        onModelChange={vi.fn()}
        commandOptions={[]}
        onCommand={vi.fn()}
      />
    )

    const modelTrigger = container.querySelector('button[title="Select model"]') as HTMLButtonElement | null
    expect(modelTrigger).toBeTruthy()

    const triggerRectSpy = vi
      .spyOn(modelTrigger as HTMLButtonElement, 'getBoundingClientRect')
      .mockReturnValue(createRect({ top: 700, left: 100, width: 160, height: 40 }))

    act(() => {
      modelTrigger?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const listbox = document.body.querySelector('div[role="listbox"]')
    const dropdown = listbox?.parentElement as HTMLDivElement | null
    expect(dropdown).toBeTruthy()
    expect(dropdown?.style.getPropertyValue('--dropdown-top')).toBe('594px')

    triggerRectSpy.mockRestore()
    if (innerHeightDescriptor) {
      Object.defineProperty(window, 'innerHeight', innerHeightDescriptor)
    } else {
      Object.defineProperty(window, 'innerHeight', { configurable: true, writable: true, value: 768 })
    }
    unmount(root, container)
  })

  it('Display clear prompt copy when there is no model permission', () => {
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
        selectedModelId={null}
        modelOptions={[]}
        onModelChange={vi.fn()}
        commandOptions={[]}
        onCommand={vi.fn()}
      />
    )

    const modelTrigger = container.querySelector('button[title="Please contact the administrator for authorizationLLMmodel"]') as HTMLButtonElement | null
    expect(modelTrigger).toBeTruthy()
    expect(modelTrigger?.disabled).toBe(true)
    expect(modelTrigger?.textContent).toContain('Please contact the administrator for authorizationLLMmodel')

    act(() => {
      modelTrigger?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(document.body.querySelector('div[role="listbox"]')).toBeNull()

    unmount(root, container)
  })
})

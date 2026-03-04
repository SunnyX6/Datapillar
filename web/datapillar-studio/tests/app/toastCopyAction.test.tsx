// @vitest-environment jsdom
import type { ReactElement } from 'react'
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { successMock, infoMock, warningMock, errorMock, loadingMock, messageMock } = vi.hoisted(() => ({
  successMock: vi.fn(() => 'success-id'),
  infoMock: vi.fn(() => 'info-id'),
  warningMock: vi.fn(() => 'warning-id'),
  errorMock: vi.fn(() => 'error-id'),
  loadingMock: vi.fn(() => 'loading-id'),
  messageMock: vi.fn(() => 'message-id')
}))

vi.mock('sonner', () => {
  const toastFn = Object.assign(vi.fn(() => 'toast-id'), {
    success: successMock,
    info: infoMock,
    warning: warningMock,
    error: errorMock,
    loading: loadingMock,
    message: messageMock
  })
  return { toast: toastFn }
})

import { toast } from 'sonner'
import { installToastCopyAction } from '@/app/toast'

async function flushAsyncQueue() {
  await Promise.resolve()
  await Promise.resolve()
}

function mountAction(action: ReactElement) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(action)
  })
  return { container, root }
}

function unmountAction(root: Root, container: HTMLDivElement) {
  act(() => {
    root.unmount()
  })
  container.remove()
}

describe('installToastCopyAction', () => {
  beforeEach(() => {
    ;(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.clearAllMocks()
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined)
      }
    })
  })

  it('Only the failure prompt injects the copy button and copies the message content', async () => {
    installToastCopyAction()

    toast.error('Character creation failed')

    expect(errorMock).toHaveBeenCalledTimes(1)
    const [, options] = errorMock.mock.calls[0] as [string, {
      style: { position: string; paddingRight: string }
      action: ReactElement
    }]
    expect(options.style.position).toBe('relative')
    expect(options.style.paddingRight).toBe('64px')

    const { container, root } = mountAction(options.action)
    const copyButton = container.querySelector('button')
    expect(copyButton).not.toBeNull()
    await act(async () => {
      copyButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushAsyncQueue()
    })

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('Character creation failed')
    expect(copyButton?.textContent).toContain('Copied')
    expect(copyButton?.style.position).toBe('absolute')
    expect(copyButton?.style.top).toBe('8px')
    expect(copyButton?.style.right).toBe('8px')
    expect(copyButton?.style.background).toBe('transparent')
    unmountAction(root, container)
  })

  it('Non-failure prompts do not inject the copy button', () => {
    installToastCopyAction()

    toast.success('Role created successfully')

    expect(successMock).toHaveBeenCalledTimes(1)
    const [, options] = successMock.mock.calls[0] as [string, undefined]
    expect(options).toBeUndefined()
  })

  it('The caller has provided action remain original action', () => {
    installToastCopyAction()

    const customAction = {
      label: 'Try again',
      onClick: vi.fn()
    }
    toast.error('Network abnormality', { action: customAction })

    expect(errorMock).toHaveBeenCalledTimes(1)
    const [, options] = errorMock.mock.calls[0] as [string, { action: typeof customAction }]
    expect(options.action).toBe(customAction)
  })

  it('Prompt error when copying fails', async () => {
    installToastCopyAction()
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockRejectedValue(new Error('copy failed'))
      }
    })

    toast.error('Copy failure scenario')
    const [, options] = errorMock.mock.calls[0] as [string, {
      action: ReactElement
    }]
    const { container, root } = mountAction(options.action)
    const copyButton = container.querySelector('button')
    expect(copyButton).not.toBeNull()
    await act(async () => {
      copyButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushAsyncQueue()
    })

    expect(copyButton?.textContent).toContain('failed')
    unmountAction(root, container)
  })
})

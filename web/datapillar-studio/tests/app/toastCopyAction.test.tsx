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

  it('仅失败提示注入复制按钮并复制消息内容', async () => {
    installToastCopyAction()

    toast.error('角色创建失败')

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

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('角色创建失败')
    expect(copyButton?.textContent).toContain('已复制')
    expect(copyButton?.style.position).toBe('absolute')
    expect(copyButton?.style.top).toBe('8px')
    expect(copyButton?.style.right).toBe('8px')
    expect(copyButton?.style.background).toBe('transparent')
    unmountAction(root, container)
  })

  it('非失败提示不注入复制按钮', () => {
    installToastCopyAction()

    toast.success('角色创建成功')

    expect(successMock).toHaveBeenCalledTimes(1)
    const [, options] = successMock.mock.calls[0] as [string, undefined]
    expect(options).toBeUndefined()
  })

  it('调用方已提供 action 时保持原始 action', () => {
    installToastCopyAction()

    const customAction = {
      label: '重试',
      onClick: vi.fn()
    }
    toast.error('网络异常', { action: customAction })

    expect(errorMock).toHaveBeenCalledTimes(1)
    const [, options] = errorMock.mock.calls[0] as [string, { action: typeof customAction }]
    expect(options.action).toBe(customAction)
  })

  it('复制失败时提示错误', async () => {
    installToastCopyAction()
    Object.defineProperty(globalThis.navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockRejectedValue(new Error('copy failed'))
      }
    })

    toast.error('复制失败场景')
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

    expect(copyButton?.textContent).toContain('失败')
    unmountAction(root, container)
  })
})

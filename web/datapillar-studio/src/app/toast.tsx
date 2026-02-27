import { isValidElement, type CSSProperties, type ReactNode } from 'react'
import { toast, type ExternalToast } from 'sonner'
import { ToastCopyAction } from './ToastCopyAction'

const TOAST_METHOD_NAMES = ['error'] as const
const PATCH_FLAG = Symbol('datapillar.toast.copy-action.patched')

const COPY_TOAST_CONTAINER_STYLE: CSSProperties = {
  position: 'relative',
  paddingRight: '64px'
}

type ToastMethodName = (typeof TOAST_METHOD_NAMES)[number]
type ToastMethod = (message: ReactNode | (() => ReactNode), data?: ExternalToast) => string | number
type ToastWithPatchFlag = typeof toast & {
  [PATCH_FLAG]?: boolean
}

function resolveNodeText(node: ReactNode | (() => ReactNode) | undefined): string {
  if (node == null || typeof node === 'boolean') {
    return ''
  }
  if (typeof node === 'function') {
    try {
      return resolveNodeText(node())
    } catch {
      return ''
    }
  }
  if (typeof node === 'string' || typeof node === 'number' || typeof node === 'bigint') {
    return String(node)
  }
  if (Array.isArray(node)) {
    return node.map((item) => resolveNodeText(item)).join('')
  }
  if (isValidElement(node)) {
    const props = node.props as { children?: ReactNode }
    return resolveNodeText(props.children)
  }
  return ''
}

function resolveToastCopyText(message: ReactNode | (() => ReactNode), data?: ExternalToast): string {
  const title = resolveNodeText(message).trim()
  const description = resolveNodeText(data?.description).trim()
  if (title && description) {
    return `${title}\n${description}`
  }
  return title || description
}

function withCopyAction(message: ReactNode | (() => ReactNode), data?: ExternalToast): ExternalToast | undefined {
  const text = resolveToastCopyText(message, data)
  if (!text || data?.action) {
    return data
  }

  return {
    ...data,
    style: {
      ...COPY_TOAST_CONTAINER_STYLE,
      ...(data?.style ?? {})
    },
    action: <ToastCopyAction text={text} />
  }
}

function patchToastMethod(methodName: ToastMethodName): void {
  const originalMethod = toast[methodName] as ToastMethod
  ;(toast as typeof toast & Record<ToastMethodName, ToastMethod>)[methodName] = ((message, data) =>
    originalMethod(message, withCopyAction(message, data))) as ToastMethod
}

export function installToastCopyAction(): void {
  const mutableToast = toast as ToastWithPatchFlag
  if (mutableToast[PATCH_FLAG]) {
    return
  }
  TOAST_METHOD_NAMES.forEach((methodName) => patchToastMethod(methodName))
  mutableToast[PATCH_FLAG] = true
}

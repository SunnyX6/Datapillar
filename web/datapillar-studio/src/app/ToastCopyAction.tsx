import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Copy } from 'lucide-react'

const COPY_ACTION_BUTTON_STYLE: CSSProperties = {
  position: 'absolute',
  top: '8px',
  right: '8px',
  minWidth: '24px',
  height: '24px',
  padding: '0 6px',
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  borderRadius: '6px',
  lineHeight: 0,
  zIndex: 1,
  cursor: 'pointer',
  border: 'none',
  background: 'transparent',
  transition: 'all 0.2s ease'
}

const COPY_ACTION_STATUS_STYLE: Record<'idle' | 'copied' | 'failed', CSSProperties> = {
  idle: {
    color: '#334155'
  },
  copied: {
    color: '#166534'
  },
  failed: {
    color: '#b91c1c'
  }
}

async function copyToClipboard(text: string): Promise<boolean> {
  if (!text || typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
    return false
  }
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

export function ToastCopyAction(props: { text: string }) {
  const [status, setStatus] = useState<'idle' | 'copied' | 'failed'>('idle')
  const resetTimerRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (resetTimerRef.current != null) {
        window.clearTimeout(resetTimerRef.current)
      }
    }
  }, [])

  const scheduleReset = () => {
    if (typeof window === 'undefined') {
      return
    }
    if (resetTimerRef.current != null) {
      window.clearTimeout(resetTimerRef.current)
    }
    resetTimerRef.current = window.setTimeout(() => {
      setStatus('idle')
      resetTimerRef.current = null
    }, 1200)
  }

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
    event.stopPropagation()
    void copyToClipboard(props.text).then((copied) => {
      setStatus(copied ? 'copied' : 'failed')
      scheduleReset()
    })
  }

  const label = status === 'copied' ? '已复制' : status === 'failed' ? '失败' : null

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-label={status === 'idle' ? '复制消息' : label ?? '复制消息'}
      title={status === 'idle' ? '复制消息' : label ?? '复制消息'}
      style={{
        ...COPY_ACTION_BUTTON_STYLE,
        ...COPY_ACTION_STATUS_STYLE[status]
      }}
    >
      {status === 'idle' ? (
        <Copy size={14} aria-hidden="true" />
      ) : (
        <span className="text-micro leading-none">{label}</span>
      )}
    </button>
  )
}

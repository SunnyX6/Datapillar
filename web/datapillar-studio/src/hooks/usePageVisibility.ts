import { useEffect, useState } from 'react'

/**
 * 监听页面可见性（标签页切换 / 窗口焦点）并返回当前可见状态
 */
export function usePageVisibility() {
  const [isVisible, setIsVisible] = useState(() => {
    if (typeof document === 'undefined') {
      return true
    }
    return document.visibilityState !== 'hidden'
  })

  useEffect(() => {
    const handleVisibility = () => {
      setIsVisible(document.visibilityState !== 'hidden')
    }

    const handleFocus = () => setIsVisible(true)
    const handleBlur = () => {
      if (document.visibilityState === 'hidden') {
        setIsVisible(false)
      }
    }

    document.addEventListener('visibilitychange', handleVisibility)
    window.addEventListener('focus', handleFocus)
    window.addEventListener('blur', handleBlur)

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility)
      window.removeEventListener('focus', handleFocus)
      window.removeEventListener('blur', handleBlur)
    }
  }, [])

  return isVisible
}

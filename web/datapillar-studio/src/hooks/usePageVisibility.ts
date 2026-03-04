import { useEffect, useState } from 'react'

/**
 * Monitor page visibility（Tab switching / window focus）and returns the currently visible state
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

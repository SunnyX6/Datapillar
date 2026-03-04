/**
 * Theme switching component
 *
 * Function：
 * 1. support light/dark Switch between two modes
 * 2. Click to switch directly，Dont show drop-down menu
 * 3. use lucide-react icon
 * 4. Reference examples design style
 */

import { Sun, Moon } from 'lucide-react'
import { Button } from '@/components/ui'
import { useThemeStore } from '@/state'

/**
 * Theme switching component
 */
export function ThemeToggle() {
  const mode = useThemeStore((state) => state.mode)
  const setMode = useThemeStore((state) => state.setMode)

  // switch theme
  const toggleTheme = () => {
    setMode(mode === 'light' ? 'dark' : 'light')
  }

  return (
    <Button
      onClick={toggleTheme}
      variant="ghost"
      size="iconSm"
      className="relative size-6! overflow-hidden rounded-full bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 active:bg-slate-300 dark:active:bg-white/15 text-slate-500 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-500 transition-colors duration-150 border border-slate-200 dark:border-white/5"
      aria-label={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      title={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
    >
      {mode === 'dark' ? <Sun size={14} /> : <Moon size={14} />}
    </Button>
  )
}

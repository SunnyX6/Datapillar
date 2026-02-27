/**
 * 主题切换组件
 *
 * 功能：
 * 1. 支持 light/dark 两种模式切换
 * 2. 点击直接切换，不显示下拉菜单
 * 3. 使用 lucide-react 的图标
 * 4. 参考 examples 的设计风格
 */

import { Sun, Moon } from 'lucide-react'
import { Button } from '@/components/ui'
import { useThemeStore } from '@/state'

/**
 * 主题切换组件
 */
export function ThemeToggle() {
  const mode = useThemeStore((state) => state.mode)
  const setMode = useThemeStore((state) => state.setMode)

  // 切换主题
  const toggleTheme = () => {
    setMode(mode === 'light' ? 'dark' : 'light')
  }

  return (
    <Button
      onClick={toggleTheme}
      variant="ghost"
      size="iconSm"
      className="relative size-6! overflow-hidden rounded-full bg-slate-100 dark:bg-white/5 hover:bg-slate-200 dark:hover:bg-white/10 active:bg-slate-300 dark:active:bg-white/15 text-slate-500 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-indigo-500 transition-colors duration-150 border border-slate-200 dark:border-white/5"
      aria-label={mode === 'dark' ? '切换到浅色模式' : '切换到深色模式'}
      title={mode === 'dark' ? '切换到浅色模式' : '切换到深色模式'}
    >
      {mode === 'dark' ? <Sun size={14} /> : <Moon size={14} />}
    </Button>
  )
}

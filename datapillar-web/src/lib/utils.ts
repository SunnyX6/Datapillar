import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'
import { TYPOGRAPHY } from '@/design-tokens/typography'

// 设计系统的字体 token（如 `text-body-sm`）与 Tailwind 的 `text-{color}` 都以 `text-` 开头。
// tailwind-merge 在未配置时会把未知的 `text-*` 当成颜色类，导致与 `text-slate-500` 合并时把字体 token 吞掉，
// 最终表现为“你明明改了字体，但页面看起来没变/不生效”。
//
// 稳妥做法：把 `TYPOGRAPHY` 中所有 `text-*` token 注册为 Tailwind v4 的 font-size（theme.text，对应 `--text-*`），
// 这样后续新增 typography token 只要写进 `TYPOGRAPHY`，就不会再被 tailwind-merge 误合并污染。
const TYPOGRAPHY_TEXT_THEME_VALUES = Array.from(
  new Set(
    Object.values(TYPOGRAPHY)
      .filter((className) => className.startsWith('text-'))
      .map((className) => className.slice('text-'.length))
  )
)

const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      text: TYPOGRAPHY_TEXT_THEME_VALUES
    }
  }
})

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * 格式化时间字符串（精确到秒）
 * 自动将 UTC 时间转换为本地时区
 */
export function formatTime(time?: string): string {
  if (!time) return '-'
  try {
    const date = new Date(time)
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    const h = String(date.getHours()).padStart(2, '0')
    const min = String(date.getMinutes()).padStart(2, '0')
    const s = String(date.getSeconds()).padStart(2, '0')
    return `${y}-${m}-${d} ${h}:${min}:${s}`
  } catch {
    return '-'
  }
}

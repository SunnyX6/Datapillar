/**
 * 基于进度的日志打字机 Hook
 *
 * 功能：
 * 根据进度百分比逐行显示日志
 */

import { useMemo } from 'react'

/**
 * 基于进度的日志 Hook
 * @param lines 日志行数组
 * @param progress 进度百分比 0-100
 * @returns 当前显示的日志行数组
 */
export function useProgressLog(lines: string[], progress: number): string[] {
  return useMemo(() => {
    // 计算所有行的总字符数
    const totalChars = lines.join('').length
    const targetChars = Math.floor((totalChars * progress) / 100)

    let charCount = 0
    const displayedLines: string[] = []

    for (const line of lines) {
      if (charCount >= targetChars) {
        break
      }

      const remainingChars = targetChars - charCount
      if (remainingChars >= line.length) {
        // 显示完整行
        displayedLines.push(line)
        charCount += line.length
      } else {
        // 显示部分行
        displayedLines.push(line.substring(0, remainingChars))
        charCount += remainingChars
      }
    }

    return displayedLines
  }, [lines, progress])
}

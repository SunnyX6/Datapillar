/**
 * Progress-based log typewriter Hook
 *
 * Function：
 * Show log line by line based on progress percentage
 */

import { useMemo } from 'react'

/**
 * Progress based logging Hook
 * @param lines Array of log lines
 * @param progress progress percentage 0-100
 * @returns Array of currently displayed log lines
 */
export function useProgressLog(lines: string[], progress: number): string[] {
  return useMemo(() => {
    // Count the total number of characters in all lines
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
        // Show full line
        displayedLines.push(line)
        charCount += line.length
      } else {
        // Show part of the row
        displayedLines.push(line.substring(0, remainingChars))
        charCount += remainingChars
      }
    }

    return displayedLines
  }, [lines, progress])
}

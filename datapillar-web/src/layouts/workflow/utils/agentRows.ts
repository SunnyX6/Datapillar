import type { ProcessActivity } from '@/stores'

const findLastIndex = <T,>(list: T[], predicate: (value: T) => boolean) => {
  for (let index = list.length - 1; index >= 0; index -= 1) {
    if (predicate(list[index])) {
      return index
    }
  }
  return -1
}

/**
 * 以活动 id 为粒度做“滚动替换”：
 * - 同一阶段只保留一行，后续事件覆盖该行
 * - 不依赖 agent/tool 事件结构，避免前端解析细节
 */
export const upsertAgentActivityByAgent = (
  rows: ProcessActivity[] | undefined,
  nextActivity: ProcessActivity,
  maxRows: number
): ProcessActivity[] => {
  const currentRows = rows ?? []
  const index = findLastIndex(currentRows, (row) => row.id === nextActivity.id)

  if (index < 0) {
    const nextRows = [...currentRows, nextActivity]
    return nextRows.length > maxRows ? nextRows.slice(-maxRows) : nextRows
  }

  const existing = currentRows[index]
  const nextRows = currentRows.slice()
  nextRows[index] = { ...existing, ...nextActivity, id: existing.id }
  return nextRows
}

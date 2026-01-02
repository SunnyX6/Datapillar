import type { AgentActivity } from '@/stores'

const findLastIndex = <T,>(list: T[], predicate: (value: T) => boolean) => {
  for (let index = list.length - 1; index >= 0; index -= 1) {
    if (predicate(list[index])) {
      return index
    }
  }
  return -1
}

/**
 * 以 agent 为粒度做“滚动替换”：
 * - 同一 agent 只保留一行，后续事件覆盖该行
 * - 不依赖 event/tool 类型，避免后端新增事件导致前端改逻辑
 */
export const upsertAgentActivityByAgent = (
  rows: AgentActivity[] | undefined,
  nextActivity: AgentActivity,
  maxRows: number
): AgentActivity[] => {
  const currentRows = rows ?? []
  const index = findLastIndex(currentRows, (row) => row.agent === nextActivity.agent)

  if (index < 0) {
    const nextRows = [...currentRows, nextActivity]
    return nextRows.length > maxRows ? nextRows.slice(-maxRows) : nextRows
  }

  const existing = currentRows[index]
  const nextRows = currentRows.slice()
  nextRows[index] = { ...existing, ...nextActivity, id: existing.id }
  return nextRows
}


import type { ProcessActivity } from '@/features/workflow/state'

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
 * - agent 为空时回退 id
 */
export const upsertAgentActivityByAgent = (
  rows: ProcessActivity[] | undefined,
  nextActivity: ProcessActivity,
  maxRows: number
): ProcessActivity[] => {
  const currentRows = rows ?? []
  const nextAgentKey = nextActivity.agent_en || nextActivity.agent_cn || nextActivity.id
  const index = findLastIndex(
    currentRows,
    (row) => (row.agent_en || row.agent_cn || row.id) === nextAgentKey
  )

  if (index < 0) {
    const nextRows = [...currentRows, nextActivity]
    return nextRows.length > maxRows ? nextRows.slice(-maxRows) : nextRows
  }

  const existing = currentRows[index]
  const nextRows = currentRows.slice()
  nextRows[index] = { ...existing, ...nextActivity, id: existing.id }
  return nextRows
}
